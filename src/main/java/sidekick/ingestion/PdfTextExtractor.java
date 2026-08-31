package sidekick.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF in, one text string per page out.
 * <p>
 * Extraction happens page by page (stripper start page == end page) rather
 * than whole-document, because page boundaries are load-bearing here: every
 * citation and every eval hit is scored by page number. Losing "which page
 * did this sentence come from" would break the whole downstream pipeline.
 * <p>
 * Every physical page appears in the result, including pages that yield no
 * text (scanned images, pure graphics) — chunking and citations must see the
 * same page numbering as a human's PDF viewer.
 */
public final class PdfTextExtractor {

    /**
     * @throws IOException if the file is missing, unreadable, or not a PDF
     */
    public List<PageText> extract(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            int pageCount = document.getNumberOfPages();
            List<PageText> pages = new ArrayList<>(pageCount);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String text = stripper.getText(document);
                pages.add(new PageText(pageNo, text == null ? "" : text));
            }
            return pages;
        }
    }
}
