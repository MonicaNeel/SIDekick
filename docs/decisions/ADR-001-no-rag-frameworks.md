# ADR-001: Hand-build the RAG pipeline; no frameworks

## Status
Accepted

## Date
2026-08-20 (backfilled; decided at project inception)

## Context
This is a first RAG project undertaken explicitly to learn how retrieval-augmented
generation works: chunking, embeddings, similarity search, prompt assembly,
citation validation. Frameworks (LangChain, LlamaIndex, Spring AI, LangGraph)
package these steps behind convenient abstractions.

## Decision
Every pipeline stage is written by hand in this repo. No RAG/orchestration
frameworks or AI convenience libraries, not even "just for embeddings."
LLM and embedding calls go through small hand-written interfaces so
providers/models swap via configuration.

## Alternatives Considered

### Spring AI / LangChain / LlamaIndex
- Pros: days faster to a demo; battle-tested edge-case handling.
- Cons: the mechanisms this project exists to teach would be invisible; debugging
  retrieval failures would mean debugging someone else's abstraction.
- Rejected: the learning IS the deliverable. A framework optimizes away the point.

## Consequences
- More code, and every non-trivial hand-built component needs its own unit tests
  (chunker, embedding pipeline, cosine search, citation validator).
- The embedding pipeline needs a calibration test (PLAN §6) because hand-built
  pooling/normalization can be wrong in ways that "look fine but rank garbage."
- Enforced in CLAUDE.md as a hard constraint so coding agents don't "helpfully"
  introduce a framework.
