package sidekick.eval;

import java.util.List;

/**
 * What came back from the full ask pipeline for one question.
 *
 * @param outcome        did the system answer or refuse?
 * @param answerText     the answer (or the refusal message)
 * @param citedChunkIds      chunk ids the answer claims to be based on
 * @param citationsValid     the citation validator's verdict (every cited chunk
 *                           was actually retrieved, every answer cites something,
 *                           quotes appear verbatim in the cited chunk)
 * @param validationProblems the validator's reasons when it rejected the output
 *                           — the diagnostic trail for benchmarking models
 */
public record AskResult(
        Outcome outcome,
        String answerText,
        List<String> citedChunkIds,
        boolean citationsValid,
        List<String> validationProblems
) {

    public enum Outcome { ANSWERED, REFUSED }
}
