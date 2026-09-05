package sidekick.ingestion;

/** Lifecycle of an async ingestion job (PLAN §3: upload is async, pollable). */
public enum JobStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}
