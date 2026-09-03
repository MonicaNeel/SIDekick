package sidekick.answering;

/** The LLM call failed (after retries) — configuration, network, or provider. */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
