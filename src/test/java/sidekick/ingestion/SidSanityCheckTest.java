package sidekick.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidSanityCheckTest {

    @Test
    void realSidStructurePasses() {
        List<PageText> pages = List.of(new PageText(1, """
                PART I. HIGHLIGHTS/SUMMARY OF THE SCHEME
                details...
                A. HOW WILL THE SCHEME ALLOCATE ITS ASSETS?
                table...
                B. WHERE WILL THE SCHEME INVEST?
                more...
                C. LOAD STRUCTURE
                exit load...
                D. HOW WILL THE SCHEME BENCHMARK ITS PERFORMANCE?
                index...
                E. WHO MANAGES THE SCHEME?
                names...
                """));

        SidSanityCheck.Result result = SidSanityCheck.check(pages);

        assertTrue(result.plausibleSid(), result.message());
        assertTrue(result.distinctHeadings() >= 5);
    }

    @Test
    void randomPdfTextFails() {
        List<PageText> pages = List.of(new PageText(1, """
                Quarterly sales report
                Revenue was up 4% quarter over quarter.
                Regional breakdown follows on the next page.
                """));

        SidSanityCheck.Result result = SidSanityCheck.check(pages);

        assertFalse(result.plausibleSid());
        assertTrue(result.message().contains("does not look like a SID"));
    }

    @Test
    void repeatedSingleHeadingDoesNotFoolTheCheck() {
        // Five mentions of ONE mandated heading is not five sections.
        List<PageText> pages = List.of(new PageText(1,
                "C. LOAD STRUCTURE\nx\n".repeat(5)));

        SidSanityCheck.Result result = SidSanityCheck.check(pages);

        assertFalse(result.plausibleSid());
    }
}
