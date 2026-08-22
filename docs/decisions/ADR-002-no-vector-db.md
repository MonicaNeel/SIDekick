# ADR-002: No vector DB — Postgres `bytea` as system of record, in-memory `float[][]` as index

## Status
Accepted

## Date
2026-08-20 (backfilled; decided at project inception)

## Context
The corpus is 15–20 SIDs ≈ 5–10k chunks × 384-dim vectors × 4 bytes — under
15 MB of vector data. Brute-force cosine similarity over that, in memory, takes
milliseconds. Vector databases and ANN indexes (HNSW etc.) solve a scale problem
(millions of vectors) this project does not have.

## Decision
Postgres stores each chunk's embedding as a `bytea` column (durable system of
record). At startup and after each ingestion, vectors load into a plain Java
`float[][]` in RAM; retrieval is a hand-written cosine loop over that array with
an optional fund filter. No vector DB, no pgvector.

## Alternatives Considered

### pgvector + Hibernate Vector
- Pros: real production pattern; similarity ranking inside SQL; Hibernate Vector
  maps `float[]` to a vector column cleanly.
- Cons: requires the pgvector extension (Postgres becomes a vector DB); folds the
  hand-written search — the heart of the learning — into a query we can't step
  through.
- Rejected for v1. Earmarked as a later experiment: swap it in behind
  `RetrievalPort` and verify eval numbers don't move (see ADR-003).

### Dedicated vector DB (Qdrant, Weaviate, ...)
- Rejected: an extra service and client library to solve a problem we don't have.

## Consequences
- The in-memory index must reload atomically after ingestion; Spring Modulith's
  event publication registry replays `DocumentIngested` after a crash so the
  index cannot silently drift from Postgres.
- RAM is the index: restart cost is a reload, acceptable at this size.
- If the corpus ever grows ~100×, revisit via a superseding ADR.
