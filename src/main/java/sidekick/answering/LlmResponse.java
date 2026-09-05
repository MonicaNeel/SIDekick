package sidekick.answering;

/**
 * What an LLM call returns: the text plus the token counts the provider
 * reports (null when the provider omits usage). Token counts feed AskTrace —
 * they are the cost meter of every question.
 */
public record LlmResponse(String content, Integer promptTokens, Integer completionTokens) {
}
