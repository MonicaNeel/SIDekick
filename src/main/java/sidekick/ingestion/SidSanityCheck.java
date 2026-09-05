package sidekick.ingestion;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The cheap "is this actually a SID?" gate (PLAN §3): a real SID must contain
 * a healthy number of SEBI-mandated section headings; a random PDF won't.
 * Reuses the chunker's heading catalog — one source of truth for what a SID
 * looks like. Plain Java, unit-tested, no Spring.
 */
public final class SidSanityCheck {

    /** Distinct mandated headings a plausible SID must match. */
    public static final int MIN_DISTINCT_HEADINGS = 5;

    public record Result(boolean plausibleSid, int distinctHeadings, String message) {
    }

    private SidSanityCheck() {
    }

    public static Result check(List<PageText> pages) {
        String text = pages.stream().map(PageText::text).collect(Collectors.joining("\n"));
        Set<String> distinct = SectionHeadingCatalog.standard().findAll(text).stream()
                .map(SectionHeadingCatalog.HeadingMatch::section)
                .collect(Collectors.toSet());
        if (distinct.size() >= MIN_DISTINCT_HEADINGS) {
            return new Result(true, distinct.size(),
                    "found " + distinct.size() + " SEBI-mandated section headings");
        }
        return new Result(false, distinct.size(),
                "this does not look like a SID: only " + distinct.size()
                        + " of the SEBI-mandated section headings were found (need "
                        + MIN_DISTINCT_HEADINGS + "). Is this the right PDF?");
    }
}
