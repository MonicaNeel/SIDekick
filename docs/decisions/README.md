# Architecture Decision Records

One file per decision, numbered, append-only. When a decision changes, we don't
edit the old file — we write a new ADR that supersedes it and update the old
one's Status line. See any ADR for the template.

## Index

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](ADR-001-no-rag-frameworks.md) | Hand-build the RAG pipeline; no frameworks | Accepted |
| [ADR-002](ADR-002-no-vector-db.md) | No vector DB: Postgres `bytea` + in-memory `float[][]` | Accepted |
| [ADR-003](ADR-003-zero-spring-cores-behind-ports.md) | Zero-Spring cores behind hand-written ports | Accepted |
| [ADR-004](ADR-004-eval-set-before-pipeline.md) | Eval set before pipeline code, pinned to exact PDFs | Accepted |
| [ADR-005](ADR-005-refusal-plus-pointer.md) | Refuse-with-pointer instead of best-effort answers | Accepted |
| [ADR-006](ADR-006-sebi-as-document-source.md) | SEBI filings page as the document source | Superseded by ADR-007 |
| [ADR-007](ADR-007-amc-sites-as-source-eval-funds-pinned.md) | AMC websites as source; eval funds pinned to verified copies | Accepted |
| [ADR-008](ADR-008-retrieval-config-by-eval-sweep.md) | Retrieval config (RRF fusion, section cap, k=6) chosen by eval sweep | Accepted |

## Pending decisions (no ADR yet)

- **Embedding model confirmation** — after the calibration test exists (PLAN §6).
- **Retrieval score threshold methodology** — tuned on the eval set, step 3.
- **Generation model pick** — after benchmarking free OpenRouter models, step 4.
