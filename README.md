# SIDekick

Exact answers from Indian mutual fund Scheme Information Documents (SIDs), with
page numbers — or an honest refusal. No advice, no guessing.

A hand-built RAG (retrieval-augmented generation) system: chunking, embeddings,
cosine search, prompt assembly, and citation validation are all written from
scratch in this repo — deliberately. Understanding every piece is the point.
See [PLAN.md](PLAN.md) for scope and build order, and
[docs/decisions/](docs/decisions/README.md) for why things are the way they are.

## Status

Build-order step 1 (eval set before any pipeline code) — in progress.

## Stack

Java 25 · Spring Boot 4 + Spring Modulith · Postgres (Docker) · PDFBox ·
bge-small-en-v1.5 via ONNX Runtime · OpenRouter for generation.
No RAG frameworks, no vector DB ([ADR-001](docs/decisions/ADR-001-no-rag-frameworks.md),
[ADR-002](docs/decisions/ADR-002-no-vector-db.md)).

## Run

```
mvn test              # unit tests incl. eval-set validation
mvn spring-boot:run   # app (needs: docker compose up -d postgres, once compose exists)
```

The OpenRouter API key lives in the `OPENROUTER_API_KEY` environment variable —
never in code or config files.
