# ADR-007: AMC websites as document source; eval funds pinned to verified copies

## Status
Accepted (supersedes ADR-006)

## Date
2026-08-21

## Context
ADR-006 chose SEBI's filings page for convenience. After completing the 30-case
eval set — verified against three specific SEBI-filed PDFs — the owner weighed
accuracy/recency higher: SEBI filings can lag the AMC's current published SID
(ICICI's filings were from Jun 2023), and the product should reflect what the
AMC currently publishes.

The constraint discovered during step 1 still binds: eval page numbers are
pinned to exact file versions (ADR-004). Any source switch for an eval fund
invalidates its verified pages.

## Decision
- **New catalog funds** (the scale-up beyond the four eval funds) are fetched
  from official AMC websites; the manifest stores the slug and the direct PDF
  URL (no uniform URL structure across AMCs — each entry is hand-curated).
- **The four eval funds stay pinned** to the exact files the eval was verified
  against (local copies in Projects\SIDs: HDFC nov-2024, SBI dec-2024, ICICI
  Bluechip jun-2023 — all SEBI-filed — plus the local ICICI ELSS file).
  Refreshing an eval fund's document is a deliberate event that requires
  re-verifying its eval cases, never an automatic refetch.
- Scale-up is deferred until one document has been ingested end-to-end and
  understood (owner's learning checkpoint).
- The UI discloses each document's source and date; the dated-document and
  taxation disclaimers from PLAN §3 stand unchanged.

## Alternatives Considered

### Switch everything to AMC sites, including eval funds
- Rejected: would invalidate the just-completed verification of 30 cases and
  force an immediate third verification round for zero eval benefit.

### Stay on SEBI (ADR-006)
- Rejected by the owner on recency grounds for the product corpus; SEBI remains
  a fallback when an AMC page is unusable.

## Consequences
- The manifest needs a per-fund `pinned` flag (or equivalent) so refresh logic
  never overwrites an eval fund's document.
- AMC sites are heterogeneous; expect per-AMC quirks in finding stable PDF URLs
  (this is the Tier-2 discovery problem in miniature).
- ADR-006's `mfdId` bookkeeping applies only to the pinned SEBI-sourced files.
