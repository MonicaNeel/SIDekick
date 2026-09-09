package sidekick.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sidekick.ingestion.internal.IngestionJobEntity;
import sidekick.ingestion.internal.IngestionJobRepository;
import sidekick.ingestion.internal.IngestionJobWorker;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for async upload ingestion (PLAN §3): submit returns a job id
 * immediately; the worker runs extraction -> sanity check -> ingest in the
 * background (~2 minutes, mostly embedding); poll status(jobId).
 *
 * ADR-007 guard: the pinned eval fund slugs can NEVER be replaced through
 * uploads — the eval corpus changes only by deliberate CLI ingestion.
 */
@Service
public class IngestionJobs {

    /** Pollable job view (IDs and counts only — no entities leave the module). */
    public record JobView(UUID jobId, String fundId, JobStatus status, String error,
                          Integer pages, Integer chunkCount,
                          Instant createdAt, Instant finishedAt) {
    }

    private final IngestionJobRepository jobs;
    private final IngestionJobWorker worker;
    private final List<String> pinnedFunds;

    public IngestionJobs(IngestionJobRepository jobs, IngestionJobWorker worker,
                         @Value("${sidekick.ingest.pinned-funds:}") List<String> pinnedFunds) {
        this.jobs = jobs;
        this.worker = worker;
        this.pinnedFunds = pinnedFunds;
    }

    /**
     * @throws PinnedFundException for the protected eval fund slugs
     */
    public UUID submit(Path uploadedPdf, String originalFileName, String fundId, String displayName) {
        if (pinnedFunds.contains(fundId)) {
            throw new PinnedFundException(fundId);
        }
        IngestionJobEntity job = new IngestionJobEntity(fundId, displayName, originalFileName);
        jobs.save(job);
        worker.run(job.getId(), uploadedPdf, fundId, displayName); // @Async — returns immediately
        return job.getId();
    }

    public Optional<JobView> status(UUID jobId) {
        return jobs.findById(jobId).map(j -> new JobView(j.getId(), j.getFundId(), j.getStatus(),
                j.getError(), j.getPages(), j.getChunkCount(), j.getCreatedAt(), j.getFinishedAt()));
    }

    public static class PinnedFundException extends RuntimeException {
        public PinnedFundException(String fundId) {
            super("fund '" + fundId + "' is part of the pinned eval corpus and cannot be replaced "
                    + "by upload (ADR-007); choose a different fund id");
        }
    }
}
