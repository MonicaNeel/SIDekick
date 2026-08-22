package sidekick.eval;

import java.time.Instant;
import java.util.List;

/**
 * One eval run, summarized. The three rates map 1:1 to PLAN.md §2's success
 * criteria.
 *
 * @param config                what the run was configured with
 * @param startedAt             when the run started
 * @param perCase               one result per eval case
 * @param retrievalHitRate      fraction of ANSWERABLE cases where an expected
 *                              page/section appeared in the top-k (target ≥ 0.90)
 * @param citationValidityRate  fraction of ANSWERED cases that passed the
 *                              citation validator; null in retrieval-only mode
 * @param refusalCorrectness    fraction of cases with the right outcome in both
 *                              directions (unanswerable → refused AND answerable
 *                              → answered); null in retrieval-only mode
 */
public record EvalReport(
        EvalConfig config,
        Instant startedAt,
        List<CaseResult> perCase,
        double retrievalHitRate,
        Double citationValidityRate,
        Double refusalCorrectness
) {
}
