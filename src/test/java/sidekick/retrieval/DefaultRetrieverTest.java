package sidekick.retrieval;

import org.junit.jupiter.api.Test;
import sidekick.embedding.TextEmbedder;
import sidekick.retrieval.InMemoryVectorIndex.Entry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRetrieverTest {

    private static final String PREFIX = "query: ";

    /**
     * Stub with hand-picked geometry: the PLAIN query points at [1,0], the
     * PREFIXED query at [0,1] -- so each variant "loves" a different chunk and
     * fusion must surface both.
     */
    private static final TextEmbedder STUB = new TextEmbedder() {
        @Override
        public float[] embed(String text) {
            return text.startsWith(PREFIX) ? new float[]{0, 1} : new float[]{1, 0};
        }

        @Override
        public int dimension() {
            return 2;
        }

        @Override
        public void close() {
        }
    };

    private static InMemoryVectorIndex index() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.replaceAll(List.of(
                new Entry("lexical-favorite", "f", "S", 1, 1, "t", new float[]{1, 0}),
                new Entry("semantic-favorite", "f", "S", 2, 2, "t", new float[]{0, 1}),
                new Entry("nobody-favorite", "f", "S", 3, 3, "t",
                        new float[]{(float) Math.sqrt(0.5), (float) Math.sqrt(0.5)})));
        return index;
    }

    @Test
    void plainAndPrefixedModesRankDifferently() {
        Retriever plain = new DefaultRetriever(STUB, index(), PREFIX, QueryMode.PLAIN);
        Retriever prefixed = new DefaultRetriever(STUB, index(), PREFIX, QueryMode.PREFIXED);

        assertEquals("lexical-favorite", plain.search("q", "f", 1).get(0).chunkId());
        assertEquals("semantic-favorite", prefixed.search("q", "f", 1).get(0).chunkId());
    }

    @Test
    void fusedModeRanksByReciprocalRankConsensus() {
        // RRF semantics, pinned deliberately: 'nobody-favorite' is rank 2 in
        // BOTH lists (1/62 + 1/62), which beats each champion's single 1/61.
        // Consensus outranks a single variant's favorite -- that is the
        // documented trade-off of RRF (it recovers cases either variant finds,
        // at the cost of sometimes seating agreeable mediocrity above a
        // champion). If this ever surprises you again, you are re-learning it.
        // topK=2 so each per-variant list is [own champion, nobody-favorite]:
        // champions appear in ONE list (1/61), nobody in BOTH (1/62 + 1/62).
        Retriever fused = new DefaultRetriever(STUB, index(), PREFIX, QueryMode.FUSED);

        List<ScoredChunk> results = fused.search("q", "f", 2);

        assertEquals(2, results.size());
        assertEquals("nobody-favorite", results.get(0).chunkId(),
                "appears in both lists -> highest RRF sum");
        // Second seat goes to one champion (they tie); its reported score is
        // its real cosine (1.0), not an RRF sum.
        assertTrue(List.of("lexical-favorite", "semantic-favorite").contains(results.get(1).chunkId()));
        assertEquals(1.0, results.get(1).score(), 1e-6);
    }
}
