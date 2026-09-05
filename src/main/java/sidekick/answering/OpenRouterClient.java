package sidekick.answering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Hand-written OpenRouter chat-completions client over java.net.http —
 * no SDK, no framework (PLAN §4). Free-tier routes rate-limit aggressively,
 * so 429/5xx responses retry with exponential backoff (PLAN §7: the eval
 * runner cannot fire 30 questions in 10 seconds without this).
 *
 * The API key comes in via constructor (wired from the OPENROUTER_API_KEY
 * environment variable at the module edge) and is never logged.
 */
public final class OpenRouterClient implements LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxAttempts;
    private final long initialBackoffMillis;

    public OpenRouterClient(String baseUrl, String apiKey, String model,
                            int maxAttempts, long initialBackoffMillis) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
    }

    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("OPENROUTER_API_KEY is not set — set the environment variable and restart");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(systemPrompt, userPrompt)))
                .build();

        long backoff = initialBackoffMillis;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return extractContent(response.body());
                }
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt == maxAttempts) {
                    throw new LlmException("OpenRouter returned HTTP " + status + ": " + truncate(response.body()));
                }
            } catch (IOException e) {
                if (attempt == maxAttempts) {
                    throw new LlmException("OpenRouter unreachable after " + maxAttempts + " attempts", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("interrupted while calling OpenRouter", e);
            }
            sleep(backoff);
            backoff *= 2;
        }
        throw new LlmException("unreachable"); // loop always returns or throws
    }

    private String requestBody(String systemPrompt, String userPrompt) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        body.put("temperature", 0.0); // deterministic-ish: we want compliance, not creativity
        return body.toString();
    }

    private static LlmResponse extractContent(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new LlmException("no message content in OpenRouter response: " + truncate(responseBody));
            }
            JsonNode usage = root.path("usage");
            return new LlmResponse(content.asText(),
                    intOrNull(usage.path("prompt_tokens")),
                    intOrNull(usage.path("completion_tokens")));
        } catch (IOException e) {
            throw new LlmException("cannot parse OpenRouter response", e);
        }
    }

    private static Integer intOrNull(JsonNode node) {
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("interrupted during backoff", e);
        }
    }

    private static String truncate(String s) {
        return s == null ? "" : s.substring(0, Math.min(s.length(), 300));
    }
}
