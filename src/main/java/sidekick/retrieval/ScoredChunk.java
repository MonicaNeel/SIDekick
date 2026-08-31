package sidekick.retrieval;

/**
 * One search result: a chunk and how similar it is to the question.
 * Score is cosine similarity (== dot product, since vectors are normalized);
 * higher is more similar. Carries the text too — answering (step 4) and the
 * debug panel (step 5) need it.
 */
public record ScoredChunk(
        String chunkId,
        String fundId,
        String section,
        int page,
        int endPage,
        String text,
        double score
) {
}
