package sidekick.ingestion.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sidekick.ingestion.IngestionService;

import java.nio.file.Path;

/**
 * CLI trigger: start the app with
 *   --sidekick.ingest.file="path\to\sid.pdf" --sidekick.ingest.fund=hdfc-flexi-cap
 * and this runner ingests that one document. Without the property the bean
 * doesn't exist and the app starts (and, having no web server yet, exits)
 * without touching anything.
 */
@Component
@ConditionalOnProperty("sidekick.ingest.file")
class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final IngestionService ingestion;
    private final String file;
    private final String fundId;
    private final String displayName;

    IngestionRunner(IngestionService ingestion,
                    @Value("${sidekick.ingest.file}") String file,
                    @Value("${sidekick.ingest.fund}") String fundId,
                    @Value("${sidekick.ingest.name:}") String displayName) {
        this.ingestion = ingestion;
        this.file = file;
        this.fundId = fundId;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Ingesting {} as fund '{}'", file, fundId);
        IngestionService.Result result = ingestion.ingest(Path.of(file), fundId,
                displayName.isBlank() ? null : displayName);
        log.info("Done: document {} -> {} pages, {} chunks persisted",
                result.documentId(), result.pages(), result.chunkCount());
    }
}
