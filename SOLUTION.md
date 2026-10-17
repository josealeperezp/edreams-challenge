# SOLUTION

> Draft. Items marked **TODO** are filled in as each slice lands. Any number in
> this document comes from a measured run on this stack, never from an estimate.

## 1. Overview

Flight Aggregation & Cache Optimization System for an OTA. Three mock GDS
providers are ingested in parallel, normalised into one canonical model,
validated, deduplicated, priced with the OTA markup and stored in PostgreSQL.
The search API and the dashboard read only from that store.

Run: `docker compose up --build`. Dashboard on :3000, API on :8080, mocks on :8081.

## 2. Architectural decisions

### 2.1 Ingest-then-serve
The requirement to paginate up to one million rows and to serve a "daily
inventory" only makes sense with a local, indexed store. The store is the
cache named in the title. Searches never touch a provider, so provider
latency (100 ms to 5 s) and 500s affect ingestion freshness, never the user.

### 2.2 Hexagonal modules
Maven multi-module with compile-time isolation: `domain` (pure Java),
`application` (use cases and ports), `infrastructure` (adapters), `bootstrap`
(Spring Boot wiring). An ArchUnit test fails the build if the direction is
violated or a framework type leaks into domain or application.

### 2.3 Concurrency and resiliency
Java 25 virtual threads for the provider fan-out, with a per-provider time
budget. Structured Concurrency is still a preview feature in Java 25 and is
deliberately not used. Resilience4j core (timeout, bounded retry on 5xx,
circuit breaker, bulkhead) decorates each provider adapter programmatically in
`infrastructure`, so no Spring starter compatibility is involved. Partial
results are first-class: the ingestion run records which providers failed.

### 2.4 Persistence
PostgreSQL with Flyway and `JdbcClient`. Hand-written SQL gives control over
batch upserts and the composite indexes that the keyset queries need. JPA was
rejected because the entity lifecycle adds overhead to a write-heavy pipeline
and a read-only search path.

### 2.5 Cache strategy
Redis caches search pages keyed by normalised filters, cursor and the current
inventory version. Ingestion completion bumps the version, so stale keys
expire by TTL without scanning or deleting. **TODO** measured hit ratio and
latency with and without the cache.

### 2.6 Frontend
React + TypeScript + Vite, served by nginx, which also proxies `/api` and
`/actuator` to the backend. Real-time updates via Server-Sent Events carrying
provider health and the inventory version.

### 2.7 Provider contracts assumed
The team did not reply to our questions about the real providers, so the three
contracts are defined by us and implemented in `providers-mock`, documented in
[providers-mock/README.md](providers-mock/README.md). Each provider streams its
whole daily inventory with optional origin, destination and date filters. The
formats carry deliberate quirks the adapters must normalise: Alpha zero-pads
flight numbers and sends prices as doubles; Beta splits price into base and
taxes and nests everything; Gamma joins carrier and number, repeats the price
per segment row and uses UTC times without offset. The mock is deterministic
from a seed, overlaps providers on purpose, damages about 1.5 % of copies with
dead legs or invalid connections, and injects 100 ms to 5 s of latency plus a
10 % failure rate that can be changed at runtime through an admin endpoint.
The mock is tooling, not part of the solution, and has no test suite. Measured
at one million itineraries it streams 169 MB of JSON in 1.5 s and 344 MB of
XML in 4.0 s with container memory flat at 142 MiB under a 256 MB limit.

### 2.8 AI tools utilized
See [docs/ai/README.md](docs/ai/README.md) for the tools, the standing
instructions in `CLAUDE.md`, and the prompt log.

## 3. Data model and pagination

### 3.1 Canonical model and deduplication signature
**TODO** describe `Itinerary`, `Segment`, `Money`, and the signature built from
ordered segments (carrier, flight number, origin, destination, departure in UTC).

### 3.2 Schema and indexes
See `backend/bootstrap/src/main/resources/db/migration/V1__init.sql`.
`itinerary` holds the winning offer per signature and is the only table the
search touches. `provider_offer` keeps provenance per provider.
`ingestion_run` records each run and its per-provider outcome.

### 3.3 Keyset vs offset
Keyset, with an opaque cursor encoding the sort value and the row id, backed by
the composite index `(origin, destination, price, id)`. Offset scans and
discards every skipped row, so cost grows with page depth. Keyset costs one
page regardless of depth. Trade-offs: no random page jumps and no cheap exact
total, so the API returns `hasNext` and an approximate count.

**TODO** measured table: p50/p99 for offset vs keyset at pages 1, 100, 1 000
and 10 000 over one million rows.

## 4. Limitations and trade-offs
**TODO** as they are made. Known from the start:
- Ingestion runs in-process. A production system would decouple it with a queue.
- When a provider is down, its previous offers are kept until the next
  successful run. Freshness per provider is exposed in the run record.
- A single currency is assumed until the provider contracts say otherwise.

## 5. Testing strategy
- domain/application: plain JUnit 5 + AssertJ, no Spring context.
- adapters: WireMock for provider parsers and resiliency, `@WebMvcTest` for the web layer.
- bootstrap: Testcontainers (PostgreSQL, Redis) with `@SpringBootTest`, plus ArchUnit.
- frontend: Vitest + Testing Library.
- CI runs all of the above and boots the compose stack.
