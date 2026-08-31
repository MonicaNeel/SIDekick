package sidekick.retrieval.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sidekick.embedding.VectorCodec;
import sidekick.ingestion.ChunkFinder;
import sidekick.ingestion.DocumentIngested;
import sidekick.retrieval.InMemoryVectorIndex;

import java.util.List;

/**
 * Keeps the in-memory index in sync with Postgres (PLAN §5):
 * - full load at startup (RAM is the index, Postgres the system of record);
 * - full reload whenever a document finishes ingesting. Modulith's event
 *   publication registry persists DocumentIngested, so a crash between DB
 *   commit and this listener means replay, not silent drift.
 *
 * Reload is full-replace, not incremental — at this corpus size a reload is
 * milliseconds, and replace-on-refetch semantics fall out for free.
 */
@Component
class IndexLoader {

    private static final Logger log = LoggerFactory.getLogger(IndexLoader.class);

    private final ChunkFinder chunkFinder;
    private final InMemoryVectorIndex index;

    IndexLoader(ChunkFinder chunkFinder, InMemoryVectorIndex index) {
        this.chunkFinder = chunkFinder;
        this.index = index;
    }

    // ApplicationStartedEvent, NOT ApplicationReadyEvent: ApplicationRunners
    // (the ingest/eval CLIs) execute between the two, and they need a loaded
    // index. Learned the hard way: first eval run scored 0.0% against an
    // index that would have loaded right after the eval finished.
    @EventListener(ApplicationStartedEvent.class)
    public void onStartup() {
        reload("startup");
    }

    @ApplicationModuleListener
    public void on(DocumentIngested event) {
        reload("DocumentIngested for fund " + event.fundId());
    }

    private void reload(String trigger) {
        List<InMemoryVectorIndex.Entry> entries = chunkFinder.findAll().stream()
                .map(c -> new InMemoryVectorIndex.Entry(c.id().toString(), c.fundId(),
                        c.section(), c.page(), c.endPage(), c.text(), VectorCodec.toFloats(c.embedding())))
                .toList();
        index.replaceAll(entries);
        log.info("Vector index reloaded ({}): {} chunks", trigger, entries.size());
    }
}
