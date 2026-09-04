package sidekick.eval.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sidekick.answering.Answer;
import sidekick.answering.AnswerService;
import sidekick.eval.AskResult;
import sidekick.eval.CaseResult;
import sidekick.eval.EvalCase;
import sidekick.eval.EvalConfig;
import sidekick.eval.EvalReport;
import sidekick.eval.EvalRunner;
import sidekick.eval.EvalSet;
import sidekick.eval.EvalSetLoader;
import sidekick.eval.AnsweringPort;
import sidekick.eval.RetrievalPort;
import sidekick.eval.RetrievedChunk;
import sidekick.retrieval.Retriever;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Eval CLI, two modes:
 *   --sidekick.eval.mode=retrieval  -> hit rate only, no LLM calls, free
 *   --sidekick.eval.mode=full       -> plus generation: citation discipline
 *                                      and refusal correctness (PLAN §7),
 *                                      paced between questions because free
 *                                      routes rate-limit
 * Model comes from sidekick.answering.model (override per run to benchmark).
 * Every run writes a timestamped JSON report to work/eval-runs/.
 */
@Component
@ConditionalOnProperty("sidekick.eval.mode")
class RetrievalEvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvalRunner.class);

    private final Retriever retriever;
    private final AnswerService answerService;
    private final String mode;
    private final int topK;
    private final String label;
    private final String modelName;
    private final long questionDelayMillis;

    RetrievalEvalRunner(Retriever retriever,
                        AnswerService answerService,
                        @Value("${sidekick.eval.mode}") String mode,
                        @Value("${sidekick.eval.top-k:6}") int topK,
                        @Value("${sidekick.eval.label:}") String label,
                        @Value("${sidekick.answering.model:}") String modelName,
                        @Value("${sidekick.eval.question-delay-millis:2000}") long questionDelayMillis) {
        this.retriever = retriever;
        this.answerService = answerService;
        this.mode = mode;
        this.topK = topK;
        this.label = label;
        this.modelName = modelName;
        this.questionDelayMillis = questionDelayMillis;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean fullMode = "full".equalsIgnoreCase(mode);
        EvalSet evalSet = new EvalSetLoader().load(Path.of("eval", "eval-set.json"));
        Map<String, EvalCase> caseById = evalSet.cases().stream()
                .collect(Collectors.toMap(EvalCase::id, Function.identity()));

        RetrievalPort retrievalPort = (question, fundId, k) -> retriever.search(question, fundId, k).stream()
                .map(s -> new RetrievedChunk(s.chunkId(), s.page(), s.endPage(), s.section(), s.score()))
                .toList();
        AnsweringPort answeringPort = fullMode ? this::askPaced : null;

        EvalConfig config = new EvalConfig(topK, fullMode ? modelName : null, label);
        EvalReport report = new EvalRunner(retrievalPort, answeringPort, config).run(evalSet.cases());

        printTable(report, caseById, fullMode);
        printMisses(report, caseById);
        if (fullMode) {
            printGate2Failures(report);
        }
        printSummary(report, caseById, fullMode);
        writeJsonReport(report);
    }

    /** The plan's pacing rule: never fire 30 questions in 10 seconds at a free route. */
    private AskResult askPaced(String question, String fundId) {
        try {
            Thread.sleep(questionDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            Answer answer = answerService.ask(question, fundId, fundId);
            return new AskResult(
                    answer.outcome() == Answer.Outcome.ANSWERED
                            ? AskResult.Outcome.ANSWERED : AskResult.Outcome.REFUSED,
                    answer.text(),
                    answer.citations().stream().map(Answer.Citation::chunkId).toList(),
                    answer.validationProblems().isEmpty(),
                    answer.validationProblems());
        } catch (sidekick.answering.LlmException e) {
            // A dead/rate-limited route must not kill a 30-question benchmark
            // run: record the casualty (counts as unclean + wrong outcome for
            // answerable cases) and keep going.
            log.warn("LLM error on question '{}': {}", question, e.getMessage());
            return new AskResult(AskResult.Outcome.REFUSED,
                    "LLM_ERROR: " + e.getMessage(), java.util.List.of(), false,
                    java.util.List.of("LLM error: " + e.getMessage()));
        }
    }

    private void printTable(EvalReport report, Map<String, EvalCase> caseById, boolean fullMode) {
        System.out.printf("%n%-42s %-6s %-5s %-9s %-9s %-7s %s%n",
                "case", "result", "rank", "topScore", "outcome", "clean", "expected pages");
        for (CaseResult result : report.perCase()) {
            EvalCase evalCase = caseById.get(result.caseId());
            boolean answerable = evalCase.type() == EvalCase.CaseType.ANSWERABLE;
            String outcome = "-";
            String clean = "-";
            if (fullMode && result.askResult() != null) {
                boolean expectAnswer = answerable;
                boolean gotAnswer = result.askResult().outcome() == AskResult.Outcome.ANSWERED;
                outcome = (gotAnswer ? "ANSW" : "REFUSE") + (expectAnswer == gotAnswer ? "" : " !!");
                clean = result.askResult().citationsValid() ? "yes" : "NO";
            }
            System.out.printf("%-42s %-6s %-5s %-9.4f %-9s %-7s %s%n",
                    result.caseId(),
                    answerable ? (result.retrievalHit() ? "PASS" : "MISS") : "unans",
                    result.hitRank() == null ? "-" : result.hitRank().toString(),
                    result.topScore(),
                    outcome,
                    clean,
                    answerable ? evalCase.sourcePages().toString() : "-");
        }
    }

    private void printGate2Failures(EvalReport report) {
        for (CaseResult result : report.perCase()) {
            AskResult ask = result.askResult();
            if (ask == null || ask.citationsValid()) {
                continue;
            }
            System.out.printf("%nGATE-2 %s%n", result.caseId());
            ask.validationProblems().forEach(p -> System.out.println("    - " + p));
        }
    }

    private void printMisses(EvalReport report, Map<String, EvalCase> caseById) {
        for (CaseResult result : report.perCase()) {
            EvalCase evalCase = caseById.get(result.caseId());
            if (evalCase.type() != EvalCase.CaseType.ANSWERABLE || result.retrievalHit()) {
                continue;
            }
            System.out.printf("%nMISS %s%n  wanted: pages %s, section '%s'%n  got:%n",
                    result.caseId(), evalCase.sourcePages(), evalCase.expectedSection());
            result.retrieved().forEach(chunk -> System.out.printf(
                    "    %.4f  p.%d-%d  %s%n", chunk.score(), chunk.page(), chunk.endPage(), chunk.section()));
        }
    }

    private void printSummary(EvalReport report, Map<String, EvalCase> caseById, boolean fullMode) {
        DoubleSummaryStatistics answerableScores = new DoubleSummaryStatistics();
        DoubleSummaryStatistics unanswerableScores = new DoubleSummaryStatistics();
        for (CaseResult result : report.perCase()) {
            if (caseById.get(result.caseId()).type() == EvalCase.CaseType.ANSWERABLE) {
                answerableScores.accept(result.topScore());
            } else {
                unanswerableScores.accept(result.topScore());
            }
        }
        System.out.printf("%nRetrieval hit rate: %.1f%% (target >= 90%%)%n", report.retrievalHitRate() * 100);
        System.out.printf("Top-score ranges — answerable: %.4f..%.4f, unanswerable: %.4f..%.4f%n",
                answerableScores.getMin(), answerableScores.getMax(),
                unanswerableScores.getMin(), unanswerableScores.getMax());
        if (fullMode) {
            long llmErrors = report.perCase().stream()
                    .filter(r -> r.askResult() != null && r.askResult().answerText().startsWith("LLM_ERROR"))
                    .count();
            System.out.printf("Citation discipline (model '%s'): %.1f%% of outputs clean (target 100%%)%n",
                    modelName, report.citationValidityRate() * 100);
            System.out.printf("Refusal correctness: %.1f%% of cases got the right outcome%n",
                    report.refusalCorrectness() * 100);
            if (llmErrors > 0) {
                System.out.printf("LLM errors (provider failures, counted against both rates): %d/%d%n",
                        llmErrors, report.perCase().size());
            }
        }
    }

    private void writeJsonReport(EvalReport report) throws Exception {
        Path out = Path.of("work", "eval-runs",
                "run-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        Files.createDirectories(out.getParent());
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(out, mapper.writeValueAsString(report));
        log.info("Eval report written to {}", out.toAbsolutePath());
    }
}
