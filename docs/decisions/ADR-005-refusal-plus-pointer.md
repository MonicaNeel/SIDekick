# ADR-005: Refuse-with-pointer instead of best-effort answers

## Status
Accepted

## Date
2026-08-20 (backfilled; decided at inception, sharpened during eval authoring)

## Context
The system answers questions about legal documents people rely on. A wrong
answer stated confidently ("the exit load is 1.8%") is worse than no answer.
Free-tier generation models follow citation instructions unreliably, and some
facts are physically unreachable (e.g. risk-o-meter levels that exist only
inside an image, invisible to text extraction).

## Decision
When the system cannot ground an answer in retrieved text, it refuses — and the
refusal carries a pointer: "not found in this document; the nearest relevant
section is X on page Y" (derived from the top-ranked chunk). Enforcement is a
two-stage gate:
1. **Pre-generation:** top retrieval score below a threshold (tuned on the eval
   set, not guessed) → decline before spending an LLM call.
2. **Post-generation:** a hand-written citation validator checks every cited
   chunk was actually retrieved, every answer cites ≥1 chunk, and quoted
   excerpts appear verbatim; any failure → the refusal response.

## Alternatives Considered

### Always answer with a confidence disclaimer
- Rejected: disclaimers don't stop users acting on wrong numbers; hallucinated
  specifics in a compliance-document context are the primary failure mode.

### Bare refusal without a pointer
- Rejected: needlessly unhelpful. The pointer converts a dead end into "open the
  PDF to page N" — which is also the only honest response when the fact is
  image-only.

## Consequences
- The eval measures refusal correctness in both directions: refusing
  unanswerables AND answering answerables (a refuse-everything system must score
  badly).
- Unanswerable eval cases record the expected pointer target in notes; pointer
  quality is graded by eye in v1.
- Validation failure costs a wasted LLM call — acceptable; correctness first.
