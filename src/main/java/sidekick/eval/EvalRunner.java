package sidekick.eval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs the eval set against whatever is plugged into the RetrievalPort socket
 * and scores it (retrieval-only mode; generation metrics arrive in step 4).
 * Plain Java, zero Spring — designed in step 1, running for real in step 3.
 *
 * Hit definition (per eval/README.md): an answerable case is a hit when any
 * of the top-k chunks starts on an expected page OR carries the expected
 * section label (normalized). Unanswerable cases aren't hits or misses here —
 * their top scores are collected as tuning data for the step-4 refusal
 * threshold.
 */
public final class EvalRunner {

    private final RetrievalPort retrieval;
    private final AnsweringPort answering; // null = retrieval-only mode (step 3)
    private final EvalConfig config;

    public EvalRunner(RetrievalPort retrieval, EvalConfig config) {
        this(retrieval, null, config);
    }

    public EvalRunner(RetrievalPort retrieval, AnsweringPort answering, EvalConfig config) {
        this.retrieval = retrieval;
        this.answering = answering;
        this.config = config;
    }

    public EvalReport run(List<EvalCase> cases) {
        List<CaseResult> results = new ArrayList<>();
        int answerable = 0;
        int hits = 0;
        int cleanOutputs = 0;
        int correctOutcomes = 0;
        for (EvalCase evalCase : cases) {
            List<RetrievedChunk> retrieved =
                    retrieval.retrieve(evalCase.question(), evalCase.fundId(), config.topK());
            double topScore = retrieved.isEmpty() ? 0 : retrieved.get(0).score();

            Integer hitRank = null;
            if (evalCase.type() == EvalCase.CaseType.ANSWERABLE) {
                answerable++;
                for (int rank = 0; rank < retrieved.size(); rank++) {
                    if (isHit(retrieved.get(rank), evalCase)) {
                        hitRank = rank + 1;
                        break;
                    }
                }
                if (hitRank != null) {
                    hits++;
                }
            }

            AskResult askResult = null;
            if (answering != null) {
                askResult = answering.ask(evalCase.question(), evalCase.fundId());
                if (askResult.citationsValid()) {
                    cleanOutputs++; // valid cited answer OR clean refusal — no gate-2 failure
                }
                boolean expectAnswer = evalCase.type() == EvalCase.CaseType.ANSWERABLE;
                boolean gotAnswer = askResult.outcome() == AskResult.Outcome.ANSWERED;
                if (expectAnswer == gotAnswer) {
                    correctOutcomes++;
                }
            }
            results.add(new CaseResult(evalCase.id(), hitRank != null, hitRank, topScore, retrieved, askResult));
        }
        double hitRate = answerable == 0 ? 0 : (double) hits / answerable;
        Double citationValidity = answering == null ? null : (double) cleanOutputs / cases.size();
        Double refusalCorrectness = answering == null ? null : (double) correctOutcomes / cases.size();
        return new EvalReport(config, Instant.now(), results, hitRate, citationValidity, refusalCorrectness);
    }

    private static boolean isHit(RetrievedChunk chunk, EvalCase evalCase) {
        boolean pageInRange = evalCase.sourcePages().stream()
                .anyMatch(p -> p >= chunk.page() && p <= chunk.endPage());
        return pageInRange || sectionsMatch(chunk.section(), evalCase.expectedSection());
    }

    /** Loose comparison: case, punctuation, and heading prefixes don't matter. */
    static boolean sectionsMatch(String chunkSection, String expectedSection) {
        if (chunkSection == null || expectedSection == null) {
            return false;
        }
        String a = normalize(chunkSection);
        String b = normalize(expectedSection);
        return !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a));
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
