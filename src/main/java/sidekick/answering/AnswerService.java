package sidekick.answering;

import sidekick.retrieval.Retriever;
import sidekick.retrieval.ScoredChunk;

import java.util.List;

/**
 * The full ask pipeline (PLAN §6): retrieve -> gate 1 -> prompt -> LLM ->
 * gate 2 -> answer or refusal-with-pointer. Zero Spring imports; wired at the
 * module edge.
 *
 * Gate 1 is deliberately only a FLOOR (ADR-008): measured score ranges of
 * answerable and unanswerable questions overlap, so a threshold cannot
 * separate them — it merely skips the LLM call when retrieval found
 * essentially nothing. The refusal duty lives in the prompt contract and in
 * gate 2 (CitationValidator).
 */
public final class AnswerService {

    /** @param minTopScore gate-1 floor: below this, refuse without an LLM call */
    public record Config(int topK, double minTopScore) {
    }

    private static final String REFUSAL_TEXT =
            "Not found in this document. The nearest relevant section is '%s' starting on page %d.";
    private static final String REFUSAL_TEXT_EMPTY =
            "Not found in this document.";

    private final Retriever retriever;
    private final PromptAssembler prompts;
    private final LlmClient llm;
    private final CitationValidator validator;
    private final Config config;

    public AnswerService(Retriever retriever, PromptAssembler prompts, LlmClient llm,
                         CitationValidator validator, Config config) {
        this.retriever = retriever;
        this.prompts = prompts;
        this.llm = llm;
        this.validator = validator;
        this.config = config;
    }

    public Answer ask(String question, String fundId, String fundName) {
        List<ScoredChunk> retrieved = retriever.search(question, fundId, config.topK());

        // Gate 1: nothing worth an LLM call.
        if (retrieved.isEmpty() || retrieved.get(0).score() < config.minTopScore()) {
            return refusal(retrieved, List.of());
        }

        String modelOutput = llm.complete(prompts.systemPrompt(),
                prompts.userPrompt(fundName, question, retrieved));

        CitationValidator.Result validation = validator.validate(modelOutput,
                retrieved.stream().map(ScoredChunk::text).toList());

        // Gate 2: the model refused, or its answer failed validation — either
        // way the user gets an honest refusal, never an unverified answer.
        if (validation.refusal() || !validation.valid()) {
            return refusal(retrieved, validation.problems());
        }

        List<Answer.Citation> citations = validation.citations().stream()
                .map(n -> {
                    ScoredChunk chunk = retrieved.get(n - 1);
                    return new Answer.Citation(n, chunk.chunkId(), chunk.section(),
                            chunk.page(), chunk.endPage());
                })
                .toList();
        return new Answer(Answer.Outcome.ANSWERED, modelOutput, citations,
                null, null, retrieved, List.of());
    }

    /** Refusal-plus-pointer (ADR-005): aim the reader at the nearest section. */
    private static Answer refusal(List<ScoredChunk> retrieved, List<String> problems) {
        if (retrieved.isEmpty()) {
            return new Answer(Answer.Outcome.REFUSED, REFUSAL_TEXT_EMPTY, List.of(),
                    null, null, retrieved, problems);
        }
        ScoredChunk top = retrieved.get(0);
        return new Answer(Answer.Outcome.REFUSED,
                REFUSAL_TEXT.formatted(top.section(), top.page()),
                List.of(), top.section(), top.page(), retrieved, problems);
    }
}
