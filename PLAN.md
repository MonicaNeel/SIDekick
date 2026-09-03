# SIDekick — Mutual Fund Scheme Document Q&A

A hand-built RAG system over Indian mutual fund Scheme Information Documents (SIDs).
First RAG project. The retrieval pipeline is built from scratch on purpose — understanding
context assembly and verifying every piece is the point of the project.

---

## 1. Problem Statement

Retail mutual fund investors in India are bound by the terms of Scheme Information
Documents — 80–150 page SEBI-mandated PDFs that effectively nobody reads. Practical,
recurring questions ("what's the exit load if I redeem in 8 months?", "is there a
lock-in?", "what's the expense ratio cap?", "which benchmark does this track?") have
exact answers buried in these documents.

The system ingests published SIDs, answers natural-language English questions with
mandatory citations (fund name, section, page number), and refuses to answer when
retrieval finds nothing relevant rather than improvising.

**Explicitly out of scope:** investment advice, fund recommendations, performance
prediction. The system reports what documents *say*, never what the user should *do*.

## 2. Success Criteria

- A 20–30 question eval set written **before** the first line of pipeline code.
- ≥ 90% retrieval hit rate (correct chunk appears in top-k) on the eval set.
- 100% of answers either carry valid citations or are refusals.
- Zero answers containing facts absent from the retrieved chunks (enforced by the
  citation validator, spot-checked manually).

## 3. Product Scope

### v1 (this build)
- **Curated catalog (Tier 1):** a manifest file maps 15–20 funds across 3–4 AMCs to
  direct SID PDF URLs on official AMC websites (see ADR-007). The four eval funds
  are pinned to the exact verified files and are never auto-refetched; refreshing
  one is a deliberate event that re-verifies its eval cases. Scale-up to 20 funds
  happens only after one document is ingested end-to-end and understood. Startup /
  refresh command downloads and ingests anything missing. Pick funds by
  AUM/popularity (large HDFC / ICICI / SBI / Nippon schemes people actually hold),
  not by download convenience. The UI must disclose each document's source and
  date — and that
  time-sensitive facts (taxation especially, which changes with every annual
  budget) reflect the document as filed, not current law; users must verify
  current rules independently. Answers state what the document says, dated as the
  document is; the system never fetches or overlays current law (that would break
  the grounded-citation model).
- **Primary flow:** pick a fund from the catalog → ask a question in English →
  grounded, cited answer or an explicit refusal.
- **Upload as escape hatch:** user can upload a SID PDF for funds outside the catalog.
  Same async ingestion pipeline as the manifest path. Includes a cheap "is this
  actually a SID?" sanity check (look for SEBI-mandated section headings) so random
  PDFs fail gracefully.
- **Refusal behavior:** refusal-plus-pointer — "not found in this document; the nearest
  relevant section is X on page Y."
- **Debug panel in UI:** collapsible view of retrieved chunks with similarity scores.
  Non-negotiable; it is the main instrument for understanding retrieval failures.
- **Tracing & observability** (added to v1 scope 2026-09-03; built late in v1,
  after generation works): a hand-built per-question `AskTrace` — plain Java,
  zero Spring in cores per the hard rules — carrying a trace id, per-stage
  timings (embed, search, gate, LLM call, validation), retrieval scores and
  chunk ids, gate decisions, model name and token usage, and the final outcome.
  Emitted as one structured JSON log line per question, and returned with the
  answer so the debug panel can show it. Spring Boot Actuator at the edges for
  health/metrics. No tracing frameworks in the cores; OpenTelemetry export is
  post-v1 if ever.

### Deferred (do not build in v1)
- **v1.5:** cross-document comparison ("which of these funds has no exit load?") —
  design for it now by putting `fund_id` on every chunk and making retrieval
  fund-filterable; implement later as a loop over per-fund retrieval.
- **v2:** native-language queries (e.g., Kannada) over English SIDs — requires swapping
  to a multilingual embedding model (e.g., multilingual-e5-small) and re-embedding
  (~coffee-break cost at this corpus size).
- **v2:** Tier-2 fetch — for funds outside the catalog, search for the SID, show the
  candidate PDF link, ingest only after user confirmation. Never autonomous scraping.
- **Later:** Angular/React SPA (the JSON API is already the contract), Flyway
  migrations (retrofit when the schema stabilizes), CI (GitHub Actions), document
  versioning beyond replace-on-refetch.

## 4. Tech Stack (locked)

| Concern | Choice |
|---|---|
| Language / runtime | Java 25 |
| Framework | Spring Boot 4.0.x + Spring Modulith 2.x |
| Build | Maven |
| Persistence | Postgres (Docker) via Hibernate ORM 7 / JPA |
| Schema management | `ddl-auto` (update in dev, create-drop in tests); Flyway later |
| PDF extraction | Apache PDFBox |
| Embeddings | bge-small-en-v1.5 (384-dim), local, via raw ONNX Runtime Java API + HuggingFace tokenizers Java bindings |
| Vector search | Hand-rolled cosine similarity over in-memory `float[][]`; Postgres stores vectors as `bytea` (system of record), RAM is the index. No vector DB, no pgvector. |
| Generation | OpenRouter free-tier model via `java.net.http.HttpClient`; model name is a config property; final pick decided by benchmarking on the eval set |
| Web / UI | REST API (`/api/**`) as first-class citizen; Thymeleaf shell + vanilla `fetch()` as a thin consumer; Jackson for JSON |
| Testing | JUnit 5, Testcontainers (real Postgres), `@ApplicationModuleTest` per module, Modulith `verify()` test |
| Dev environment | Postgres via Docker; app via `mvn spring-boot:run` |
| Repo | GitHub; CI deferred |

### Hard rules
- **No RAG/orchestration frameworks.** No LangChain, no LangGraph, no Spring AI, no
  LlamaIndex — not even "just for embeddings." Chunking, embedding invocation, vector
  search, prompt assembly, and citation validation are hand-written.
- Spring's job is web + persistence + module boundaries. The `retrieval` and
  `answering` cores must have **zero Spring imports** (plain Java, constructor-injected
  by Spring config classes at the edges).
- LLM and embedding calls go through small hand-written interfaces so providers/models
  swap via config.

## 5. Architecture — Spring Modulith Modules

Top-level packages under the application class; each is a Modulith module. Root package
of a module = its public API; `internal` subpackages are invisible to other modules.
The `ApplicationModules.verify()` test enforces this in the build.

- **`catalog`** — funds, the SID manifest, download/refresh of catalog PDFs.
- **`ingestion`** — PDF → text (PDFBox) → section-aware chunking → embedding → persist.
  Async jobs with pollable status. Publishes `DocumentIngested(fundId, documentId)`.
- **`retrieval`** — pure hand-built core: in-memory vector index, cosine search with
  optional fund filter. Listens for `DocumentIngested` via `@ApplicationModuleListener`
  and atomically reloads the matrix (Modulith's event publication registry guarantees
  replay after a crash, so the index cannot silently drift from Postgres).
- **`answering`** — prompt assembly, OpenRouter client, two-stage confidence gate,
  citation validator.
- **`web`** — REST controllers + Thymeleaf.

Cross-module references use plain IDs (`fundId`), never `@ManyToOne` to another
module's entity.

### Entities
`Fund`, `Document`, `Chunk` (`fund_id`, `document_id`, `section`, `page`, `text`,
`embedding bytea`), `IngestionJob`.

### API endpoints
- `GET  /api/funds` — catalog list
- `POST /api/funds/{id}/ask` — question in, answer + citations + retrieved chunks out
- `POST /api/documents` — upload (async; returns job id)
- `GET  /api/jobs/{id}` — ingestion job status

## 6. RAG Pipeline Details

### Ingestion
1. PDFBox extracts text page by page (keep page numbers).
2. Section-aware chunker splits on SEBI-mandated headings (regex over known heading
   set); fallback to ~500-token windows with overlap where headings are mangled.
   Expect the second AMC's formatting to break the first chunker — that is why the
   corpus spans 3–4 AMCs.
3. SID sanity check (mandated headings present) before accepting an upload.
4. Embed in batches; persist chunks + vectors; publish `DocumentIngested`.

### Embedding (hand-built, the transparency we paid for)
Tokenize (WordPiece, `[CLS]`/`[SEP]`, attention mask) → ONNX session run →
**pool per the model's official recipe → L2-normalize**. For bge-v1.5 models the
official recipe is **CLS pooling** (first token's vector); mean-pooling with the
attention mask is the recipe for the MiniLM/e5 families. Pooling is a config
toggle (CLS | MEAN) so a model swap stays config-only; the calibration test
validates the active choice empirically.
Wrong pooling/normalization produces vectors that look fine but rank garbage.
**Calibration test required before trusting anything:** pin similarity expectations in
a unit test, e.g. "the exit load is 1%" ≈ "redemption charge is one percent" (high) vs
"the fund's benchmark is Nifty 50" (low). This test is also the canary for any future
model swap.

### Query
Embed question → cosine over in-memory matrix (optional fund filter) → top-k chunks →
prompt with cite-or-suppress rules → generation → validation → answer + citations +
raw chunks (for the debug panel). Retrieval specifics are config, tuned on the eval
(as of 2026-08-31: dual-query RRF fusion — plain + bge-instruction-prefixed — with
a max-2-chunks-per-section diversity cap and k=6; see application.yml and the
eval-run reports).

### Two-stage confidence gate
1. **Pre-generation:** top retrieval score below threshold (tuned on the eval set, not
   guessed) → decline before spending an LLM call, with pointer to nearest section.
2. **Post-generation:** validator (hand-written, ~80 lines) checks: every cited chunk
   ID is in the retrieved set; every answer cites ≥ 1 chunk; quoted excerpts appear
   verbatim in the cited chunk. Validation failure → return the refusal response.
   This matters double with free-tier models, which follow citation instructions less
   reliably.

## 7. Eval

- 30 pairs total: 10 hand-written by actually reading 2–3 SIDs (calibrates the
  builder), 20 generated by Claude from the PDFs and manually verified against source.
  ~5 of the 30 are deliberately **unanswerable** (fact absent from that SID) so
  refusal correctness has something to measure.
- Stored as JSON in the repo (`eval/eval-set.json`): `{id, type, question,
  expected_answer, fund_id, source_pages[], expected_section, provenance, notes}`.
  `id` is stable (for diffing runs), `type` is answerable|unanswerable,
  `provenance` is hand|generated-verified. `fund_id` is a human-assigned slug the
  catalog manifest will adopt as its key. Pages are 1-based **PDF** pages (as
  PDFBox/viewers count), never the printed footer page numbers.
- Eval runner class reports: retrieval hit rate (expected page/section in top-k),
  citation validity rate, refusal correctness. Takes `--model` flag to benchmark
  OpenRouter free models (DeepSeek / Llama / Qwen classes) on citation discipline.
- Free-tier rate limits: runner needs retry-with-backoff; it cannot fire 30 questions
  in 10 seconds.

## 8. Build Order (do not reorder; UI last)

1. **Eval set** — download 3 SIDs from different AMCs (e.g., HDFC Flexi Cap, ICICI
   Bluechip, SBI Small Cap), read exit-load / expense-ratio / lock-in sections, write
   the first 10 question–answer–page triples. Doubles as chunker reconnaissance.
2. **Ingestion** — CLI runner ingesting the 15–20 seed SIDs from the manifest.
3. **Retrieval + eval runner** — iterate chunking/retrieval until hit rate ≥ 90%.
   *This is the heart of the project.*
4. **Generation** — OpenRouter client, confidence gate, citation validator; benchmark
   models via the eval runner.
5. **Tracing & observability** — hand-built `AskTrace` through the ask pipeline
   (see §3), structured JSON logging, Actuator at the edges. Before the UI, so
   the debug panel has trace data to show on day one.
6. **Web UI + upload** — API endpoints, Thymeleaf shell, debug panel, async upload.

## 9. Known Risks / Notes

- Spring Boot 4 pulls Hibernate ORM 7 (stricter Jakarta Persistence 3.2 semantics);
  some Boot-3-era tutorial artifact names differ. Greenfield, so low impact.
- Free OpenRouter routes may train on prompts — acceptable, SIDs are public.
- bge-small is English-only; multilingual v2 = model swap + re-embed (~5k chunks).
- SID revisions: replace-on-refetch for v1; reload the in-memory index atomically
  after any ingestion so it never points at deleted chunk rows.
