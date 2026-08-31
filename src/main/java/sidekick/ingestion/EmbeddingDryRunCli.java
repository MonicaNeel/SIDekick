package sidekick.ingestion;

import sidekick.embedding.OnnxTextEmbedder;
import sidekick.embedding.Pooling;
import sidekick.embedding.TextEmbedder;
import sidekick.embedding.VectorCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Milestone C inspection tool: run the full Docker-free pipeline
 * (extract -> chunk -> embed) on one document and report what persistence
 * will actually cost — time, vectors, bytes. No database involved.
 *
 * Usage: EmbeddingDryRunCli &lt;input.pdf&gt; &lt;fundId&gt;
 */
public final class EmbeddingDryRunCli {

    private static final Path MODEL = Path.of("models", "bge-small-en-v1.5", "model.onnx");
    private static final Path TOKENIZER = Path.of("models", "bge-small-en-v1.5", "tokenizer.json");

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: EmbeddingDryRunCli <input.pdf> <fundId>");
            System.exit(1);
        }
        List<PageText> pages = new PdfTextExtractor().extract(Path.of(args[0]));
        List<Chunk> chunks = SectionAwareChunker.withDefaults().chunk(args[1], pages);
        System.out.printf("Extracted %d pages -> %d chunks. Embedding...%n", pages.size(), chunks.size());

        try (TextEmbedder embedder = new OnnxTextEmbedder(MODEL, TOKENIZER, Pooling.CLS)) {
            long start = System.nanoTime();
            List<EmbeddedChunk> embedded = new ChunkEmbedder(embedder).embedAll(chunks, done -> {
                if (done % 25 == 0) {
                    System.out.printf("  ...%d/%d%n", done, chunks.size());
                }
            });
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            long storedBytes = embedded.stream()
                    .mapToLong(e -> VectorCodec.toBytes(e.vector()).length)
                    .sum();
            double worstNormDrift = embedded.stream()
                    .mapToDouble(e -> Math.abs(1.0 - length(e.vector())))
                    .max().orElse(0);

            System.out.printf("Embedded %d chunks in %,d ms (%.0f ms/chunk)%n",
                    embedded.size(), elapsedMs, (double) elapsedMs / embedded.size());
            System.out.printf("Vector storage: %d vectors x %d dims = %,d bytes (%.2f MB)%n",
                    embedded.size(), embedder.dimension(), storedBytes, storedBytes / 1048576.0);
            System.out.printf("Worst L2-norm drift from 1.0: %.2e (should be ~1e-7; anything near 1e-1 means broken normalization)%n",
                    worstNormDrift);
        }
    }

    private static double length(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        return Math.sqrt(sum);
    }
}
