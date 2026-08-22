# The eval set — what it is and how to write cases

This folder holds the exam we give our RAG system. Before building any pipeline,
we write down 30 questions **whose answers we already know** (because we looked
them up in the PDFs ourselves). Later, the eval runner asks the system these
questions and checks: did it find the right page? Did it cite properly? Did it
refuse when it should?

Without this file, "the retrieval works better now" is just a feeling.
With it, it's a number.

## File: `eval-set.json`

Top level:

| Field | Meaning |
|---|---|
| `schema_version` | Always `1` for now. Lets us change the format later without confusion. |
| `cases` | The list of exam questions. Target: 30 (10 hand-written, 20 generated + verified, ~5 of the 30 unanswerable). |

Each case:

| Field | Meaning |
|---|---|
| `id` | A short unique name like `hdfc-flexi-exit-load-8mo`. Never reuse or rename one — it's how we compare runs over time ("case X passed yesterday, fails today"). |
| `type` | `answerable` (the answer is in the document) or `unanswerable` (it is not, and the correct behavior is to refuse). |
| `question` | The question, phrased the way a real investor would ask it. |
| `fund_id` | A slug like `hdfc-flexi-cap`. The fund catalog (built later) will use these same slugs, so eval and catalog agree by name. |
| `expected_answer` | The correct answer in your own words. **For humans only** — the runner never string-compares it; it's there so a person can spot-check. `null` for unanswerable cases. |
| `source_pages` | Every PDF page where the fact appears. A retrieved chunk landing on ANY of these pages counts as a hit. ⚠️ Use the page number your **PDF viewer** shows, not the number printed in the document footer — they usually differ (cover pages, table of contents). |
| `expected_section` | The SEBI-mandated heading the fact lives under, as printed (e.g. "Load Structure"). Compared case-insensitively. Backup signal in case page matching is off. |
| `provenance` | `hand` (you read the PDF and wrote it) or `generated-verified` (Claude drafted it, you verified it against the PDF). |
| `notes` | Anything a future you would want to know. Optional. |

## Rules the validator enforces (see `EvalSetLoaderTest`)

- Every case has a unique, non-empty `id`.
- `answerable` cases MUST have `expected_answer`, at least one page in
  `source_pages`, and an `expected_section`.
- `unanswerable` cases MUST have `expected_answer: null`, empty `source_pages`,
  and no `expected_section`.
- Unknown/misspelled field names are rejected (typo protection).

Run the check after editing this file:

```
mvn test
```

## Tips for writing good cases

- Cover the questions real investors ask: exit load, expense ratio, lock-in,
  benchmark, minimum SIP amount, riskometer level, fund manager.
- Include at least one "trap" per fund: a question that *sounds* answerable but
  isn't in that document — that's what keeps the system honest.
- Vary the wording: don't phrase the question with the exact words the PDF uses,
  or retrieval gets an artificially easy exam.
- **Expense ratio special case:** SIDs contain only the fee *structure and legal
  maximums*; the actual current TER lives in a separate document on the AMC
  website, which is out of scope for v1. So "what is the current expense ratio?"
  is a great `unanswerable` trap case, while "what is the maximum expense ratio
  this fund can charge?" is the `answerable` sibling citing the fee table.
  Write both — same section, opposite correct outcomes — to test refusal
  precision.
