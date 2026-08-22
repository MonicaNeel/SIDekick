package sidekick.eval;

import java.util.List;

/**
 * What the eval runner needs from the retrieval side — nothing more.
 *
 * Think of this as a wall socket: the eval runner is an appliance that plugs
 * into it. The retrieval module (build-order step 3) is the power plant that
 * will eventually supply it. Today nothing implements this interface, and
 * that's fine — the shape of the socket IS the design decision.
 */
public interface RetrievalPort {

    /**
     * Given a question, return the top-k most similar chunks for one fund,
     * best first.
     */
    List<RetrievedChunk> retrieve(String question, String fundId, int topK);
}
