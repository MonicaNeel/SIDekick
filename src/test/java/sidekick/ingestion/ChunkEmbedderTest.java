package sidekick.ingestion;

import org.junit.jupiter.api.Test;
import sidekick.embedding.TextEmbedder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkEmbedderTest {

    /** Fake embedder: deterministic, instant, no model files needed. */
    private static final class StubEmbedder implements TextEmbedder {
        @Override
        public float[] embed(String text) {
            return new float[]{text.length(), 1};
        }

        @Override
        public int dimension() {
            return 2;
        }

        @Override
        public void close() {
        }
    }

    @Test
    void pairsEveryChunkWithItsVectorInOrder() {
        List<Chunk> chunks = List.of(
                new Chunk("f", "S1", 1, 1, "ab"),
                new Chunk("f", "S2", 2, 2, "abcd"));
        AtomicInteger progressCalls = new AtomicInteger();

        List<EmbeddedChunk> embedded =
                new ChunkEmbedder(new StubEmbedder()).embedAll(chunks, done -> progressCalls.incrementAndGet());

        assertEquals(2, embedded.size());
        assertEquals("ab", embedded.get(0).chunk().text());
        assertEquals(2f, embedded.get(0).vector()[0]);
        assertEquals(4f, embedded.get(1).vector()[0]);
        assertEquals(2, progressCalls.get());
    }
}
