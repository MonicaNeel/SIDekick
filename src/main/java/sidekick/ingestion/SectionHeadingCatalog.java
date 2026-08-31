package sidekick.ingestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The set of SEBI-mandated SID headings the chunker splits on, compiled into
 * wrap-tolerant regexes.
 *
 * Hard-won rules encoded here (each traceable to a real document quirk):
 * - Words are joined with \s+ because PDF extraction breaks headings across
 *   lines ("Minimum Application\nAmount") — a literal space would miss them.
 * - An optional list prefix ("A.", "C.", "PART I.", "1.") is accepted but not
 *   part of the phrase, because AMCs letter the same heading differently.
 * - A match must start at the beginning of a line, so a mid-sentence mention
 *   ("...the load structure is described below...") is not a boundary.
 * - A match whose line trails off into dot leaders / a page number is a table
 *   of contents entry, not a real section start, and is skipped.
 *
 * This catalog is calibrated against the HDFC/SBI/ICICI 2023-24 SID formats;
 * PLAN.md predicts the next AMC's formatting will break it. Extend the list,
 * don't fight it.
 */
public final class SectionHeadingCatalog {

    /** Where a heading was found and which canonical section it opens. */
    public record HeadingMatch(int start, int end, String section) {
    }

    private record Entry(String canonical, Pattern pattern) {
    }

    private final List<Entry> entries;

    private SectionHeadingCatalog(List<Entry> entries) {
        this.entries = entries;
    }

    public static SectionHeadingCatalog standard() {
        List<Entry> entries = new ArrayList<>();
        // canonical name, then one or more phrase variants seen in real SIDs
        add(entries, "HIGHLIGHTS/SUMMARY OF THE SCHEME", "HIGHLIGHTS/SUMMARY OF THE SCHEME");
        add(entries, "DEFINITIONS", "DEFINITIONS");
        add(entries, "RISK FACTORS", "RISK FACTORS", "SCHEME SPECIFIC RISK FACTORS");
        add(entries, "INFORMATION ABOUT THE SCHEME", "INFORMATION ABOUT THE SCHEME");
        add(entries, "TYPE OF THE SCHEME", "TYPE OF THE SCHEME");
        add(entries, "WHAT IS THE INVESTMENT OBJECTIVE OF THE SCHEME",
                "WHAT IS THE INVESTMENT OBJECTIVE OF THE SCHEME");
        add(entries, "HOW WILL THE SCHEME ALLOCATE ITS ASSETS", "HOW WILL THE SCHEME ALLOCATE ITS ASSETS");
        add(entries, "WHERE WILL THE SCHEME INVEST", "WHERE WILL THE SCHEME INVEST");
        add(entries, "WHAT ARE THE INVESTMENT STRATEGIES", "WHAT ARE THE INVESTMENT STRATEGIES");
        add(entries, "HOW WILL THE SCHEME BENCHMARK ITS PERFORMANCE",
                "HOW WILL THE SCHEME BENCHMARK ITS PERFORMANCE");
        add(entries, "WHO MANAGES THE SCHEME", "WHO MANAGES THE SCHEME", "WHO MANAGES THE SCHEMES");
        add(entries, "WHAT ARE THE INVESTMENT RESTRICTIONS", "WHAT ARE THE INVESTMENT RESTRICTIONS");
        add(entries, "HOW HAS THE SCHEME PERFORMED", "HOW HAS THE SCHEME PERFORMED");
        add(entries, "ADDITIONAL SCHEME RELATED DISCLOSURES", "ADDITIONAL SCHEME RELATED DISCLOSURES",
                "ADDITIONAL DISCLOSURES");
        add(entries, "FUNDAMENTAL ATTRIBUTES", "FUNDAMENTAL ATTRIBUTES");
        add(entries, "COMPARISON BETWEEN THE SCHEMES", "COMPARISON BETWEEN THE SCHEMES",
                "HOW IS THE SCHEME DIFFERENT FROM EXISTING SCHEMES OF THE MUTUAL FUND");
        add(entries, "UNITS AND OFFER", "UNITS AND OFFER");
        add(entries, "NEW FUND OFFER DETAILS", "NEW FUND OFFER DETAILS");
        add(entries, "ONGOING OFFER DETAILS", "ONGOING OFFER DETAILS");
        add(entries, "OTHER SCHEME SPECIFIC DISCLOSURES", "OTHER SCHEME SPECIFIC DISCLOSURES");
        add(entries, "PERIODIC DISCLOSURES", "PERIODIC DISCLOSURES");
        add(entries, "TRANSPARENCY/NAV DISCLOSURE", "TRANSPARENCY/NAV DISCLOSURE");
        add(entries, "FEES AND EXPENSES", "FEES AND EXPENSES");
        add(entries, "NEW FUND OFFER EXPENSES", "NEW FUND OFFER EXPENSES");
        add(entries, "ANNUAL SCHEME RECURRING EXPENSES", "ANNUAL SCHEME RECURRING EXPENSES");
        add(entries, "LOAD STRUCTURE", "LOAD STRUCTURE");
        add(entries, "LOCK-IN PERIOD APPLICABLE TO THE SCHEME", "LOCK-IN PERIOD APPLICABLE TO THE SCHEME");
        add(entries, "RIGHTS OF UNITHOLDERS", "RIGHTS OF UNITHOLDERS");
        add(entries, "TAXATION", "TAXATION");
        return new SectionHeadingCatalog(entries);
    }

