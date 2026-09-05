package sidekick.answering;

import java.util.List;

/**
 * The flight recorder for one question (PLAN §3 tracing scope): what
 * happened, where the time went, what it cost. Hand-built, zero frameworks —
 * but deliberately shaped like OpenTelemetry spans (stages carry start +
 * duration, metadata is flat attributes), so a post-v1 OTLP exporter at the
 * edge is a mapping, not a redesign.
 *
 * @param traceId          unique per question
 * @param fundId           which fund was asked
 * @param model            LLM route used; null when gate 1 refused pre-LLM
 * @param stages           span-like timings: retrieval (incl. query
 *                         embedding), llm, validation
 * @param topScore         best retrieval similarity (gate 1's input)
 * @param gate1Passed      whether the LLM was called at all
 * @param retrieved        chunk ids/sections/pages/scores (ids only — the
 *                         full text lives in Answer, not in log lines)
 * @param promptTokens     provider-reported cost; null if unavailable/unused
 * @param completionTokens ditto
 * @param outcome          how the question ended, with the refusal cause
 */
public record AskTrace(
        String traceId,
        String fundId,
        String model,
        List<Stage> stages,
        double topScore,
        boolean gate1Passed,
        List<ChunkRef> retrieved,
        Integer promptTokens,
        Integer completionTokens,
        Outcome outcome
) {

    public enum Outcome {
        ANSWERED,
        REFUSED_GATE1,      // retrieval found nothing worth an LLM call
        REFUSED_BY_MODEL,   // model said NOT_IN_DOCUMENT (honest refusal)
        REFUSED_VALIDATION  // gate 2 rejected the model's answer
    }

    /** One pipeline stage, span-shaped. */
    public record Stage(String name, long startEpochMillis, long durationMillis) {
    }

    /** Enough to identify a chunk in logs without dumping its text. */
    public record ChunkRef(String chunkId, String section, int page, double score) {
    }
}
