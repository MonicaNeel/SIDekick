package sidekick.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalRunnerTest {

    private static EvalCase answerable(String id, String fund, List<Integer> pages, String section) {
        return new EvalCase(id, EvalCase.CaseType.ANSWERABLE, "q?", fund, "a", pages, section,
                EvalCase.Provenance.HAND, null);
    }

    private static EvalCase unanswerable(String id, String fund) {
        return new EvalCase(id, EvalCase.CaseType.UNANSWERABLE, "q?", fund, null, List.of(), null,
                EvalCase.Provenance.HAND, null);
    }

    private static RetrievedChunk chunk(int page, String section, double score) {
        return new RetrievedChunk("c" + page, page, page, section, score);
    }

    /** Scripted retrieval: per-fund fixed results, question ignored. */
    private static RetrievalPort scripted(Map<String, List<RetrievedChunk>> byFund) {
        return (question, fundId, topK) -> byFund.getOrDefault(fundId, List.of());
    }

    @Test
    void scoresPageHitsSectionHitsAndMisses() {
        RetrievalPort port = scripted(Map.of(
                "page-hit-fund", List.of(chunk(9, "Wrong Section", 0.9), chunk(24, "Also Wrong", 0.8)),
                "section-hit-fund", List.of(chunk(99, "C. LOAD STRUCTURE", 0.7)),
                "miss-fund", List.of(chunk(1, "PREAMBLE", 0.4))));

        EvalReport report = new EvalRunner(port, new EvalConfig(5, null, "test")).run(List.of(
                answerable("by-page", "page-hit-fund", List.of(24), "Load Structure"),
                answerable("by-section", "section-hit-fund", List.of(24), "Load Structure"),
                answerable("miss", "miss-fund", List.of(24), "Load Structure"),
                unanswerable("trap", "miss-fund")));

        Map<String, CaseResult> byId = Map.of(
                report.perCase().get(0).caseId(), report.perCase().get(0),
                report.perCase().get(1).caseId(), report.perCase().get(1),
                report.perCase().get(2).caseId(), report.perCase().get(2),
                report.perCase().get(3).caseId(), report.perCase().get(3));

        assertTrue(byId.get("by-page").retrievalHit());
        assertEquals(2, byId.get("by-page").hitRank(), "page 24 chunk is ranked second");
        assertTrue(byId.get("by-section").retrievalHit(), "normalized section match must count");
        assertFalse(byId.get("miss").retrievalHit());
        assertNull(byId.get("miss").hitRank());

        // Unanswerable cases don't enter the hit rate but keep their top score.
        assertFalse(byId.get("trap").retrievalHit());
        assertEquals(0.4, byId.get("trap").topScore(), 1e-9);

        assertEquals(2.0 / 3.0, report.retrievalHitRate(), 1e-9,
                "hit rate is over answerable cases only");
        assertNull(report.citationValidityRate(), "no generation metrics in retrieval-only mode");
    }

    @Test
    void chunkSpanningPagesHitsWhenExpectedPageFallsInsideItsRange() {
        // The page-108 lesson: the chunk STARTS on 108 but contains the
        // page-109/110 facts — that must count as a hit.
        RetrievalPort port = scripted(Map.of("f",
                List.of(new RetrievedChunk("c", 108, 110, "Ongoing Offer Details", 0.65))));

        EvalReport report = new EvalRunner(port, new EvalConfig(5, null, "test"))
                .run(List.of(answerable("span", "f", List.of(109, 110), "Suspension of Sale")));

        assertTrue(report.perCase().get(0).retrievalHit());
        assertEquals(1, report.perCase().get(0).hitRank());
    }

    @Test
    void sectionMatchingIgnoresCasePunctuationAndPrefixes() {
        assertTrue(EvalRunner.sectionsMatch("C. LOAD STRUCTURE", "Load Structure"));
        assertTrue(EvalRunner.sectionsMatch("ANNUAL SCHEME RECURRING EXPENSES",
                "Annual Scheme Recurring Expenses"));
        assertFalse(EvalRunner.sectionsMatch("PREAMBLE", "Load Structure"));
        assertFalse(EvalRunner.sectionsMatch("LOAD STRUCTURE", null));
    }
}
