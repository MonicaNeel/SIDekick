package sidekick.ingestion;

import sidekick.embedding.TextEmbedder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Embeds chunks in bulk. Deliberately a plain loop: at this corpus size
 * (~120 chunks per document, seconds of CPU) parallelism would buy nothing
 * and hide the cost we want the owner to see.
 */
public final class ChunkEmbedder {

    private final TextEmbedder embedder;

    public ChunkEmbedder(TextEmbedder embedder) {
        this.embedder = embedder;
    }

    public List<EmbeddedChunk> embedAll(List<Chunk> chunks) {
        return embedAll(chunks, done -> {
        });
    }

    /** @param progress called after each chunk with the count done so far */
    public List<EmbeddedChunk> embedAll(List<Chunk> chunks, IntConsumer progress) {
        List<EmbeddedChunk> result = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            result.add(new EmbeddedChunk(chunk, embedder.embed(chunk.text())));
            progress.accept(result.size());
        }
        return result;
    }
}
