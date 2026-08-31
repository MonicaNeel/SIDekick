package sidekick.ingestion;

import sidekick.ingestion.SectionHeadingCatalog.HeadingMatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an extracted document into chunks along SEBI-mandated section
 * headings; sections too large for one chunk fall back to overlapping
 * word windows (PLAN §6).
 *
 * Page attribution: a chunk's page is the page its first word sits on.
 * Chunks may run onto later pages — the eval accommodates that by listing
 * every acceptable page per case.
 */
public final class SectionAwareChunker {

    /**
     * @param maxWordsPerChunk section size above which windowing kicks in;
     *                         ~350 words ≈ the plan's ~500 tokens
     * @param overlapWords     words shared between consecutive windows so a
     *                         fact straddling a window edge survives in one
     *                         piece somewhere
     */
    public record Config(int maxWordsPerChunk, int overlapWords) {
        public static Config defaults() {
            return new Config(350, 50);
        }
    }

    private final Config config;
    private final SectionHeadingCatalog catalog;

    public SectionAwareChunker(Config config, SectionHeadingCatalog catalog) {
        this.config = config;
        this.catalog = catalog;
    }

    public static SectionAwareChunker withDefaults() {
        return new SectionAwareChunker(Config.defaults(), SectionHeadingCatalog.standard());
    }

    public List<Chunk> chunk(String fundId, List<PageText> pages) {
        PagedText doc = PagedText.of(pages);
        List<HeadingMatch> headings = catalog.findAll(doc.text());

        List<Chunk> chunks = new ArrayList<>();
        // Implicit section for everything before the first recognized heading.
        int firstHeadingStart = headings.isEmpty() ? doc.text().length() : headings.get(0).start();
        emitSection(chunks, fundId, doc, "PREAMBLE", 0, firstHeadingStart);

        for (int i = 0; i < headings.size(); i++) {
            HeadingMatch heading = headings.get(i);
            int sectionEnd = i + 1 < headings.size() ? headings.get(i + 1).start() : doc.text().length();
            emitSection(chunks, fundId, doc, heading.section(), heading.start(), sectionEnd);
        }
        return chunks;
    }

    private void emitSection(List<Chunk> chunks, String fundId, PagedText doc,
                             String section, int from, int to) {
        List<int[]> words = wordSpans(doc.text(), from, to);
        if (words.isEmpty()) {
            return;
        }
        int step = config.maxWordsPerChunk() - config.overlapWords();
        for (int w = 0; w < words.size(); w += step) {
            int endWord = Math.min(w + config.maxWordsPerChunk(), words.size());
            int textStart = words.get(w)[0];
            int textEnd = words.get(endWord - 1)[1];
            chunks.add(new Chunk(fundId, section, doc.pageAt(textStart),
                    doc.text().substring(textStart, textEnd)));
            if (endWord == words.size()) {
                break;
            }
        }
    }

    /** Start/end offsets of every whitespace-delimited word in [from, to). */
    private static List<int[]> wordSpans(String text, int from, int to) {
        List<int[]> spans = new ArrayList<>();
        int i = from;
        while (i < to) {
            while (i < to && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= to) {
                break;
            }
            int start = i;
            while (i < to && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            spans.add(new int[]{start, i});
        }
        return spans;
    }

    /** The document as one string that still remembers where each page begins. */
    private static final class PagedText {
        private final String text;
        private final int[] pageStartOffsets; // index i = offset where page i+1 begins

        private PagedText(String text, int[] pageStartOffsets) {
            this.text = text;
            this.pageStartOffsets = pageStartOffsets;
        }

        static PagedText of(List<PageText> pages) {
            StringBuilder sb = new StringBuilder();
            int[] starts = new int[pages.size()];
            for (int i = 0; i < pages.size(); i++) {
                starts[i] = sb.length();
                sb.append(pages.get(i).text());
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
            }
            return new PagedText(sb.toString(), starts);
        }

        String text() {
            return text;
        }

        int pageAt(int offset) {
            int page = 1;
            for (int i = 0; i < pageStartOffsets.length; i++) {
                if (pageStartOffsets[i] <= offset) {
                    page = i + 1;
                } else {
                    break;
                }
            }
            return page;
        }
    }
}
