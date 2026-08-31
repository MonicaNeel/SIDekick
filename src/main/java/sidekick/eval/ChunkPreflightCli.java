package sidekick.eval;

import sidekick.ingestion.Chunk;
import sidekick.ingestion.PageText;
import sidekick.ingestion.PdfTextExtractor;
import sidekick.ingestion.SectionAwareChunker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Milestone B inspection tool: chunk one document and answer two questions
 * before any retrieval exists —
 *   1. What did the chunker produce? (sections found, chunk sizes)
 *   2. For each answerable eval case of this fund, does at least one chunk
 *      START on an expected page? (the same criterion eval hit-scoring uses)
 *
 * Usage: ChunkPreflightCli &lt;input.pdf&gt; &lt;fundId&gt; [eval-set.json]
 *
 * Lives in the eval package on purpose: the preflight is an eval instrument
 * that consumes ingestion's public API (Chunk, chunker) — the same dependency
 * direction the eval runner will use in build-order step 3.
 */
public final class ChunkPreflightCli {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: ChunkPreflightCli <input.pdf> <fundId> [eval-set.json]");
            System.exit(1);
        }
        Path pdf = Path.of(args[0]);
        String fundId = args[1];
        Path evalJson = args.length >= 3 ? Path.of(args[2]) : Path.of("eval", "eval-set.json");

        List<PageText> pages = new PdfTextExtractor().extract(pdf);
        List<Chunk> chunks = SectionAwareChunker.withDefaults().chunk(fundId, pages);

        printChunkReport(chunks);
        printPreflight(chunks, new EvalSetLoader().load(evalJson), fundId);
    }

    private static void printChunkReport(List<Chunk> chunks) {
        List<Integer> sizes = chunks.stream().map(c -> c.text().split("\\s+").length).sorted().toList();
        System.out.printf("Chunks: %d | words per chunk min/median/max: %d / %d / %d%n",
                chunks.size(), sizes.get(0), sizes.get(sizes.size() / 2), sizes.get(sizes.size() - 1));

        Map<String, List<Chunk>> bySection = new LinkedHashMap<>();
        for (Chunk chunk : chunks) {
            bySection.computeIfAbsent(chunk.section(), k -> new ArrayList<>()).add(chunk);
        }
        System.out.println("Sections in document order (first page, chunk count):");
        bySection.forEach((section, sectionChunks) -> System.out.printf("  p.%-3d %-55s %d chunk(s)%n",
                sectionChunks.get(0).page(), section, sectionChunks.size()));
    }

    private static void printPreflight(List<Chunk> chunks, EvalSet evalSet, String fundId) {
        System.out.printf("%nEval preflight for fund '%s':%n", fundId);
        int passed = 0;
        int total = 0;
        for (EvalCase evalCase : evalSet.cases()) {
            if (!fundId.equals(evalCase.fundId()) || evalCase.type() != EvalCase.CaseType.ANSWERABLE) {
                continue;
            }
            total++;
            boolean pageHit = chunks.stream().anyMatch(c -> evalCase.sourcePages().stream()
                    .anyMatch(p -> p >= c.page() && p <= c.endPage()));
            boolean sectionExists = chunks.stream()
                    .anyMatch(c -> sectionsMatch(c.section(), evalCase.expectedSection()));
            if (pageHit) {
                passed++;
            }
            System.out.printf("  %-6s %-40s pages %s%s%n",
                    pageHit ? "PASS" : "MISS",
                    evalCase.id(),
                    evalCase.sourcePages(),
                    sectionExists ? "" : "  [no chunk labeled '" + evalCase.expectedSection() + "']");
        }
        System.out.printf("Page-hit: %d/%d answerable cases have a chunk starting on an expected page.%n",
                passed, total);
        System.out.println("(A MISS here predicts an eval retrieval miss no matter how good the embeddings are.)");
    }

    /** Loose comparison: heading prefixes/case/punctuation don't matter. */
    private static boolean sectionsMatch(String chunkSection, String expectedSection) {
        if (expectedSection == null) {
            return false;
        }
        String a = normalize(chunkSection);
        String b = normalize(expectedSection);
        return a.contains(b) || b.contains(a);
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
