package sidekick.embedding;

/**
 * How to squeeze one vector out of the model's per-token output matrix.
 *
 * Each model family has an official recipe — using the wrong one produces
 * vectors that look normal but rank worse (PLAN §6). Config, not code, so a
 * model swap stays config-only:
 * - CLS  — take the first token's vector. Official for bge-v1.5 models.
 * - MEAN — average all real-token vectors (attention mask decides which
 *          positions are real). Official for MiniLM / e5 families.
 */
public enum Pooling {
    CLS,
    MEAN
}
