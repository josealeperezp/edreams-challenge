# Prompt log

Newest entries at the bottom. Each entry: date, tool, prompt summary, outcome.
Tools and working method: SOLUTION.md, section 2.9.

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

## 2026-10-17 · Claude Code · backend: ingestion and search
**Prompt:** "let's work on the backend ... the ingestion layer where the
information is retrieved and stored properly according with your design and
then expose the endpoints to the frontend ... is the right one or do you
recommend another approach?" Agreed through the plan's questions: dedup in the
domain over a database-sorted stream, keep a failed provider's itineraries
with freshness exposed, runs at startup plus manual endpoint plus daily cron,
Redis cache and SSE included.

**Outcome:** domain core (value objects, validator, signature, pricing,
selector) with 38 tests; application use cases and ports with fake-based
tests; three streaming adapters, Resilience4j decorator, JDBC repositories
with idempotent batch inserts and a streaming cursor, Redis cache with
versioned keys, REST and SSE endpoints; Testcontainers and WireMock
integration tests; end-to-end test proving one shared itinerary comes out
once at the cheapest base price plus markup with three quotes. Defects the
tests caught before any manual run: a Gamma regex that split "845" into
carrier "84"; a slice test scanning repositories because a plain
@ComponentScan lacks Boot's TypeExcludeFilter; the PostgreSQL driver refusing
java.sql.Timestamp for TIMESTAMP_WITH_TIMEZONE; Jackson 3 shifting
OffsetDateTime to UTC on read unless DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
is disabled. All fixed; 83 tests green.

**Found only by running the real stack, not by the suite:** the resolve phase
upserted its first 1 000-row batch from inside the read-only streaming
transaction, on the same pooled connection, and PostgreSQL refused the write;
the end-to-end test resolved two itineraries, below the batch size. Fixed with
a REQUIRES_NEW transaction for the upsert and a batch size of 1 in the test so
it exercises that path. A stopped Redis made a search take 180 s (three
Lettuce 60 s timeouts); fixed with 500 ms timeouts and a 10 s bypass window.
At one million itineraries every provider failed at exactly ten seconds:
the 10 s "read timeout" of Spring's JDK request factory applies to the whole
streamed body, not to the first byte, so any stream longer than that is cut.
Fixed by making the per-attempt timeout equal to the run budget (300 s) and
documenting the semantic; the connect timeout is what catches a dead host.
Final measurement: one million itineraries ingested in 104 s (954 057
resolved, 1.92 M offers stored, memory flat at about 560 MiB), and the
pagination benchmark shows offset at 347 ms versus keyset at 0.31 ms at page
10 000. Both tables are in SOLUTION.md.

## 2026-10-17 · Claude Code · frontend dashboard
**Prompt:** "we need to complete the frontend dashboard ... a search form, a
keyset 'load more', a couple of charts, and an SSE panel showing run progress
and provider circuit state. In this implementation, I'm more interested in
aligning the frontend with the requirements of the assessment, in terms of
what we can do in the interface. Propose a plan for this slice." The plan
mapped every element to the assessment's "search, filter, and visualize these
itineraries in real-time". Decision taken through the plan's question: the
charts describe the whole filtered inventory through a new stats endpoint
rather than the pages loaded so far.

**Outcome:** `GET /api/itineraries/stats` (one hash aggregate behind the
same filters, cached per inventory version, 9 new backend tests) and the
dashboard: search form with the four filters mirrored into the URL, keyset
"Load more", results with base price, marked-up price, every quote and a
stale marker, stats headline with three Recharts charts, SSE-driven run panel
with per-provider outcome, provider circuit panel, run history, "Run
ingestion now" with the 409 surfaced. 32 frontend tests against a fetch
router and a fake EventSource. Two defects caught before any manual run: a
service test built with a fake cursor string that the validator rejected,
and seven frontend tests failing on "multiple elements" because Testing
Library cannot register its DOM cleanup without vitest globals; both fixed in
the tests' setup. Verified on the compose stack at 100k: the stats totals
equal `count(*)` in SQL for every filter tried, the breaker scenario shows
PARTIAL, Beta OPEN and stale rows in the table, and headless Chromium
screenshots in light and dark mode showed one display defect, run durations
rounded to "0m", fixed to keep seconds under an hour. At one million rows
EXPLAIN ANALYZE showed the first version of the stats query (GROUPING SETS)
sorting the whole table on disk, 0.8 s; regrouping on all four columns at
once with a Java rollup replaced the sort with a hash aggregate.

## 2026-10-18 · Claude Code · documentation consolidation
**Prompt:** "Let's organise the documentation a little bit": merge `CLAUDE.md`
and `docs/ai` into SOLUTION.md, move this log to the repository root, end
with README.md and SOLUTION.md as the only documentation files. "Plan first,
then execute."

**Outcome:** decided through the plan's questions: this log lives at the root
as a separate file, and `CLAUDE.md` is kept untouched because Claude Code
loads it automatically, in the live session too. `docs/ai/README.md` became
SOLUTION.md 2.9 and the folder was removed; the README layout and the
definition of done in `CLAUDE.md` point to the new path.
