package sidekick.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The hand-built embedding pipeline (PLAN §6), no framework:
 *
 *   text --tokenizer--> token ids + attention mask
 *        --ONNX session--> one vector per token (the "hidden states")
 *        --pooling--> a single vector
 *        --L2 normalize--> the embedding
 *
 * The tokenizer is the HuggingFace tokenizers library (Java bindings) reading
 * the model's own tokenizer.json — same WordPiece vocabulary the model was
 * trained with. The ONNX session is the neural network itself.
 */
public final class OnnxTextEmbedder implements TextEmbedder {

    private static final int MAX_TOKENS = 512; // BERT-family context limit

    private final HuggingFaceTokenizer tokenizer;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Pooling pooling;

    public OnnxTextEmbedder(Path modelFile, Path tokenizerFile, Pooling pooling) {
        this.pooling = pooling;
        try {
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerFile)
                    .optTruncation(true)
                    .optMaxLength(MAX_TOKENS)
                    .optPadding(false)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load tokenizer " + tokenizerFile, e);
        }
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(modelFile.toString(), new OrtSession.SessionOptions());
        } catch (OrtException e) {
            throw new IllegalStateException("cannot load ONNX model " + modelFile, e);
        }
    }

    @Override
    public float[] embed(String text) {
        Encoding encoding = tokenizer.encode(text);
        long[] ids = encoding.getIds();
        long[] mask = encoding.getAttentionMask();
        long[] typeIds = encoding.getTypeIds();

        try (OnnxTensor idsTensor = OnnxTensor.createTensor(environment, new long[][]{ids});
             OnnxTensor maskTensor = OnnxTensor.createTensor(environment, new long[][]{mask});
             OnnxTensor typeTensor = OnnxTensor.createTensor(environment, new long[][]{typeIds})) {

            // Only feed inputs this particular model declares (some exports
            // omit token_type_ids).
            Map<String, OnnxTensor> inputs = new HashMap<>();
            for (String name : session.getInputNames()) {
                switch (name) {
                    case "input_ids" -> inputs.put(name, idsTensor);
                    case "attention_mask" -> inputs.put(name, maskTensor);
                    case "token_type_ids" -> inputs.put(name, typeTensor);
                    default -> throw new IllegalStateException("unexpected model input: " + name);
                }
            }
            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                float[] pooled = pool(hidden[0], mask);
                return normalize(pooled);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("embedding failed", e);
        }
    }

    private float[] pool(float[][] tokenVectors, long[] attentionMask) {
        return switch (pooling) {
            case CLS -> tokenVectors[0].clone();
            case MEAN -> meanPool(tokenVectors, attentionMask);
        };
    }

    private static float[] meanPool(float[][] tokenVectors, long[] attentionMask) {
        int dim = tokenVectors[0].length;
        float[] sum = new float[dim];
        long realTokens = 0;
        for (int t = 0; t < tokenVectors.length; t++) {
            if (attentionMask[t] == 0) {
                continue; // padding position — not a real token
            }
            realTokens++;
            for (int d = 0; d < dim; d++) {
                sum[d] += tokenVectors[t][d];
            }
        }
        for (int d = 0; d < dim; d++) {
            sum[d] /= realTokens;
        }
        return sum;
    }

    /** Scale to unit length so cosine similarity becomes a plain dot product. */
    private static float[] normalize(float[] vector) {
        double squares = 0;
        for (float v : vector) {
            squares += (double) v * v;
        }
        float norm = (float) Math.sqrt(squares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }

    @Override
    public int dimension() {
        return 384;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
            // closing anyway
        }
        tokenizer.close();
    }
}
