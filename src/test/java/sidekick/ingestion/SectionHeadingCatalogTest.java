package sidekick.ingestion;

import org.junit.jupiter.api.Test;
import sidekick.ingestion.SectionHeadingCatalog.HeadingMatch;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each test pins a rule the catalog exists to enforce — every one traceable
 * to a quirk found in a real SID during step 1.
 */
class SectionHeadingCatalogTest {

    private final SectionHeadingCatalog catalog = SectionHeadingCatalog.standard();

    @Test
    void findsHeadingWrappedAcrossALineBreak() {
        // The page-5 lesson: PDF extraction splits phrases mid-heading.
        String text = "some earlier text\nA. HOW WILL THE\nSCHEME ALLOCATE ITS ASSETS?\nEquity 65 100\n";

        List<HeadingMatch> matches = catalog.findAll(text);

        assertEquals(1, matches.size());
        assertEquals("HOW WILL THE SCHEME ALLOCATE ITS ASSETS", matches.get(0).section());
    }

    @Test
    void acceptsDifferentListPrefixesAsTheSameSection() {
        // AMCs letter the same mandated heading differently.
        String hdfc = "A. HOW WILL THE SCHEME ALLOCATE ITS ASSETS?\n...\n";
        String icici = "C. HOW WILL THE SCHEME ALLOCATE ITS ASSETS?\n...\n";

        assertEquals(catalog.findAll(hdfc).get(0).section(), catalog.findAll(icici).get(0).section());
    }

    @Test
    void skipsTableOfContentsEntries() {
        String toc = """
                TABLE OF CONTENTS
                HOW WILL THE SCHEME ALLOCATE ITS ASSETS? ............ 10
                LOAD STRUCTURE .................. 24
                """;

        assertTrue(catalog.findAll(toc).isEmpty(),
                "dot-leader lines are TOC rows, not section starts");
    }

    @Test
    void ignoresMidSentenceMentions() {
        String prose = "As stated above, the load structure of the scheme is subject to change.\n";

        assertTrue(catalog.findAll(prose).isEmpty(),
                "a heading phrase inside a sentence is not a section boundary");
    }

    @Test
    void returnsMatchesInDocumentOrder() {
        String text = """
                PART I. HIGHLIGHTS/SUMMARY OF THE SCHEME
                stuff
                B. WHERE WILL THE SCHEME INVEST?
                more stuff
                C. LOAD STRUCTURE
                even more
                """;

        List<HeadingMatch> matches = catalog.findAll(text);

        assertEquals(List.of("HIGHLIGHTS/SUMMARY OF THE SCHEME", "WHERE WILL THE SCHEME INVEST", "LOAD STRUCTURE"),
                matches.stream().map(HeadingMatch::section).toList());
    }
}
