package sidekick.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sidekick.embedding.TextEmbedder;
import sidekick.embedding.VectorCodec;
import sidekick.ingestion.internal.ChunkEntity;
import sidekick.ingestion.internal.ChunkRepository;
import sidekick.ingestion.internal.DocumentEntity;
import sidekick.ingestion.internal.DocumentRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * The ingestion pipeline end to end: extract -> chunk -> embed -> persist ->
 * announce. Replace-on-refetch semantics (PLAN §3): re-ingesting a fund
 * deletes its previous document and chunks in the same transaction, so
 * there is never a moment where old and new chunks coexist.
 *
 * NOTE: the whole method is one transaction, which currently includes the
 * ~1 minute of embedding. Fine for a CLI-triggered milestone; when async
 * ingestion jobs arrive, embedding moves out of the transactional window.
 */
@Service
public class IngestionService {

    /** What one ingestion produced (IDs and counts only). */
    public record Result(UUID documentId, int pages, int chunkCount) {
    }

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final TextEmbedder embedder;
    private final boolean prependSection;
    private final ApplicationEventPublisher events;

    public IngestionService(DocumentRepository documents, ChunkRepository chunks,
                            TextEmbedder embedder,
                            @Value("${sidekick.embedding.prepend-section:false}") boolean prependSection,
                            ApplicationEventPublisher events) {
        this.documents = documents;
        this.chunks = chunks;
        this.embedder = embedder;
        this.prependSection = prependSection;
        this.events = events;
    }

    @Transactional
    public Result ingest(Path pdfFile, String fundId) throws IOException {
        return ingest(pdfFile, fundId, null);
    }

    /** @param displayName human-readable fund name shown in the UI; null falls back to fundId */
    @Transactional
    public Result ingest(Path pdfFile, String fundId, String displayName) throws IOException {
        List<PageText> pages = new PdfTextExtractor().extract(pdfFile);
        List<Chunk> chunked = SectionAwareChunker.withDefaults().chunk(fundId, pages);
        List<EmbeddedChunk> embedded = new ChunkEmbedder(embedder, prependSection).embedAll(chunked);

        chunks.deleteByFundId(fundId);
        documents.deleteByFundId(fundId);

        DocumentEntity document = new DocumentEntity(fundId, displayName,
                pdfFile.getFileName().toString(), pages.size());
        documents.save(document);
        chunks.saveAll(embedded.stream()
                .map(e -> new ChunkEntity(document.getId(), fundId, e.chunk().section(),
                        e.chunk().page(), e.chunk().endPage(), e.chunk().text(),
                        VectorCodec.toBytes(e.vector())))
                .toList());

        events.publishEvent(new DocumentIngested(fundId, document.getId()));
        return new Result(document.getId(), pages.size(), embedded.size());
    }
}
