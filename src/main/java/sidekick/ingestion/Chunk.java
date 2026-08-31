package sidekick.ingestion;

/**
 * One retrievable unit of a document: a stretch of text that knows which fund
 * it belongs to, which section heading it sits under, and which page it starts
 * on. Everything downstream — embeddings, retrieval, citations, eval scoring —
 * works on these.
 *
 * @param fundId  manifest slug (cross-module reference by ID, never entity)
 * @param section canonical heading label, or "PREAMBLE" for text before the
 *                first recognized heading (cover pages, TOC)
 * @param page    1-based physical PDF page where this chunk STARTS; a chunk
 *                may run onto following pages
 * @param text    the chunk text
 */
public record Chunk(String fundId, String section, int page, String text) {
}
