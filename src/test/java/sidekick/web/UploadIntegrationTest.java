package sidekick.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sidekick.embedding.TextEmbedder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upload escape hatch end to end: accepted upload -> async job -> DONE and
 * searchable; garbage PDF -> FAILED with the sanity message; pinned eval fund
 * -> 409 before any work happens (ADR-007).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sidekick.embedding.stub=true",
        "sidekick.ingest.pinned-funds=hdfc-flexi-cap,sbi-small-cap",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class UploadIntegrationTest {

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
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void uploadedSidBecomesAnIngestedFund(@TempDir Path dir) throws Exception {
        Path pdf = writePdf(dir, true);

        HttpResponse<String> accepted = upload(pdf, "uploaded-fund", "Uploaded Test Fund");
        assertEquals(202, accepted.statusCode());
        String jobId = JSON.readTree(accepted.body()).get("jobId").asText();

        JsonNode job = awaitTerminal(jobId);
        assertEquals("DONE", job.get("status").asText(), job.toString());
        assertTrue(job.get("chunkCount").asInt() > 0);

        JsonNode funds = JSON.readTree(get("/api/funds").body());
        boolean listed = false;
        for (JsonNode fund : funds) {
            listed |= fund.get("fundId").asText().equals("uploaded-fund")
                    && fund.get("displayName").asText().equals("Uploaded Test Fund");
        }
        assertTrue(listed, "uploaded fund must appear in the catalog with its display name");
    }

    @Test
    void nonSidPdfFailsWithFriendlySanityMessage(@TempDir Path dir) throws Exception {
        Path pdf = writePdf(dir, false);

        HttpResponse<String> accepted = upload(pdf, "not-a-sid", null);
        assertEquals(202, accepted.statusCode());
        JsonNode job = awaitTerminal(JSON.readTree(accepted.body()).get("jobId").asText());

        assertEquals("FAILED", job.get("status").asText());
        assertTrue(job.get("error").asText().contains("does not look like a SID"));
    }

    @Test
    void pinnedEvalFundIsRejectedWith409(@TempDir Path dir) throws Exception {
        HttpResponse<String> response = upload(writePdf(dir, true), "sbi-small-cap", null);

        assertEquals(409, response.statusCode());
        assertTrue(JSON.readTree(response.body()).get("error").asText().contains("pinned"));
    }

    @Test
    void badSlugAndUnknownJobAreRejected(@TempDir Path dir) throws Exception {
        assertEquals(400, upload(writePdf(dir, true), "Not A Slug!", null).statusCode());
        assertEquals(404, get("/api/jobs/00000000-0000-0000-0000-000000000000").statusCode());
    }

    // --- helpers ---

    private JsonNode awaitTerminal(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode job = JSON.readTree(get("/api/jobs/" + jobId).body());
            String status = job.get("status").asText();
            if (status.equals("DONE") || status.equals("FAILED")) {
                return job;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("job " + jobId + " never reached a terminal state");
    }

    private HttpResponse<String> upload(Path pdf, String fundId, String displayName)
            throws IOException, InterruptedException {
        String boundary = "----sidekick" + System.nanoTime();
        var body = new java.io.ByteArrayOutputStream();
        var writer = new java.io.PrintWriter(body, true, java.nio.charset.StandardCharsets.UTF_8);
        writer.printf("--%s\r\nContent-Disposition: form-data; name=\"fundId\"\r\n\r\n%s\r\n", boundary, fundId);
        if (displayName != null) {
            writer.printf("--%s\r\nContent-Disposition: form-data; name=\"displayName\"\r\n\r\n%s\r\n",
                    boundary, displayName);
        }
        writer.printf("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n", boundary, pdf.getFileName());
        writer.flush();
        body.write(Files.readAllBytes(pdf));
        writer.printf("\r\n--%s--\r\n", boundary);
        writer.flush();

        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/documents"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Path writePdf(Path dir, boolean sidLike) throws IOException {
        Path pdf = dir.resolve(sidLike ? "plausible-sid.pdf" : "not-a-sid.pdf");
        String[] lines = sidLike
                ? new String[]{"PART I. HIGHLIGHTS/SUMMARY OF THE SCHEME", "details",
                "A. HOW WILL THE SCHEME ALLOCATE ITS ASSETS?", "equity 65-100",
                "B. WHERE WILL THE SCHEME INVEST?", "in equities",
                "C. LOAD STRUCTURE", "exit load 1% within one year",
                "D. HOW WILL THE SCHEME BENCHMARK ITS PERFORMANCE?", "an index",
                "E. WHO MANAGES THE SCHEME?", "a manager"}
                : new String[]{"Quarterly sales report", "Revenue was up 4%.", "See appendix."};
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(72, 720);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -16);
                }
                cs.endText();
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }
}
