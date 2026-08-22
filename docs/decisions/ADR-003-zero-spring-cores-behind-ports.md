# ADR-003: Zero-Spring cores behind hand-written ports

## Status
Accepted

## Date
2026-08-20 (backfilled; decided at inception, ports concretized in step 1)

## Context
Spring earns its keep at the edges (web, persistence, module wiring) but tends to
seep into everything. The interesting logic here — retrieval and answering — is
plain algorithms that don't need a framework, and the project wants them
understandable, testable, and swappable in isolation.

## Decision
The `retrieval` and `answering` module cores contain zero Spring imports: plain
Java, constructor-injected by Spring configuration classes at the module edges.
Consumers depend on small hand-written interfaces ("ports") — e.g. the eval
runner sees only `RetrievalPort` and `AnsweringPort` plus small record types,
never entities or Spring beans. Cross-module references use plain IDs (`fundId`),
never another module's entities. `ApplicationModules.verify()` enforces the
module boundaries in the build.

## Alternatives Considered

### Idiomatic Spring throughout (`@Service` cores, `@Autowired` everywhere)
- Pros: less wiring code; the way most tutorials do it.
- Cons: cores become untestable without a Spring context; implementation details
  leak across modules; swapping an implementation (e.g. the ADR-002 pgvector
  experiment) touches consumers.
- Rejected: a port with one implementation costs ~10 lines and buys free
  swap-ability and plain-JUnit testability.

## Consequences
- Ports are defined by the consumer's needs (the eval runner's `RetrievedChunk`
  carries only chunkId/page/section/score — just enough to score a hit).
- The wall-socket rule: implementations must fit the socket; the socket never
  learns about the power plant.
- Slightly more ceremony: each module has explicit config classes doing the wiring.
