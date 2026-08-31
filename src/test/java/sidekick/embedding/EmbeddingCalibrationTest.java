package sidekick.embedding;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The calibration test PLAN §6 demands before trusting any stored vector,
 * and the canary for every future model/pooling change: if this fails after
 * a swap, the pipeline is broken no matter how plausible the vectors look.
 *
 * Skips (rather than fails) when the model files aren't downloaded, so the
 * suite still runs on a fresh machine.
 */
class EmbeddingCalibrationTest {

    private static final Path MODEL = Path.of("models", "bge-small-en-v1.5", "model.onnx");
    private static final Path TOKENIZER = Path.of("models", "bge-small-en-v1.5", "tokenizer.json");

    private static final String STATEMENT = "the exit load is 1%";
    private static final String PARAPHRASE = "redemption charge is one percent";
    private static final String UNRELATED = "the fund's benchmark is Nifty 50";

    @Test
    void paraphraseRanksAboveUnrelatedSentence() {
        assumeTrue(Files.exists(MODEL) && Files.exists(TOKENIZER),
                "bge-small model files not downloaded — skipping calibration");

        try (TextEmbedder embedder = new OnnxTextEmbedder(MODEL, TOKENIZER, Pooling.CLS)) {
            float[] statement = embedder.embed(STATEMENT);
            float[] paraphrase = embedder.embed(PARAPHRASE);
            float[] unrelated = embedder.embed(UNRELATED);

            assertEquals(384, statement.length);
            assertEquals(1.0, length(statement), 1e-3, "embedding must be L2-normalized");

            // Anchors pinned empirically on 2026-08-24 (bge-small-en-v1.5, CLS):
            // identical 1.0000, near-verbatim 0.9780, cross-vocab paraphrase
            // 0.6836, unrelated 0.5194. A model/pooling swap that breaks the
            // pipeline breaks these long before it breaks anything subtle.
            assertEquals(1.0, dot(statement, embedder.embed(STATEMENT)), 1e-3,
                    "identical text must score ~1.0");
            assertTrue(dot(statement, embedder.embed("the exit load is 1 percent")) > 0.95,
                    "near-verbatim paraphrase must score very high");

            double simParaphrase = dot(statement, paraphrase);
            double simUnrelated = dot(statement, unrelated);
            System.out.printf("CLS pooling: paraphrase %.4f vs unrelated %.4f (gap %.4f)%n",
                    simParaphrase, simUnrelated, simParaphrase - simUnrelated);

            assertTrue(simParaphrase > simUnrelated + 0.10,
                    "cross-vocab paraphrase must clearly outrank an unrelated sentence");
            assertTrue(simParaphrase > 0.60,
                    "cross-vocab paraphrase below its pinned range (0.68 ± drift) — pipeline likely broken");
        }
    }

    @Test
    void reportsClsVersusMeanPoolingGap() {
        assumeTrue(Files.exists(MODEL) && Files.exists(TOKENIZER),
                "bge-small model files not downloaded — skipping calibration");

        for (Pooling pooling : Pooling.values()) {
            try (TextEmbedder embedder = new OnnxTextEmbedder(MODEL, TOKENIZER, pooling)) {
                double simParaphrase = dot(embedder.embed(STATEMENT), embedder.embed(PARAPHRASE));
                double simUnrelated = dot(embedder.embed(STATEMENT), embedder.embed(UNRELATED));
                System.out.printf("%-4s pooling: paraphrase %.4f vs unrelated %.4f (gap %.4f)%n",
                        pooling, simParaphrase, simUnrelated, simParaphrase - simUnrelated);
                // Both poolings must at least preserve the ranking; which one
                // separates better is what this report exists to show.
                assertTrue(simParaphrase > simUnrelated,
                        pooling + " pooling inverted the ranking — pipeline bug");
            }
        }
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    private static double length(float[] v) {
        return Math.sqrt(dot(v, v));
    }
}
