package sidekick.ingestion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sidekick.ingestion.internal.DocumentRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Public read API over ingested documents (used by the web module's funds
 * list). Entities stay internal; callers get DocumentInfo views.
 */
@Service
public class DocumentFinder {

    private final DocumentRepository documents;

    public DocumentFinder(DocumentRepository documents) {
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public List<DocumentInfo> listAll() {
        return documents.findAll().stream()
                .map(d -> new DocumentInfo(d.getFundId(),
                        d.getDisplayName() != null ? d.getDisplayName() : d.getFundId(),
                        d.getFileName(), d.getPageCount(), d.getIngestedAt()))
                .sorted(Comparator.comparing(DocumentInfo::displayName))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean fundExists(String fundId) {
        return !documents.findByFundId(fundId).isEmpty();
    }

    /** Human-readable name for prompts and UI; falls back to the slug. */
    @Transactional(readOnly = true)
    public String displayName(String fundId) {
        return documents.findByFundId(fundId).stream()
                .map(d -> d.getDisplayName())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(fundId);
    }
}
