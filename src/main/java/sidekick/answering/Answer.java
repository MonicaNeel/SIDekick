package sidekick.answering;

import sidekick.retrieval.ScoredChunk;

import java.util.List;

/**
 * What the ask pipeline returns: a cited answer or a refusal-with-pointer
 * (ADR-005), plus everything the debug panel needs (the raw retrieved chunks
 * and any validation problems).
 *
 * @param outcome            ANSWERED or REFUSED
 * @param text               the answer text, or the refusal message
 * @param citations          resolved citations (empty for refusals)
 * @param pointerSection     nearest relevant section (refusals; null otherwise)
 * @param pointerPage        page of that section (refusals; null otherwise)
 * @param retrieved          the chunks retrieval returned (debug panel, PLAN §3)
 * @param validationProblems why gate 2 rejected the model's output, if it did
 * @param trace              the flight recorder for this question (PLAN §3)
 */
public record Answer(
        Outcome outcome,
        String text,
        List<Citation> citations,
        String pointerSection,
        Integer pointerPage,
        List<ScoredChunk> retrieved,
        List<String> validationProblems,
        AskTrace trace
) {

    public enum Outcome { ANSWERED, REFUSED }

    public record Citation(int excerptNumber, String chunkId, String section, int page, int endPage) {
    }
}
