package sidekick.ingestion.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import sidekick.ingestion.JobStatus;

import java.time.Instant;
import java.util.UUID;

/** One async ingestion job — the pollable record behind GET /api/jobs/{id}. */
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJobEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String fundId;

    @Column
    private String displayName;

    @Column(nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(columnDefinition = "text")
    private String error;

    @Column
    private Integer pages;

    @Column
    private Integer chunkCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant finishedAt;

    protected IngestionJobEntity() {
        // JPA only.
    }

    public IngestionJobEntity(String fundId, String displayName, String fileName) {
        this.id = UUID.randomUUID();
        this.fundId = fundId;
        this.displayName = displayName;
        this.fileName = fileName;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markRunning() {
        this.status = JobStatus.RUNNING;
    }

    public void markDone(int pages, int chunkCount) {
        this.status = JobStatus.DONE;
        this.pages = pages;
        this.chunkCount = chunkCount;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = JobStatus.FAILED;
        this.error = error;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFundId() {
        return fundId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileName() {
        return fileName;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Integer getPages() {
        return pages;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
