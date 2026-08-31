package sidekick.retrieval;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sidekick.embedding.TextEmbedder;
import sidekick.ingestion.IngestionService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The full step-2 -> step-3 handshake against a real Postgres: ingest a
 * document, the DocumentIngested event fires, the ASYNC index loader reloads,
 * and the retriever finds the chunk — with the fund filter respected.
 */
@SpringBootTest(properties = {
        "sidekick.embedding.stub=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class RetrievalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class StubEmbedderConfiguration {
        @Bean
        TextEmbedder stubEmbedder() {
            // Same text -> same vector, so a query phrased like the chunk text
            // scores 1.0 against it. Good enough to prove the plumbing.
            return new TextEmbedder() {
                @Override
                public float[] embed(String text) {
                    float[] v = new float[384];
                    v[Math.floorMod(text.strip().toLowerCase().hashCode(), 384)] = 1f;
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
    Retriever retriever;

    @Test
    void ingestedDocumentBecomesSearchableViaTheEventListener(@TempDir Path dir) throws Exception {
        ingestion.ingest(writeTestPdf(dir), "it-fund");

        // The index loader listens asynchronously — poll until it has run.
        List<ScoredChunk> results = awaitResults("An exit load of 1% applies.", "it-fund");

        assertTrue(results.get(0).text().contains("exit load"),
                "the ingested chunk must come back from search");
        assertEquals("it-fund", results.get(0).fundId());
        assertEquals(1, results.get(0).page());

        assertTrue(retriever.search("anything", "some-other-fund", 3).isEmpty(),
                "fund filter must exclude other funds");
    }

    private List<ScoredChunk> awaitResults(String question, String fundId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            List<ScoredChunk> results = retriever.search(question, fundId, 3);
            if (!results.isEmpty()) {
                return results;
            }
            Thread.sleep(200);
        }
        fail("index was not reloaded within 15s of DocumentIngested");
        return List.of(); // unreachable
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
                cs.showText("An exit load of 1% applies.");
                cs.endText();
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }
}
