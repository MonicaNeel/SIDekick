package sidekick.eval;

/**
 * Everything that could explain why two eval runs scored differently.
 * Written into every report so runs stay comparable weeks later.
 *
 * @param topK      how many chunks retrieval returns (the k in hit@k)
 * @param modelName OpenRouter model used for generation; null in retrieval-only mode
 * @param runLabel  free-text note, e.g. "chunker v2, overlap 50 tokens"
 */
public record EvalConfig(int topK, String modelName, String runLabel) {
}
