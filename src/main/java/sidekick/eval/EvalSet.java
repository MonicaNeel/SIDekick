package sidekick.eval;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The whole eval-set.json file: a version number plus the list of cases. */
public record EvalSet(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("cases") List<EvalCase> cases
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
