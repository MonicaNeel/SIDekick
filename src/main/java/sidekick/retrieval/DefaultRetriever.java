package sidekick.retrieval;

import sidekick.embedding.TextEmbedder;

import java.util.List;

/**
 * The query path: embed the question, search the index. Zero Spring imports.
 *
 * queryPrefix exists because bge models were trained with an instruction
 * prefix for short retrieval queries ("Represent this sentence for searching
 * relevant passages: "). It's config; the eval decides whether it helps.
 */
public final class DefaultRetriever implements Retriever {

    private final TextEmbedder embedder;
    private final InMemoryVectorIndex index;
    private final String queryPrefix;

    public DefaultRetriever(TextEmbedder embedder, InMemoryVectorIndex index, String queryPrefix) {
        this.embedder = embedder;
        this.index = index;
        this.queryPrefix = queryPrefix == null ? "" : queryPrefix;
    }

    @Override
    public List<ScoredChunk> search(String question, String fundId, int topK) {
        float[] queryVector = embedder.embed(queryPrefix + question);
        return index.search(queryVector, fundId, topK);
    }
}
