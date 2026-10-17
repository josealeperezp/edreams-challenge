# Prompt log

Newest entries at the bottom. Each entry: date, tool, prompt summary, outcome.

## 2026-10-15 · Claude Code · challenge analysis
**Prompt:** pasted the challenge statement, stated the constraint "Java 25 and
Spring Boot for the backend", asked for a technology and architecture proposal.

**Outcome:** proposal adopted: ingest-then-serve design, hexagonal Maven
modules, virtual threads + Resilience4j core, PostgreSQL + JdbcClient, Redis
with versioned cache keys, keyset pagination, React dashboard with SSE, mock
providers as a separate container. The agent verified current versions
(Spring Boot 4.1.1, Testcontainers 2.0.5, Resilience4j 2.4.0) against Maven
Central before recommending them.

## 2026-10-16 · Claude Code · scaffold
**Prompt:** "create the scaffold in this directory".

**Outcome:** generated the repository skeleton: compose stack, backend
multi-module build with Flyway V1 and ArchUnit, mock providers with static
sample data in the three formats, Vite + React dashboard, CI workflow, this
documentation. Verified by building and booting the stack with
`docker compose up --build`.

## 2026-10-16 · Claude Code · mock providers with data, latency and failures
**Prompt:** the team had not answered the provider questions, so: "edit the simple
mock you just created to return meaningful data", one API per provider (JSON, XML,
CSV), no security, simulate latency and errors, "create a plan for it". Decisions
taken through the plan's questions: bulk stream with optional filters, 100k default
inventory, runtime admin endpoint for chaos. The user rejected a test suite for the
mock because it is tooling, not part of the solution.

**Outcome:** deterministic index-based generator (24 airports, 10 carriers, direct
and one-stop itineraries, provider shares and price factors, five corruption
kinds), StAX / Jackson / plain writers streaming with constant memory, a chaos
filter with per-provider settings and `/admin/chaos`, the contracts documented in
providers-mock/README.md and summarised in SOLUTION.md.
