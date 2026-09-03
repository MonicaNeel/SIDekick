# ADR-008: Retrieval configuration chosen empirically by eval sweep

## Status
Accepted

## Date
2026-08-31

## Context
Step 3's mandate: iterate retrieval until the 30-case eval hits >= 90%.
Baseline was 72%. Instrumenting the eval (per-case retrieved top-k) turned
misses into diagnosable evidence:
- A k=30 probe showed EVERY answerable case findable at rank <= 19 (100%),
  so misses were ranking/seating problems, not semantic gaps.
- The bge query-instruction prefix helps colloquial questions and hurts
  lexically-matched ones (near-disjoint miss sets).
- Max-score fusion of the two query variants failed: their score scales
  differ, one drowns the other (measured, not theorized).
- Same-section overlapping windows hog top-k seats (e.g. four RISK FACTORS
  near-duplicates above the one Taxation chunk that held the answer).

## Decision
Retrieval runs dual-query RRF fusion (plain + bge-prefixed embeddings, ranks
combined by 1/(60+rank), reported scores stay real cosines) with a
max-2-chunks-per-section diversity cap (overfetch 4x, cap, truncate) and k=6.
All of it is configuration (application.yml), selected by a mode x cap x k
grid sweep on the eval: 92.0% (23/25). The two remaining misses are
documented known-hard cases (nfo-period: "NFO" jargon vs a terse "Not
Applicable" one-liner; min-redemption: a cap-eviction boundary case).

## Alternatives Considered

### Bigger embedding model (bge-base)
- Deferred: the k=30 probe showed ranking, not embedding quality, was the
  binding constraint. Re-test if a future corpus lowers the findability rate.

### Hybrid BM25 + vector retrieval
- Rejected for v1 (PLAN defers it): more machinery than the gap justified.

### Just raise k (k=9..13 reaches 92-96%)
- Rejected: every extra chunk feeds the step-4 prompt; the diversity cap
  fixes the actual disease (redundant seats) instead of widening the bus.

### Section-heading-prepended embeddings
- Tried and FAILED (72%): heading text diluted intra-section discrimination.
  Kept as a documented dead end behind sidekick.embedding.prepend-section.

## Consequences
- Overfitting caveat, stated honestly: the config was picked by sweeping the
  same 25 cases it is scored on. Mitigation: neighboring cells scored 88%
  (not a freak cell), both mechanisms are principled, and step 4's citation
  validator provides an independent quality check.
- Two embedding calls per question (fusion) — negligible at this scale.
- Re-run the sweep after any embedding-model swap, corpus growth beyond
  ~20 documents, or chunker change; eval reports in work/eval-runs/ are the
  comparison baseline.
- Score ranges of answerable (0.63-0.86) and unanswerable (0.72-0.79) cases
  OVERLAP: the step-4 pre-generation threshold cannot cleanly separate them,
  so the refusal burden falls mostly on the prompt contract and the
  post-generation validator (gate 1 catches only obvious-junk retrievals).
