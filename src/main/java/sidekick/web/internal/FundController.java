package sidekick.web.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sidekick.ingestion.DocumentFinder;
import sidekick.ingestion.DocumentInfo;

import java.util.List;

/**
 * GET /api/funds — the catalog list (PLAN §5 API). Backed by ingested
 * documents until the catalog module arrives with the 20-fund scale-up;
 * the endpoint's shape is the contract, the source behind it can change.
 */
@RestController
class FundController {

    private final DocumentFinder documents;

    FundController(DocumentFinder documents) {
        this.documents = documents;
    }

    @GetMapping("/api/funds")
    List<DocumentInfo> funds() {
        return documents.listAll();
    }
}
