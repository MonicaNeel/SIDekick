package sidekick.answering;

/**
 * The tiny hand-written LLM port (PLAN §4 hard rules): providers and models
 * swap behind this via config, callers never know which one answered.
 */
public interface LlmClient {

    /** @throws LlmException when the provider fails after retries */
    LlmResponse complete(String systemPrompt, String userPrompt);
}
