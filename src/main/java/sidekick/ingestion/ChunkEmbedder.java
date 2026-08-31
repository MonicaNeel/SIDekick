package sidekick.ingestion;

import sidekick.embedding.TextEmbedder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Embeds chunks in bulk. Deliberately a plain loop: at this corpus size
 * (~120 chunks per document, seconds of CPU) parallelism would buy nothing
 * and hide the cost we want the owner to see.
 *
 * prependSection: when true, the EMBEDDED string is "SECTION\ntext" so
 * table-heavy chunks carry their topic ("TAXATION: ...") into the vector.
 * The stored chunk text is untouched — citations and quotes stay clean.
 * PREAMBLE is skipped (a made-up label carries no meaning).
 */
public final class ChunkEmbedder {

    private final TextEmbedder embedder;
    private final boolean prependSection;

    public ChunkEmbedder(TextEmbedder embedder) {
        this(embedder, false);
    }

    public ChunkEmbedder(TextEmbedder embedder, boolean prependSection) {
        this.embedder = embedder;
        this.prependSection = prependSection;
    }

    public List<EmbeddedChunk> embedAll(List<Chunk> chunks) {
        return embedAll(chunks, done -> {
        });
    }

    /** @param progress called after each chunk with the count done so far */
    public List<EmbeddedChunk> embedAll(List<Chunk> chunks, IntConsumer progress) {
        List<EmbeddedChunk> result = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            result.add(new EmbeddedChunk(chunk, embedder.embed(embeddedText(chunk))));
            progress.accept(result.size());
        }
        return result;
    }

    private String embeddedText(Chunk chunk) {
        if (prependSection && !"PREAMBLE".equals(chunk.section())) {
            return chunk.section() + "\n" + chunk.text();
        }
        return chunk.text();
    }
}
