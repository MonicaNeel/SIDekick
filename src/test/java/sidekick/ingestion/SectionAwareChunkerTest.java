package sidekick.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionAwareChunkerTest {

    private final SectionAwareChunker chunker = SectionAwareChunker.withDefaults();

    @Test
    void textBeforeFirstHeadingBecomesPreambleChunk() {
        List<PageText> pages = List.of(
                new PageText(1, "Cover page text about the fund\n"),
                new PageText(2, "B. WHERE WILL THE SCHEME INVEST?\nIn equities mostly.\n"));

        List<Chunk> chunks = chunker.chunk("test-fund", pages);

        assertEquals("PREAMBLE", chunks.get(0).section());
        assertEquals(1, chunks.get(0).page());
        assertEquals("WHERE WILL THE SCHEME INVEST", chunks.get(1).section());
        assertEquals(2, chunks.get(1).page());
    }

    @Test
    void chunkPageIsWhereTheChunkStartsEvenWhenSectionSpansPages() {
        List<PageText> pages = List.of(
                new PageText(1, "C. LOAD STRUCTURE\nExit load details begin here\n"),
                new PageText(2, "and continue on the next page.\n"));

        List<Chunk> chunks = chunker.chunk("test-fund", pages);

        assertEquals(1, chunks.size(), "small two-page section stays one chunk");
        assertEquals(1, chunks.get(0).page());
        assertEquals(2, chunks.get(0).endPage(),
                "a chunk spanning pages must know where it ends — eval hits depend on the range");
        assertTrue(chunks.get(0).text().contains("continue on the next page"));
    }

    @Test
    void oversizedSectionFallsBackToOverlappingWindows() {
        // 800 distinct numbered words -> windows of 350 stepping by 300:
        // words 0-349, 300-649, 600-799.
        String body = IntStream.range(0, 800).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
        List<PageText> pages = List.of(new PageText(1, "A. WHERE WILL THE SCHEME INVEST?\n" + body + "\n"));

        List<Chunk> chunks = chunker.chunk("test-fund", pages);

        // The heading words themselves join the section's word stream, so
        // expect 3 windows either way.
        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).text().contains("w0"));
        assertTrue(chunks.get(1).text().contains("w340"),
                "second window must overlap the first window's tail");
        assertTrue(chunks.get(0).text().contains("w340"),
                "overlap means the same words appear in both windows");
        assertTrue(chunks.get(2).text().contains("w799"), "last words must not be dropped");
        chunks.forEach(c -> assertEquals("WHERE WILL THE SCHEME INVEST", c.section()));
    }

    @Test
    void emptyPagesProduceNoChunks() {
        List<PageText> pages = List.of(
                new PageText(1, ""),
                new PageText(2, "D. TAXATION\nGains are taxed as per prevailing law.\n"));

        List<Chunk> chunks = chunker.chunk("test-fund", pages);

        assertEquals(1, chunks.size(), "no phantom PREAMBLE chunk from an empty page");
        assertEquals("TAXATION", chunks.get(0).section());
        assertEquals(2, chunks.get(0).page());
    }

    @Test
    void everyChunkCarriesTheFundId() {
        List<PageText> pages = List.of(new PageText(1, "C. LOAD STRUCTURE\nExit load is 1%.\n"));

        List<Chunk> chunks = chunker.chunk("hdfc-flexi-cap", pages);

        chunks.forEach(c -> assertEquals("hdfc-flexi-cap", c.fundId()));
    }
}
