package sidekick.retrieval;

import org.junit.jupiter.api.Test;
import sidekick.retrieval.InMemoryVectorIndex.Entry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-checkable vectors: 2-dimensional, so every expected score can be
 * verified with pen and paper.
 */
class InMemoryVectorIndexTest {

    private static final float DIAG = (float) (1 / Math.sqrt(2)); // normalized [1,1]

    private static Entry entry(String id, String fund, float x, float y) {
        return new Entry(id, fund, "S", 1, 1, "text-" + id, new float[]{x, y});
    }

    private InMemoryVectorIndex indexOf(Entry... entries) {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.replaceAll(List.of(entries));
        return index;
    }

    @Test
    void ranksByDotProductBestFirst() {
        InMemoryVectorIndex index = indexOf(
                entry("east", "f", 1, 0),      // dot with query [1,0] = 1.0
                entry("north", "f", 0, 1),     // = 0.0
                entry("northeast", "f", DIAG, DIAG)); // = 0.707

        List<ScoredChunk> results = index.search(new float[]{1, 0}, "f", 3);

        assertEquals(List.of("east", "northeast", "north"),
                results.stream().map(ScoredChunk::chunkId).toList());
        assertEquals(1.0, results.get(0).score(), 1e-6);
        assertEquals(DIAG, results.get(1).score(), 1e-6);
    }

    @Test
    void topKKeepsOnlyTheBestK() {
        InMemoryVectorIndex index = indexOf(
                entry("east", "f", 1, 0),
                entry("north", "f", 0, 1),
                entry("northeast", "f", DIAG, DIAG));

        List<ScoredChunk> results = index.search(new float[]{1, 0}, "f", 2);

        assertEquals(2, results.size());
        assertEquals("east", results.get(0).chunkId());
        assertEquals("northeast", results.get(1).chunkId());
    }

    @Test
    void fundFilterExcludesOtherFunds() {
        InMemoryVectorIndex index = indexOf(
                entry("mine", "fund-a", DIAG, DIAG),
                entry("other", "fund-b", 1, 0)); // better score, wrong fund

        List<ScoredChunk> results = index.search(new float[]{1, 0}, "fund-a", 5);

        assertEquals(List.of("mine"), results.stream().map(ScoredChunk::chunkId).toList());
    }

    @Test
    void nullFundSearchesEverything() {
        InMemoryVectorIndex index = indexOf(
                entry("a", "fund-a", 1, 0),
                entry("b", "fund-b", 0, 1));

        assertEquals(2, index.search(new float[]{1, 0}, null, 5).size());
    }

    @Test
    void emptyIndexReturnsEmptyNotError() {
        assertTrue(new InMemoryVectorIndex().search(new float[]{1, 0}, "f", 5).isEmpty());
    }

    @Test
    void replaceAllSwapsTheWholeIndex() {
        InMemoryVectorIndex index = indexOf(entry("old", "f", 1, 0));

        index.replaceAll(List.of(entry("new", "f", 1, 0)));

        List<ScoredChunk> results = index.search(new float[]{1, 0}, "f", 5);
        assertEquals(List.of("new"), results.stream().map(ScoredChunk::chunkId).toList());
        assertEquals(1, index.size());
    }
}
