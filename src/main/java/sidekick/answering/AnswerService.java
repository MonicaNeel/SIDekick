package sidekick.answering;

import sidekick.retrieval.Retriever;
import sidekick.retrieval.ScoredChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The full ask pipeline (PLAN §6): retrieve -> gate 1 -> prompt -> LLM ->
 * gate 2 -> answer or refusal-with-pointer. Zero Spring imports; wired at the
 * module edge. Every question is flight-recorded into an AskTrace (PLAN §3).
 *
 * Gate 1 is deliberately only a FLOOR (ADR-008): measured score ranges of
 * answerable and unanswerable questions overlap, so a threshold cannot
 * separate them — it merely skips the LLM call when retrieval found
 * essentially nothing. The refusal duty lives in the prompt contract and in
 * gate 2 (CitationValidator).
 */
public final class AnswerService {

    /**
     * @param minTopScore gate-1 floor: below this, refuse without an LLM call
     * @param modelName   for the trace; the actual route lives in LlmClient
     */
    public record Config(int topK, double minTopScore, String modelName) {
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
        String traceId = UUID.randomUUID().toString();
        List<AskTrace.Stage> stages = new ArrayList<>();

        List<ScoredChunk> retrieved = timed(stages, "retrieval",
                () -> retriever.search(question, fundId, config.topK()));
        double topScore = retrieved.isEmpty() ? 0 : retrieved.get(0).score();

        // Gate 1: nothing worth an LLM call.
        if (retrieved.isEmpty() || topScore < config.minTopScore()) {
            AskTrace trace = new AskTrace(traceId, fundId, null, stages, topScore, false,
                    chunkRefs(retrieved), null, null, AskTrace.Outcome.REFUSED_GATE1);
            return refusal(retrieved, List.of(), trace);
        }

        LlmResponse response = timed(stages, "llm",
                () -> llm.complete(prompts.systemPrompt(),
                        prompts.userPrompt(fundName, question, retrieved)));

        CitationValidator.Result validation = timed(stages, "validation",
                () -> validator.validate(response.content(),
                        retrieved.stream().map(ScoredChunk::text).toList()));

        AskTrace.Outcome outcome = validation.refusal() ? AskTrace.Outcome.REFUSED_BY_MODEL
                : validation.valid() ? AskTrace.Outcome.ANSWERED
                : AskTrace.Outcome.REFUSED_VALIDATION;
        AskTrace trace = new AskTrace(traceId, fundId, config.modelName(), stages, topScore, true,
                chunkRefs(retrieved), response.promptTokens(), response.completionTokens(), outcome);

        // Gate 2: the model refused, or its answer failed validation — either
        // way the user gets an honest refusal, never an unverified answer.
        if (outcome != AskTrace.Outcome.ANSWERED) {
            return refusal(retrieved, validation.problems(), trace);
        }

        List<Answer.Citation> citations = validation.citations().stream()
                .map(n -> {
                    ScoredChunk chunk = retrieved.get(n - 1);
                    return new Answer.Citation(n, chunk.chunkId(), chunk.section(),
                            chunk.page(), chunk.endPage());
                })
                .toList();
        return new Answer(Answer.Outcome.ANSWERED, response.content(), citations,
                null, null, retrieved, List.of(), trace);
    }

    /** Run one stage and record it span-style: name, start, duration. */
    private static <T> T timed(List<AskTrace.Stage> stages, String name, java.util.function.Supplier<T> work) {
        long startEpoch = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        T result = work.get();
        stages.add(new AskTrace.Stage(name, startEpoch, (System.nanoTime() - startNanos) / 1_000_000));
        return result;
    }

    private static List<AskTrace.ChunkRef> chunkRefs(List<ScoredChunk> retrieved) {
        return retrieved.stream()
                .map(c -> new AskTrace.ChunkRef(c.chunkId(), c.section(), c.page(), c.score()))
                .toList();
    }

    /** Refusal-plus-pointer (ADR-005): aim the reader at the nearest section. */
    private static Answer refusal(List<ScoredChunk> retrieved, List<String> problems, AskTrace trace) {
        if (retrieved.isEmpty()) {
            return new Answer(Answer.Outcome.REFUSED, REFUSAL_TEXT_EMPTY, List.of(),
                    null, null, retrieved, problems, trace);
        }
        ScoredChunk top = retrieved.get(0);
        return new Answer(Answer.Outcome.REFUSED,
                REFUSAL_TEXT.formatted(top.section(), top.page()),
                List.of(), top.section(), top.page(), retrieved, problems, trace);
    }
}
