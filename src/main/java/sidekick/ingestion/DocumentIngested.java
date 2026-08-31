package sidekick.ingestion;

import java.util.UUID;

/**
 * Published after a document's chunks and vectors are committed. The
 * retrieval module (build-order step 3) listens for this to reload its
 * in-memory index; Modulith's event publication registry persists the event
 * so a crash between commit and reload gets replayed instead of lost
 * (PLAN §5 — the index must never silently drift from Postgres).
 *
 * Carries IDs only — cross-module communication never shares entities.
 */
public record DocumentIngested(String fundId, UUID documentId) {
}
