package sidekick.eval;

/**
 * The eval runner's view of one retrieved chunk: just enough to score a hit
 * (did the page/section match?) — deliberately NOT the full Chunk entity,
 * so eval never depends on another module's database internals.
 *
 * @param chunkId stable id of the chunk
 * @param page    1-based PDF page the chunk starts on
 * @param endPage page the chunk ends on (chunks span pages; a hit means an
 *                expected page falls anywhere inside [page, endPage])
 * @param section heading the chunk sits under (as labeled by the chunker)
 * @param score   cosine similarity, higher = more similar
 */
public record RetrievedChunk(String chunkId, int page, int endPage, String section, double score) {
}
