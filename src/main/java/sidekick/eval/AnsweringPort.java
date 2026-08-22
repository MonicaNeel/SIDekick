package sidekick.eval;

/**
 * What the eval runner needs from the answering side (build-order step 4).
 *
 * In step 3 the runner runs in retrieval-only mode and this port simply isn't
 * plugged in — no LLM calls, so eval runs are free and fast while iterating
 * on chunking and retrieval.
 */
public interface AnsweringPort {

    AskResult ask(String question, String fundId);
}
