# ADR-004: Eval set before pipeline code, pinned to exact PDF files

## Status
Accepted

## Date
2026-08-20 (backfilled; schema decided and authored during step 1)

## Context
Retrieval quality claims ("it works better now") are unfalsifiable without a
fixed exam. PLAN §7 sketched the eval; building it surfaced gaps: no way to
express questions whose correct outcome is a refusal, no stable case identity
for diffing runs, and an ambiguity about page numbering.

## Decision
30 question–answer–page cases exist before the first line of pipeline code:
10 hand-written by reading the SIDs (calibrates the builder), 20 generated and
then human-verified against source (`provenance` field records which). The
schema (eval/README.md) includes: stable never-renamed `id`s; a `type` of
answerable|unanswerable (~5 of 30 unanswerable so refusal correctness is
measurable); pages as 1-based PDF pages (what PDFBox sees), never printed footer
numbers. A loader with a validation test enforces the authoring rules on every
`mvn test`.

**Pinning:** every page number refers to the exact PDF files in the local corpus
folder. Eval cases are version-locked to those files; replacing a document means
re-verifying its cases.

## Alternatives Considered

### Write the eval after retrieval exists
- Rejected: the eval would be unconsciously shaped by what the pipeline already
  does well — a test you can't fail.

### Automated answer grading (string/LLM-judge match on expected_answer)
- Rejected for v1: mechanical metrics (hit rate, citation validity, refusal
  correctness) are objective; `expected_answer` stays human-facing for spot checks.

## Consequences
- Retrieval iteration (step 3) has a hard target: ≥90% hit rate on this set.
- The pinning rule exists because one downloaded "SID" turned out to be two
  concatenated documents (SBI SID glued before the ICICI Bluechip SID) — version
  and content drift is real, not theoretical.
- Renaming or reusing a case id after runs begin is forbidden.
