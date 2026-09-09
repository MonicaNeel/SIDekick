package sidekick.ingestion.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import sidekick.ingestion.IngestionService;
import sidekick.ingestion.PageText;
import sidekick.ingestion.PdfTextExtractor;
import sidekick.ingestion.SidSanityCheck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Executes one ingestion job off the request thread. Every failure path lands
 * in the job row (users poll, they don't read server logs) and the uploaded
 * temp file is always cleaned up.
 */
@Component
public class IngestionJobWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobWorker.class);

    private final IngestionJobRepository jobs;
    private final IngestionService ingestion;

    IngestionJobWorker(IngestionJobRepository jobs, IngestionService ingestion) {
        this.jobs = jobs;
        this.ingestion = ingestion;
    }

    @Async
    public void run(UUID jobId, Path pdf, String fundId, String displayName) {
        IngestionJobEntity job = jobs.findById(jobId).orElseThrow();
        job.markRunning();
        jobs.save(job);
        try {
            List<PageText> pages = new PdfTextExtractor().extract(pdf);
            SidSanityCheck.Result sanity = SidSanityCheck.check(pages);
            if (!sanity.plausibleSid()) {
                job.markFailed(sanity.message());
                jobs.save(job);
                return;
            }
            IngestionService.Result result = ingestion.ingest(pdf, fundId, displayName);
            job.markDone(result.pages(), result.chunkCount());
            jobs.save(job);
            log.info("upload job {} done: fund '{}', {} chunks", jobId, fundId, result.chunkCount());
        } catch (Exception e) {
            log.warn("upload job {} failed", jobId, e);
            job.markFailed("ingestion failed: " + e.getMessage());
            jobs.save(job);
        } finally {
            try {
                Files.deleteIfExists(pdf);
            } catch (IOException cleanupFailure) {
                log.warn("could not delete temp upload {}", pdf, cleanupFailure);
            }
        }
    }
}
