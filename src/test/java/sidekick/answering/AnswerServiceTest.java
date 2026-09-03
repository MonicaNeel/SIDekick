package sidekick.answering;

import org.junit.jupiter.api.Test;
import sidekick.retrieval.Retriever;
import sidekick.retrieval.ScoredChunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pipeline's decision logic with a scripted retriever and LLM — every
 * path a real question can take, no network anywhere.
 */
class AnswerServiceTest {

    private static final AnswerService.Config CONFIG = new AnswerService.Config(5, 0.5);
    private static final PromptAssembler PROMPTS =
            new PromptAssembler("system", "{fund_name} {chunks} {question}");

    private static ScoredChunk chunk(double score, String text) {
        return new ScoredChunk("c1", "f", "LOAD STRUCTURE", 24, 24, text, score);
    }

    private static Retriever returning(ScoredChunk... chunks) {
        return (question, fundId, topK) -> List.of(chunks);
    }

    private static AnswerService service(Retriever retriever, String llmOutput) {
        return new AnswerService(retriever, PROMPTS, (sys, user) -> llmOutput,
                new CitationValidator(), CONFIG);
    }

    @Test
    void validCitedAnswerPassesThrough() {
        AnswerService service = service(returning(chunk(0.8, "Exit load is 1% within one year.")),
                "The exit load is 1% within one year [1].");

        Answer answer = service.ask("exit load?", "f", "Fund");

        assertEquals(Answer.Outcome.ANSWERED, answer.outcome());
        assertEquals(1, answer.citations().size());
        assertEquals("LOAD STRUCTURE", answer.citations().get(0).section());
    }

    @Test
    void gate1RefusesWithoutAnLlmCallWhenRetrievalFindsNothingGood() {
        LlmClient explodingLlm = (sys, user) -> {
            throw new AssertionError("LLM must not be called below the gate-1 floor");
        };
        AnswerService service = new AnswerService(returning(chunk(0.3, "irrelevant")),
                PROMPTS, explodingLlm, new CitationValidator(), CONFIG);

        Answer answer = service.ask("q?", "f", "Fund");

        assertEquals(Answer.Outcome.REFUSED, answer.outcome());
        assertTrue(answer.text().contains("LOAD STRUCTURE"), "refusal carries the pointer");
        assertEquals(24, answer.pointerPage());
    }

    @Test
    void modelRefusalBecomesRefusalWithPointer() {
        AnswerService service = service(returning(chunk(0.8, "text")), "NOT_IN_DOCUMENT");

        Answer answer = service.ask("q?", "f", "Fund");

        assertEquals(Answer.Outcome.REFUSED, answer.outcome());
        assertEquals("LOAD STRUCTURE", answer.pointerSection());
    }

    @Test
    void gate2TurnsInvalidAnswerIntoRefusalWithProblemsRecorded() {
        // Model answers confidently but cites nothing — the free-tier failure
        // mode the whole gate exists for.
        AnswerService service = service(returning(chunk(0.8, "text")),
                "The exit load is definitely 2.5%, trust me.");

        Answer answer = service.ask("q?", "f", "Fund");

        assertEquals(Answer.Outcome.REFUSED, answer.outcome());
        assertTrue(answer.validationProblems().get(0).contains("no excerpts"));
    }

    @Test
    void emptyRetrievalRefusesWithoutPointer() {
        AnswerService service = service(returning(), "unused");

        Answer answer = service.ask("q?", "totally-unknown-fund", "Fund");

        assertEquals(Answer.Outcome.REFUSED, answer.outcome());
        assertEquals("Not found in this document.", answer.text());
    }
}
