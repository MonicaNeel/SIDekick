package sidekick.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sidekick.embedding.TextEmbedder;
import sidekick.ingestion.internal.ChunkEntity;
import sidekick.ingestion.internal.ChunkRepository;
import sidekick.ingestion.internal.DocumentRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole pipeline against a REAL Postgres (Testcontainers spins one up in
 * Docker just for this test): PDF in -> chunk rows with vector bytes out,
 * DocumentIngested announced, and re-ingesting replaces instead of duplicating.
 *
 * The embedder is a stub (sidekick.embedding.stub=true) — model quality has
 * its own calibration test; this test is about persistence semantics.
 */
@SpringBootTest(properties = {
        "sidekick.embedding.stub=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
@RecordApplicationEvents
class IngestionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class StubEmbedderConfiguration {
        @Bean
        TextEmbedder stubEmbedder() {
            return new TextEmbedder() {
                @Override
                public float[] embed(String text) {
                    float[] v = new float[384];
                    v[Math.floorMod(text.hashCode(), 384)] = 1f; // deterministic, unit length
                    return v;
                }

                @Override
                public int dimension() {
                    return 384;
                }

                @Override
                public void close() {
                }
            };
        }
    }

    @Autowired
    IngestionService ingestion;
    @Autowired
    ChunkRepository chunks;
    @Autowired
    DocumentRepository documents;

    @Test
    void ingestsPersistsAndAnnounces(@TempDir Path dir, ApplicationEvents events) throws IOException {
        Path pdf = writeTestPdf(dir);

        IngestionService.Result result = ingestion.ingest(pdf, "test-fund");

        assertTrue(result.chunkCount() > 0);
        List<ChunkEntity> stored = chunks.findByFundId("test-fund");
        assertEquals(result.chunkCount(), stored.size());
        stored.forEach(c -> assertEquals(384 * 4, c.getEmbedding().length,
                "every chunk must carry a full vector as bytes"));
        assertFalse(stored.get(0).getText().isBlank());
        assertEquals(1, events.stream(DocumentIngested.class).count(),
                "exactly one DocumentIngested must be announced");

        // Replace-on-refetch: ingesting the same fund again must not duplicate.
        ingestion.ingest(pdf, "test-fund");
        assertEquals(result.chunkCount(), chunks.findByFundId("test-fund").size());
        assertEquals(1, documents.findByFundId("test-fund").size());
    }

    private static Path writeTestPdf(Path dir) throws IOException {
        Path pdf = dir.resolve("mini-sid.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("C. LOAD STRUCTURE");
                cs.newLineAtOffset(0, -20);
                cs.showText("An exit load of 1% applies if units are redeemed within one year.");
                cs.endText();
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }
}
