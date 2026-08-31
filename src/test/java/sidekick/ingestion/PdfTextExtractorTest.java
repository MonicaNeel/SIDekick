package sidekick.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a small PDF from scratch (so the test depends on no external file)
 * and proves extraction preserves text AND page numbering — the property
 * everything downstream (chunks, citations, eval hits) relies on.
 */
class PdfTextExtractorTest {

    @Test
    void extractsTextWithCorrectPageNumbers(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("three-pages.pdf");
        writePdf(pdf,
                "The exit load is 1 percent within one year.",
                "The benchmark of the scheme is NIFTY 500 Index TRI.",
                null); // page 3 has no text at all — like an image-only riskometer page

        List<PageText> pages = new PdfTextExtractor().extract(pdf);

        assertEquals(3, pages.size(), "every physical page must appear, even empty ones");
        assertEquals(1, pages.get(0).pageNumber());
        assertEquals(2, pages.get(1).pageNumber());
        assertEquals(3, pages.get(2).pageNumber());
        assertTrue(pages.get(0).text().contains("exit load is 1 percent"));
        assertTrue(pages.get(1).text().contains("NIFTY 500 Index TRI"));
        assertTrue(pages.get(2).isEmpty(), "text-free page must extract as empty, not vanish");
    }

    /** Creates one page per entry; a null entry becomes a page with no text. */
    private static void writePdf(Path target, String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (text == null) {
                    continue;
                }
                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            document.save(target.toFile());
        }
    }
}
