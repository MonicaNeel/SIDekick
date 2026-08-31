package sidekick.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * <p>
 *     Milestone A inspection tool: dump exactly what PDFBox sees in a SID, page
 *     by page, so a human can read the extraction next to the real PDF.
 * </p>
 * <p>
 *     Usage: ExtractionDumpCli &lt;input.pdf&gt; [output.txt]
 *     Default output: work/extraction/&lt;input-name&gt;.pages.txt 
 * </p>
 * <p>
 *     This is a throwaway-grade tool by design — the learning instrument, not production code. 
 *     The real ingestion pipeline (milestones B/C) reuses PdfTextExtractor, not this class.
 * </p>
 */
public final class ExtractionDumpCli {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: ExtractionDumpCli <input.pdf> [output.txt]");
            System.exit(1);
        }
        Path input = Path.of(args[0]);
        Path output = args.length >= 2
                ? Path.of(args[1])
                : Path.of("work", "extraction", stripExtension(input.getFileName().toString()) + ".pages.txt");

        List<PageText> pages = new PdfTextExtractor().extract(input);

        StringBuilder dump = new StringBuilder();
        int emptyPages = 0;
        long totalChars = 0;
        for (PageText page : pages) {
            dump.append("===== PAGE ").append(page.pageNumber()).append(" =====\n");
            dump.append(page.text()).append('\n');
            totalChars += page.text().length();
            if (page.isEmpty()) {
                emptyPages++;
            }
        }
        Files.createDirectories(output.getParent());
        Files.writeString(output, dump.toString());

        System.out.printf("Extracted %d pages (%,d chars) from %s%n", pages.size(), totalChars, input);
        System.out.printf("Empty/image-only pages: %d%n", emptyPages);
        if (emptyPages > 0) {
            System.out.print("  page numbers:");
            pages.stream().filter(PageText::isEmpty)
                    .forEach(p -> System.out.print(" " + p.pageNumber()));
            System.out.println();
        }
        System.out.println("Dump written to " + output.toAbsolutePath());
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
