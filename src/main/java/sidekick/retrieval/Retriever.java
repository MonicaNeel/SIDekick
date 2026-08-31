package sidekick.retrieval;

import java.util.List;

/**
 * The retrieval module's public API: question in, most similar chunks out,
 * best first. This is the surface the eval runner (and later the answering
 * module) plugs into.
 */
public interface Retriever {

    /**
     * @param fundId restrict to one fund's chunks, or null for all funds
     */
    List<ScoredChunk> search(String question, String fundId, int topK);
}
