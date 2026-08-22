package sidekick.eval;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One question in the eval set — see eval/README.md for the field guide.
 *
 * A record is just a small immutable data-carrier class: Java writes the
 * constructor, getters, equals/hashCode for us.
 */
public record EvalCase(
        @JsonProperty("id") String id,
        @JsonProperty("type") CaseType type,
        @JsonProperty("question") String question,
        @JsonProperty("fund_id") String fundId,
        @JsonProperty("expected_answer") String expectedAnswer,
        @JsonProperty("source_pages") List<Integer> sourcePages,
        @JsonProperty("expected_section") String expectedSection,
        @JsonProperty("provenance") Provenance provenance,
        @JsonProperty("notes") String notes
) {

    public enum CaseType {
        @JsonProperty("answerable") ANSWERABLE,
        @JsonProperty("unanswerable") UNANSWERABLE
    }

    public enum Provenance {
        @JsonProperty("hand") HAND,
        @JsonProperty("generated-verified") GENERATED_VERIFIED
    }
}
