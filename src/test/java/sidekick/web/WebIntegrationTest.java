package sidekick.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sidekick.answering.LlmClient;
import sidekick.answering.LlmResponse;
import sidekick.embedding.TextEmbedder;
import sidekick.ingestion.IngestionService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole web surface against a real Postgres: funds list, the ask flow
 * (stub embedder + stub LLM — no network beyond localhost), the error
 * contract. Uses the JDK HTTP client directly — Boot 4 removed
 * TestRestTemplate, and the JDK client is churn-proof.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sidekick.embedding.stub=true",
        // The stub embedder's one-hot vectors score ~0 against any question,
        // which gate 1 would (correctly!) refuse — disable the floor, it has
        // its own unit test and this IT is about the web contract.
        "sidekick.answering.min-top-score=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class WebIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class Stubs {
        @Bean
        TextEmbedder stubEmbedder() {
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

        @Bean
        @Primary
        LlmClient stubLlm() {
            return (sys, user) -> new LlmResponse("The exit load is 1% within one year [1].", 100, 20);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    IngestionService ingestion;

    @BeforeEach
    void ingestOnce(@TempDir Path dir) throws Exception {
        if (!get("/api/funds").body().toString().contains("web-test-fund")) {
            ingestion.ingest(writeTestPdf(dir), "web-test-fund");
            awaitSearchable();
        }
    }

    @Test
    void fundsListShowsIngestedDocumentWithSourceDisclosure() throws Exception {
        HttpResponse<String> response = get("/api/funds");
        JsonNode funds = JSON.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertTrue(funds.isArray() && funds.size() >= 1);
        JsonNode fund = funds.get(0);
        assertEquals("web-test-fund", fund.get("fundId").asText());
        assertEquals("mini-sid.pdf", fund.get("fileName").asText());
        assertTrue(fund.hasNonNull("ingestedAt"), "UI must be able to disclose document date");
    }

    @Test
    void askReturnsCitedAnswerWithDebugPayloadAndTrace() throws Exception {
        HttpResponse<String> response = post("/api/funds/web-test-fund/ask",
                "{\"question\": \"what is the exit load?\"}");

        assertEquals(200, response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("ANSWERED", body.get("outcome").asText());
        assertTrue(body.get("text").asText().contains("exit load"));
        assertEquals(1, body.get("citations").size());
        assertTrue(body.get("retrieved").size() >= 1, "debug panel needs the chunks");
        assertEquals("ANSWERED", body.get("trace").get("outcome").asText());
        assertTrue(body.get("trace").get("stages").size() >= 2, "trace rides along");
    }

    @Test
    void unknownFundIs404AndBlankQuestionIs400() throws Exception {
        assertEquals(404, post("/api/funds/no-such-fund/ask",
                "{\"question\": \"anything\"}").statusCode());
        assertEquals(400, post("/api/funds/web-test-fund/ask",
                "{\"question\": \"  \"}").statusCode());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void awaitSearchable() throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode body = JSON.readTree(post("/api/funds/web-test-fund/ask",
                    "{\"question\": \"exit load?\"}").body());
            if (body.has("retrieved") && body.get("retrieved").size() > 0) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("index never loaded the ingested document");
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
