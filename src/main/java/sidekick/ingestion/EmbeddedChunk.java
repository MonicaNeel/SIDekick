package sidekick.ingestion;

/**
 * A chunk paired with its embedding — the unit that persistence stores
 * (text + metadata into columns, vector into bytea via VectorCodec).
 */
public record EmbeddedChunk(Chunk chunk, float[] vector) {
}
