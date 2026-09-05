package sidekick.ingestion;

import java.time.Instant;

/**
 * Public read view of an ingested document — what the funds list shows,
 * including the source-and-date disclosure PLAN §3 requires in the UI.
 * Until the catalog module exists (deferred with the 20-fund scale-up),
 * "the funds" ARE the ingested documents.
 */
public record DocumentInfo(String fundId, String displayName, String fileName,
                           int pageCount, Instant ingestedAt) {
}
