package sidekick.retrieval;

/**
 * How the question is embedded before searching.
 *
 * Eval evidence (2026-08-31): PLAIN and PREFIXED miss nearly disjoint case
 * sets — the bge instruction prefix helps colloquial questions and hurts
 * lexically-matched ones. FUSED runs both and merges by best score, buying
 * the union of their strengths for one extra embedding call per question.
 */
public enum QueryMode {
    PLAIN,
    PREFIXED,
    FUSED
}
