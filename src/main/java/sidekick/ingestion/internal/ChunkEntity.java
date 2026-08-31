package sidekick.ingestion.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One chunk with its embedding, as stored. The embedding is raw bytes
 * (VectorCodec's little-endian float layout) in a bytea column — Postgres is
 * the durable system of record, RAM is the search index (ADR-002).
 *
 * documentId/fundId are plain columns, not @ManyToOne — no entity
 * relationships across what will later be module boundaries.
 */
@Entity
@Table(name = "chunks")
public class ChunkEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String fundId;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private int page;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    private byte[] embedding;

    protected ChunkEntity() {
        // JPA only.
    }

    public ChunkEntity(UUID documentId, String fundId, String section, int page,
                       String text, byte[] embedding) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.fundId = fundId;
        this.section = section;
        this.page = page;
        this.text = text;
        this.embedding = embedding;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getFundId() {
        return fundId;
    }

    public String getSection() {
        return section;
    }

    public int getPage() {
        return page;
    }

    public String getText() {
        return text;
    }

    public byte[] getEmbedding() {
        return embedding;
    }
}
