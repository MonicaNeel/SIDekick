package sidekick.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads eval-set.json and refuses to accept it if any case breaks the rules
 * in eval/README.md. Collects ALL problems in one pass instead of stopping at
 * the first, so fixing the file takes one round-trip, not thirty.
 */
public final class EvalSetLoader {

    private final ObjectMapper mapper;

    public EvalSetLoader() {
        this.mapper = new ObjectMapper()
                // A misspelled field name is an authoring bug — fail loudly.
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    /**
     * @throws IOException             if the file is missing or not valid JSON
     * @throws InvalidEvalSetException if the JSON parses but breaks eval-set rules
     */
    public EvalSet load(Path file) throws IOException {
        EvalSet set = mapper.readValue(Files.readString(file), EvalSet.class);
        List<String> problems = validate(set);
        if (!problems.isEmpty()) {
            throw new InvalidEvalSetException(problems);
        }
        return set;
    }

    private static List<String> validate(EvalSet set) {
        List<String> problems = new ArrayList<>();

        if (set.schemaVersion() != EvalSet.CURRENT_SCHEMA_VERSION) {
            problems.add("schema_version must be " + EvalSet.CURRENT_SCHEMA_VERSION
                    + " but was " + set.schemaVersion());
        }
        if (set.cases() == null || set.cases().isEmpty()) {
            problems.add("cases must not be empty");
            return problems;
        }

        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < set.cases().size(); i++) {
            EvalCase c = set.cases().get(i);
            String label = isBlank(c.id()) ? "case at index " + i : "case '" + c.id() + "'";

            if (isBlank(c.id())) {
                problems.add(label + ": id must not be blank");
            } else if (!seenIds.add(c.id())) {
                problems.add(label + ": duplicate id");
            }
            if (c.type() == null) problems.add(label + ": type is required");
            if (isBlank(c.question())) problems.add(label + ": question must not be blank");
            if (isBlank(c.fundId())) problems.add(label + ": fund_id must not be blank");
            if (c.provenance() == null) problems.add(label + ": provenance is required");

            if (c.type() == EvalCase.CaseType.ANSWERABLE) {
                if (isBlank(c.expectedAnswer())) {
                    problems.add(label + ": answerable case needs an expected_answer");
                }
                if (c.sourcePages() == null || c.sourcePages().isEmpty()) {
                    problems.add(label + ": answerable case needs at least one source_page");
                } else if (c.sourcePages().stream().anyMatch(p -> p == null || p < 1)) {
                    problems.add(label + ": source_pages must all be >= 1 (PDF pages are 1-based)");
                }
                if (isBlank(c.expectedSection())) {
                    problems.add(label + ": answerable case needs an expected_section");
                }
            } else if (c.type() == EvalCase.CaseType.UNANSWERABLE) {
                if (c.expectedAnswer() != null) {
                    problems.add(label + ": unanswerable case must have expected_answer: null");
                }
                if (c.sourcePages() != null && !c.sourcePages().isEmpty()) {
                    problems.add(label + ": unanswerable case must have empty source_pages");
                }
                if (c.expectedSection() != null) {
                    problems.add(label + ": unanswerable case must have expected_section: null");
                }
            }
        }
        return problems;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
