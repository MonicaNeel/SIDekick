package sidekick.eval;

/**
 * The eval runner's view of one retrieved chunk: just enough to score a hit
 * (did the page/section match?) — deliberately NOT the full Chunk entity,
 * so eval never depends on another module's database internals.
 *
 * @param chunkId stable id of the chunk
 * @param page    1-based PDF page the chunk starts on
 * @param section heading the chunk sits under (as labeled by the chunker)
 * @param score   cosine similarity, higher = more similar
 */
public record RetrievedChunk(String chunkId, int page, String section, double score) {
}
