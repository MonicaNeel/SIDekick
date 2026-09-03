package sidekick.answering;

import org.junit.jupiter.api.Test;
import sidekick.answering.CitationValidator.Result;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationValidatorTest {

    private final CitationValidator validator = new CitationValidator();

    private static final List<String> CHUNKS = List.of(
            "Exit Load: In respect of each purchase of Units, an Exit Load of 1.00% is payable if Units are redeemed within 1 year from the date of allotment.",
            "The scheme would be benchmarked against BSE Small Cap Index TRI.");

    @Test
    void acceptsProperlyCitedAnswer() {
        Result result = validator.validate(
                "The exit load is 1% within one year of allotment [1].", CHUNKS);

        assertTrue(result.valid());
        assertFalse(result.refusal());
        assertEquals(List.of(1), result.citations());
    }

    @Test
    void refusalTokenIsAValidRefusalNotAFailure() {
        Result result = validator.validate("NOT_IN_DOCUMENT", CHUNKS);

        assertTrue(result.refusal());
        assertTrue(result.valid());
    }

    @Test
    void rejectsAnswerWithNoCitations() {
        Result result = validator.validate("The exit load is 1% within one year.", CHUNKS);

        assertFalse(result.valid());
        assertTrue(result.problems().get(0).contains("no excerpts"));
    }

    @Test
    void rejectsCitationOfUnprovidedExcerpt() {
        Result result = validator.validate("The exit load is 1% [7].", CHUNKS);

        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("[7]")));
    }

    @Test
    void rejectsFabricatedQuote() {
        // The model "quotes" something the cited chunk never says — the
        // hallucination this validator exists to kill.
        Result result = validator.validate(
                "The document states \"exit load shall be waived for senior citizens\" [1].", CHUNKS);

        assertFalse(result.valid());
        assertTrue(result.problems().get(0).contains("not found verbatim"));
    }

    @Test
    void acceptsVerbatimQuoteDespiteWhitespaceDifferences() {
        // PDF text and model output break lines differently; whitespace must
        // not fail an honest quote.
        Result result = validator.validate(
                "It says \"an Exit Load of 1.00% is payable if   Units are\n redeemed within 1 year\" [1].",
                CHUNKS);

        assertTrue(result.valid(), () -> String.join("; ", result.problems()));
    }

    @Test
    void rejectsEmptyOutput() {
        assertFalse(validator.validate("  ", CHUNKS).valid());
    }

    @Test
    void collectsMultipleProblemsInOnePass() {
        Result result = validator.validate(
                "It says \"totally invented quote goes here\" [9].", CHUNKS);

        assertFalse(result.valid());
        assertTrue(result.problems().size() >= 2, "bad citation AND bad quote both reported");
    }
}
