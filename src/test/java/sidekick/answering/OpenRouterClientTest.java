package sidekick.answering;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-written HTTP client against a local stub server (JDK built-in) —
 * proves auth header, request shape, response parsing, and the 429
 * retry-with-backoff that free-tier rate limits demand. No real API touched.
 */
class OpenRouterClientTest {

    private static final String OK_RESPONSE = """
            {"choices": [{"message": {"role": "assistant", "content": "The exit load is 1% [1]."}}],
             "usage": {"prompt_tokens": 812, "completion_tokens": 44}}
            """;

    private HttpServer server;
    private final List<String> authHeaders = new ArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OpenRouterClient client() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new OpenRouterClient(baseUrl, "test-key", "test/model", 3, 10);
    }

    private void respondWith(int... statusSequence) {
        server.createContext("/chat/completions", exchange -> {
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int call = requests.getAndIncrement();
            int status = statusSequence[Math.min(call, statusSequence.length - 1)];
            byte[] body = (status == 200 ? OK_RESPONSE : "{\"error\": \"rate limited\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    @Test
    void sendsBearerAuthAndParsesContentAndUsage() {
        respondWith(200);

        LlmResponse response = client().complete("sys", "user");

        assertEquals("The exit load is 1% [1].", response.content());
        assertEquals(812, response.promptTokens());
        assertEquals(44, response.completionTokens());
        assertEquals("Bearer test-key", authHeaders.get(0));
    }

    @Test
    void retriesOn429ThenSucceeds() {
        respondWith(429, 429, 200);

        LlmResponse response = client().complete("sys", "user");

        assertEquals("The exit load is 1% [1].", response.content());
        assertEquals(3, requests.get(), "two rate-limited attempts, then success");
    }

    @Test
    void givesUpAfterMaxAttempts() {
        respondWith(429);

        LlmException e = assertThrows(LlmException.class, () -> client().complete("sys", "user"));

        assertTrue(e.getMessage().contains("429"));
        assertEquals(3, requests.get(), "must stop at maxAttempts");
    }

    @Test
    void doesNotRetryClientErrors() {
        respondWith(400);

        assertThrows(LlmException.class, () -> client().complete("sys", "user"));

        assertEquals(1, requests.get(), "a 400 is our bug, not their weather — no retry");
    }

    @Test
    void missingApiKeyFailsFastWithoutNetworkCall() {
        OpenRouterClient noKey = new OpenRouterClient("http://127.0.0.1:1", "", "m", 3, 10);

        LlmException e = assertThrows(LlmException.class, () -> noKey.complete("s", "u"));

        assertTrue(e.getMessage().contains("OPENROUTER_API_KEY"));
    }
}
