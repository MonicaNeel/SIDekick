package sidekick.ingestion;

import java.util.UUID;

/**
 * Read-only view of a persisted chunk — ingestion's public answer to "give me
 * the chunks" without exposing its JPA entities. The embedding stays as raw
 * bytes; the caller decodes with VectorCodec (the byte layout is that codec's
 * contract, not this module's).
 */
public record StoredChunk(
        UUID id,
        String fundId,
        String section,
        int page,
        int endPage,
        String text,
        byte[] embedding
) {
}
