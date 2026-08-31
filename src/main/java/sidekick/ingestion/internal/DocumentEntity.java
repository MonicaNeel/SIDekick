package sidekick.ingestion.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One ingested source document. Internal to the ingestion module — other
 * modules only ever see its UUID (via DocumentIngested).
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String fundId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private int pageCount;

    @Column(nullable = false)
    private Instant ingestedAt;

    protected DocumentEntity() {
        // JPA needs a no-arg constructor; nobody else should use it.
    }

    public DocumentEntity(String fundId, String fileName, int pageCount) {
        this.id = UUID.randomUUID();
        this.fundId = fundId;
        this.fileName = fileName;
        this.pageCount = pageCount;
        this.ingestedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFundId() {
        return fundId;
    }

    public String getFileName() {
        return fileName;
    }

    public int getPageCount() {
        return pageCount;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
