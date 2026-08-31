package sidekick.ingestion;

/**
 * One page of extracted text. The page number is 1-based and physical —
 * exactly the numbering the eval set uses (ADR-004), never the number
 * printed in the document's footer.
 *
 * @param pageNumber 1-based physical PDF page
 * @param text       extracted text; empty string for image-only pages
 *                   (PDFBox cannot read pictures — see the risk-o-meter lesson)
 */
public record PageText(int pageNumber, String text) {

    public boolean isEmpty() {
        return text.isBlank();
    }
}
