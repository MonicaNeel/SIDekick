package sidekick.embedding;

/**
 * The tiny hand-written interface every embedding consumer depends on
 * (PLAN §4 hard rules: models swap via config, callers never know which
 * model is behind this).
 *
 * Contract: the returned vector is L2-normalized, so cosine similarity
 * between two embeddings is just their dot product.
 */
public interface TextEmbedder extends AutoCloseable {

    float[] embed(String text);

    /** Vector length, e.g. 384 for bge-small. */
    int dimension();

    @Override
    void close();
}
