package sidekick.web.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import sidekick.answering.Answer;
import sidekick.answering.AnswerService;
import sidekick.answering.AskTrace;
import sidekick.answering.LlmException;
import sidekick.ingestion.DocumentFinder;

import java.util.List;

/**
 * POST /api/funds/{fundId}/ask — the primary flow (PLAN §3): question in,
 * grounded cited answer or refusal-with-pointer out, plus the retrieved
 * chunks (debug panel) and the AskTrace. This edge also emits the
 * one-JSON-line-per-question trace log.
 */
@RestController
class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    record AskRequest(String question) {
    }

    record CitationDto(int excerptNumber, String section, int page, int endPage) {
    }

    record RetrievedChunkDto(String section, int page, int endPage, double score, String text) {
    }

    record AskResponse(String outcome, String text, List<CitationDto> citations,
                       String pointerSection, Integer pointerPage,
                       List<String> validationProblems,
                       List<RetrievedChunkDto> retrieved, AskTrace trace) {
    }

    private final AnswerService answers;
    private final DocumentFinder documents;

    AskController(AnswerService answers, DocumentFinder documents) {
        this.answers = answers;
        this.documents = documents;
    }

    @PostMapping("/api/funds/{fundId}/ask")
    AskResponse ask(@PathVariable String fundId, @RequestBody AskRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question must not be blank");
        }
        if (!documents.fundExists(fundId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown fund: " + fundId);
        }

        Answer answer = answers.ask(request.question().strip(), fundId,
                documents.displayName(fundId));
        logTrace(answer.trace());
        return new AskResponse(
                answer.outcome().name(),
                answer.text(),
                answer.citations().stream()
                        .map(c -> new CitationDto(c.excerptNumber(), c.section(), c.page(), c.endPage()))
                        .toList(),
                answer.pointerSection(),
                answer.pointerPage(),
                answer.validationProblems(),
                answer.retrieved().stream()
                        .map(c -> new RetrievedChunkDto(c.section(), c.page(), c.endPage(),
                                c.score(), c.text()))
                        .toList(),
                answer.trace());
    }

    @ExceptionHandler(LlmException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    java.util.Map<String, String> llmFailure(LlmException e) {
        log.warn("LLM failure surfaced to API: {}", e.getMessage());
        return java.util.Map.of("error", "The language model is unavailable right now: " + e.getMessage());
    }

    private void logTrace(AskTrace trace) {
        try {
            log.info("askTrace {}", JSON.writeValueAsString(trace));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("could not serialize askTrace", e);
        }
    }
}
