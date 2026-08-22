package sidekick.eval;

/**
 * How the system did on one eval case.
 *
 * @param caseId       which case this scores
 * @param retrievalHit did any top-k chunk land on an expected page/section?
 *                     (only meaningful for answerable cases)
 * @param hitRank      1-based rank of the first hit; null if no hit
 * @param topScore     similarity of the best-ranked chunk (hit or not) — the
 *                     raw material for tuning the refusal threshold later
 * @param askResult    full-pipeline result; null in retrieval-only mode
 */
public record CaseResult(
        String caseId,
        boolean retrievalHit,
        Integer hitRank,
        double topScore,
        AskResult askResult
) {
}
