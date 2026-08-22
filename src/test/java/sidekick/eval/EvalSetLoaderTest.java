package sidekick.eval;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the eval set: every time eval-set.json is edited, `mvn test` confirms
 * it still follows the authoring rules in eval/README.md.
 */
class EvalSetLoaderTest {

    private final EvalSetLoader loader = new EvalSetLoader();

    @Test
    void theRealEvalSetIsValid() throws IOException {
        // Maven runs tests with the repo root as working directory.
        EvalSet set = loader.load(Path.of("eval", "eval-set.json"));

        assertEquals(EvalSet.CURRENT_SCHEMA_VERSION, set.schemaVersion());
        assertFalse(set.cases().isEmpty());
    }

    @Test
    void rejectsDuplicateIds(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                { "schema_version": 1, "cases": [ %s, %s ] }
                """.formatted(answerable("same-id"), answerable("same-id")));

        InvalidEvalSetException e =
                assertThrows(InvalidEvalSetException.class, () -> loader.load(file));
        assertTrue(e.problems().stream().anyMatch(p -> p.contains("duplicate id")));
    }

    @Test
    void rejectsAnswerableCaseWithoutSourcePages(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                { "schema_version": 1, "cases": [ {
                    "id": "broken", "type": "answerable",
                    "question": "What is the exit load?", "fund_id": "some-fund",
                    "expected_answer": "1%", "source_pages": [],
                    "expected_section": "Load Structure", "provenance": "hand",
                    "notes": null
                } ] }
                """);

        InvalidEvalSetException e =
                assertThrows(InvalidEvalSetException.class, () -> loader.load(file));
        assertTrue(e.problems().stream().anyMatch(p -> p.contains("source_page")));
    }

    @Test
    void rejectsUnanswerableCaseThatHasAnAnswer(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                { "schema_version": 1, "cases": [ {
                    "id": "broken", "type": "unanswerable",
                    "question": "Is there a lock-in?", "fund_id": "some-fund",
                    "expected_answer": "No lock-in", "source_pages": [],
                    "expected_section": null, "provenance": "hand", "notes": null
                } ] }
                """);

        InvalidEvalSetException e =
                assertThrows(InvalidEvalSetException.class, () -> loader.load(file));
        assertTrue(e.problems().stream()
                .anyMatch(p -> p.contains("expected_answer: null")));
    }

    @Test
    void rejectsMisspelledFieldNames(@TempDir Path dir) throws IOException {
        // "quesiton" instead of "question" — must fail, not silently load.
        Path file = write(dir, """
                { "schema_version": 1, "cases": [ {
                    "id": "typo-case", "type": "answerable",
                    "quesiton": "What is the exit load?", "fund_id": "some-fund",
                    "expected_answer": "1%", "source_pages": [12],
                    "expected_section": "Load Structure", "provenance": "hand",
                    "notes": null
                } ] }
                """);

        assertThrows(UnrecognizedPropertyException.class, () -> loader.load(file));
    }

    @Test
    void collectsAllProblemsInOnePass(@TempDir Path dir) throws IOException {
        // Two broken cases -> both reported, not just the first.
        Path file = write(dir, """
                { "schema_version": 1, "cases": [
                  { "id": "", "type": "answerable", "question": "q", "fund_id": "f",
                    "expected_answer": "a", "source_pages": [1],
                    "expected_section": "s", "provenance": "hand", "notes": null },
                  { "id": "no-section", "type": "answerable", "question": "q",
                    "fund_id": "f", "expected_answer": "a", "source_pages": [1],
                    "expected_section": null, "provenance": "hand", "notes": null }
                ] }
                """);

        InvalidEvalSetException e =
                assertThrows(InvalidEvalSetException.class, () -> loader.load(file));
        assertTrue(e.problems().size() >= 2,
                "expected both problems reported, got: " + e.problems());
    }

    private static Path write(Path dir, String json) throws IOException {
        Path file = dir.resolve("eval-set.json");
        Files.writeString(file, json);
        return file;
    }

    private static String answerable(String id) {
        return """
                { "id": "%s", "type": "answerable",
                  "question": "What is the exit load?", "fund_id": "some-fund",
                  "expected_answer": "1%%", "source_pages": [12],
                  "expected_section": "Load Structure", "provenance": "hand",
                  "notes": null }
                """.formatted(id);
    }
}
