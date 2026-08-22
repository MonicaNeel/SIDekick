package sidekick.eval;

import java.util.List;

/** Thrown when eval-set.json parses fine but breaks the authoring rules. */
public class InvalidEvalSetException extends RuntimeException {

    private final List<String> problems;

    public InvalidEvalSetException(List<String> problems) {
        super("eval set has " + problems.size() + " problem(s):\n  - "
                + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
