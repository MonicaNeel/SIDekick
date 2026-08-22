# ADR-006: SEBI filings page as the document source

## Status
Superseded by ADR-007

## Date
2026-08-20

## Context
The catalog needs SID PDFs for 15–20 funds across 3–4 AMCs. PLAN originally
said "official AMC sites"; SEBI's filings page
(sebiweb/other/OtherAction.do?doMutualFund=yes&mftype=2) hosts every AMC's
filed SIDs in one place. Investigation findings (2026-08-20):
- Structure: AMC index → per-AMC scheme list (`mfId`) → per-fund page (`mfdId`)
  embedding exactly one direct PDF URL (the latest filed SID).
- Recency is mixed per AMC, not uniformly stale: HDFC filed Nov 2024 and SBI
  Dec 2024 (both NEWER than our local AMC-site copies), while ICICI's filings
  were Jun 2023.
- PDF URLs are timestamped and change when a new SID is filed; the `mfdId`
  fund-page URL is the stable pointer.

## Decision
All catalog SIDs are fetched from SEBI's filings page. The manifest stores, per
fund: the slug (`fund_id`), the stable SEBI `mfdId`, and the currently-resolved
direct PDF URL. The UI must display a disclaimer that scheme documents are
fetched from SEBI's public filings (and may lag the AMC's latest version).

## Alternatives Considered

### Individual AMC websites
- Pros: always the AMC's current published version.
- Cons: four differently-organized sites to navigate and re-check; no uniform
  URL structure; recency advantage turned out to be inconsistent (SEBI was
  newer for 2 of our 4 funds).
- Rejected: convenience and uniformity win; recency is a wash in practice.

## Consequences
- Eval cases previously verified against local AMC-site PDFs (HDFC Flexi Cap
  jun-2024, SBI Small Cap) are re-mapped to the SEBI versions; page numbers and
  any changed facts must be re-verified (ADR-004 pinning now points at the SEBI
  files).
- ICICI Long Term Equity: local file believed same vintage as SEBI's jun-2023
  filing; parity unverified — check before ingestion.
- Refresh command can detect a new filing by re-resolving `mfdId` and comparing
  the PDF URL.
- Filed versions may lag AMC sites — accepted, and disclosed in the UI.
