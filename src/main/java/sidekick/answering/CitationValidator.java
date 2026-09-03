package sidekick.answering;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gate 2 (PLAN §6): the hand-written check that makes "grounded" a property
 * we enforce, not a property we hope for. Free-tier models follow citation
 * instructions unreliably; anything that fails here becomes a refusal.
 *
 * Checks, in order:
 * 1. A reply containing the refusal token is a (valid) refusal.
 * 2. An answer must cite at least one excerpt: [n].
 * 3. Every cited number must be one we actually provided.
 * 4. Every double-quoted span (15+ chars) must appear VERBATIM in one of the
 *    cited excerpts — compared whitespace-normalized, because PDF extraction
 *    and model output break lines differently.
 */
public final class CitationValidator {

    public static final String REFUSAL_TOKEN = "NOT_IN_DOCUMENT";

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");
    private static final Pattern QUOTED_SPAN = Pattern.compile("\"([^\"]{15,})\"");

    /**
     * @param refusal   the model declined — a valid outcome, not a failure
     * @param valid     answer passed all checks (always true for refusals)
     * @param citations distinct cited excerpt numbers, in order of appearance
     * @param problems  why validation failed; empty when valid
     */
    public record Result(boolean refusal, boolean valid, List<Integer> citations, List<String> problems) {
    }

    public Result validate(String modelOutput, List<String> providedChunkTexts) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return new Result(false, false, List.of(), List.of("model output is empty"));
        }
        if (modelOutput.contains(REFUSAL_TOKEN)) {
            return new Result(true, true, List.of(), List.of());
        }

        List<String> problems = new ArrayList<>();
        List<Integer> citations = parseCitations(modelOutput);
        if (citations.isEmpty()) {
            problems.add("answer cites no excerpts");
        }
        for (int cited : citations) {
            if (cited < 1 || cited > providedChunkTexts.size()) {
                problems.add("cites excerpt [" + cited + "] which was not provided");
            }
        }

        List<String> citedTexts = citations.stream()
                .filter(n -> n >= 1 && n <= providedChunkTexts.size())
                .map(n -> normalize(providedChunkTexts.get(n - 1)))
                .toList();
        Matcher quotes = QUOTED_SPAN.matcher(modelOutput);
        while (quotes.find()) {
            String quoted = normalize(quotes.group(1));
            boolean foundVerbatim = citedTexts.stream().anyMatch(text -> text.contains(quoted));
            if (!foundVerbatim) {
                problems.add("quoted text not found verbatim in any cited excerpt: \""
                        + quotes.group(1) + "\"");
            }
        }

        return new Result(false, problems.isEmpty(), citations, List.copyOf(problems));
    }

    private static List<Integer> parseCitations(String output) {
        List<Integer> citations = new ArrayList<>();
        Matcher matcher = CITATION.matcher(output);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (!citations.contains(number)) {
                citations.add(number);
            }
        }
        return citations;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
