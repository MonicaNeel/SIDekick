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
import sidekick.eval.CaseResult;
import sidekick.eval.EvalCase;
import sidekick.eval.EvalConfig;
import sidekick.eval.EvalReport;
import sidekick.eval.EvalRunner;
import sidekick.eval.EvalSet;
import sidekick.eval.EvalSetLoader;
import sidekick.eval.RetrievalPort;
import sidekick.eval.RetrievedChunk;
import sidekick.retrieval.Retriever;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CLI for retrieval-only eval runs (build-order step 3's instrument):
 *   --sidekick.eval.mode=retrieval [--sidekick.eval.label="chunker v1, k=5"]
 * Prints per-case results and writes a timestamped JSON report to
 * work/eval-runs/ so runs stay comparable across chunker/config changes.
 *
 * The two-line adapter below is the whole "wiring" between the eval module's
 * RetrievalPort socket (designed in step 1) and the retrieval module's
 * public Retriever API — dependencies flow eval -> retrieval.
 */
@Component
@ConditionalOnProperty(name = "sidekick.eval.mode", havingValue = "retrieval")
class RetrievalEvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvalRunner.class);

    private final Retriever retriever;
    private final int topK;
    private final String label;

    RetrievalEvalRunner(Retriever retriever,
                        @Value("${sidekick.eval.top-k:5}") int topK,
                        @Value("${sidekick.eval.label:}") String label) {
        this.retriever = retriever;
        this.topK = topK;
        this.label = label;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        EvalSet evalSet = new EvalSetLoader().load(Path.of("eval", "eval-set.json"));
        Map<String, EvalCase> caseById = evalSet.cases().stream()
                .collect(Collectors.toMap(EvalCase::id, Function.identity()));

        RetrievalPort port = (question, fundId, k) -> retriever.search(question, fundId, k).stream()
                .map(s -> new RetrievedChunk(s.chunkId(), s.page(), s.section(), s.score()))
                .toList();

        EvalReport report = new EvalRunner(port, new EvalConfig(topK, null, label)).run(evalSet.cases());

        System.out.printf("%n%-42s %-6s %-5s %-9s %s%n", "case", "result", "rank", "topScore", "expected pages");
        DoubleSummaryStatistics answerableScores = new DoubleSummaryStatistics();
        DoubleSummaryStatistics unanswerableScores = new DoubleSummaryStatistics();
        for (CaseResult result : report.perCase()) {
            EvalCase evalCase = caseById.get(result.caseId());
            boolean answerable = evalCase.type() == EvalCase.CaseType.ANSWERABLE;
            if (answerable) {
                answerableScores.accept(result.topScore());
            } else {
                unanswerableScores.accept(result.topScore());
            }
            System.out.printf("%-42s %-6s %-5s %-9.4f %s%n",
                    result.caseId(),
                    answerable ? (result.retrievalHit() ? "PASS" : "MISS") : "unans",
                    result.hitRank() == null ? "-" : result.hitRank().toString(),
                    result.topScore(),
                    answerable ? evalCase.sourcePages() : "-");
        }
        System.out.printf("%nRetrieval hit rate: %.1f%% (target >= 90%%)%n", report.retrievalHitRate() * 100);
        System.out.printf("Top-score ranges — answerable: %.4f..%.4f, unanswerable: %.4f..%.4f%n",
                answerableScores.getMin(), answerableScores.getMax(),
                unanswerableScores.getMin(), unanswerableScores.getMax());
        System.out.println("(The gap between those ranges is where the step-4 refusal threshold will live.)");

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