    private static void add(List<Entry> entries, String canonical, String... variants) {
        for (String variant : variants) {
            entries.add(new Entry(canonical, toPattern(variant)));
        }
    }

    /**
     * All non-TOC heading matches, sorted by position, overlaps removed
     * (earliest match wins; on a tie the longer match wins).
     */
    public List<HeadingMatch> findAll(String text) {
        List<HeadingMatch> all = new ArrayList<>();
        for (Entry entry : entries) {
            Matcher m = entry.pattern().matcher(text);
            while (m.find()) {
                if (!looksLikeTocEntry(text, m.end())) {
                    all.add(new HeadingMatch(m.start(), m.end(), entry.canonical()));
                }
            }
        }
        all.sort(Comparator.comparingInt(HeadingMatch::start)
                .thenComparing(Comparator.comparingInt(HeadingMatch::end).reversed()));
        List<HeadingMatch> result = new ArrayList<>();
        int lastEnd = -1;
        for (HeadingMatch match : all) {
            if (match.start() >= lastEnd) {
                result.add(match);
                lastEnd = match.end();
            }
        }
        return result;
    }

    /** A heading whose line ends in dot leaders and/or a page number is a TOC row. */
    private static boolean looksLikeTocEntry(String text, int afterMatch) {
        int lineEnd = text.indexOf('\n', afterMatch);
        String restOfLine = text.substring(afterMatch, lineEnd < 0 ? text.length() : lineEnd);
        return restOfLine.matches(".*\\.{2,}.*\\d+\\s*$")   // "...... 24"
                || restOfLine.matches("[\\s.]*\\d{1,3}\\s*$"); // "  24"
    }

    private static Pattern toPattern(String phrase) {
        StringBuilder rx = new StringBuilder();
        rx.append("(?m)^[ \\t]*");
        rx.append("(?:(?:PART\\s+)?(?:[A-Z]|[IVXL]{1,4}|\\d{1,2})\\s*[.):]\\s*)?");
        String[] words = phrase.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                rx.append("\\s+");
            }
            rx.append(wordRegex(words[i]));
        }
        rx.append("\\s*[:?]?");
        return Pattern.compile(rx.toString(), Pattern.CASE_INSENSITIVE);
    }

    /** Letters/digits match literally; punctuation inside a word ("/", "-") tolerates surrounding whitespace. */
    private static String wordRegex(String word) {
        StringBuilder sb = new StringBuilder();
        for (char c : word.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append("\\s*").append(Pattern.quote(String.valueOf(c))).append("\\s*");
            }
        }
        return sb.toString();
    }
}
