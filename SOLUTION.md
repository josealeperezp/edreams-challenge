# SOLUTION

> Every number in this document comes from a measured run on this stack,
> never from an estimate. The commands that produce them are in section 5
> and in `providers-mock/README.md`.

## 1. Overview

Flight Aggregation & Cache Optimization System for an OTA. Three mock GDS
providers are ingested in parallel, normalised into one canonical model,
validated, deduplicated, priced with the OTA markup and stored in PostgreSQL.
The search API and the dashboard read only from that store.

Run: `docker compose up --build`. Dashboard on :3000, API on :8080, mocks on
:8081. The backend ingests the mock inventory as soon as it is ready, so the
stack never boots empty.

## 2. Architectural decisions

### 2.1 Ingest-then-serve
The requirement to paginate up to one million rows and to serve a "daily
inventory" only makes sense with a local, indexed store. The store is the
cache named in the title. Searches never touch a provider, so provider
latency (100 ms to 5 s) and 500s affect ingestion freshness, never the user.
Search-time aggregation was rejected for that reason; a message queue between
ingestion and resolution was rejected as a broker in the stack for no visible
benefit in a take-home.

### 2.2 Hexagonal modules
Maven multi-module with compile-time isolation:
- `domain`: plain Java. `AirportCode`, `Carrier`, `FlightNumber`, `Money`,
  `Segment`, `RawOffer`, `ProviderOffer`, `Itinerary`, `ItinerarySignature`
  and the rules `ItineraryValidator`, `PricingPolicy`, `BestOfferSelector`.
- `application`: use cases `IngestionService` and `SearchService` plus the
  ports they need (`FlightProviderPort`, repositories, `SearchCache`,
  `IngestionEvents`). JDK only; concurrency through a `java.util.concurrent`
  executor injected from bootstrap.
- `infrastructure`: one anti-corruption adapter per provider, Resilience4j
  decorator, JDBC repositories, Redis cache, REST controllers, SSE relay.
- `bootstrap`: Spring Boot wiring, configuration properties, scheduler,
  migrations, ArchUnit and integration tests.

`ArchitectureTest` fails the build if the dependency direction is violated or
a framework type leaks into domain or application. No class in those two
modules carries a Spring, Jakarta or Jackson annotation.

### 2.3 Ingestion pipeline
One run, in `IngestionService`:
1. Fan out to the three providers on virtual threads with a shared time
   budget. Each adapter streams its body (Jackson token parser for JSON, StAX
   for XML, line reader for CSV) and pushes raw offers through a sink.
2. The sink validates each raw offer with the domain validator, counts
   rejections by reason, and batch-inserts valid offers into `provider_offer`
   per run and provider. Inserts are `ON CONFLICT DO NOTHING` on
   `(run, signature, provider)`, so a retry that re-streams a provider after a
   mid-stream failure cannot duplicate rows.
3. Resolve: the run's offers are read back ordered by signature through a
   server-side cursor and every consecutive group goes to `BestOfferSelector`,
   which picks the cheapest, applies the markup and keeps all quotes. The
   result is upserted into `itinerary` by signature. The database sorts, the
   domain decides, memory stays constant whatever the volume.
4. Old runs are purged, rows unconfirmed for the last N runs are dropped, the
   inventory version is bumped and the run record is marked COMPLETED,
   PARTIAL or FAILED.

Runs start at boot, on `POST /api/ingestion/runs` and on a daily cron; a
second start while one runs gets 409. Progress is published over SSE.

### 2.4 Resiliency
Structured Concurrency is still a preview feature in Java 25 and is
deliberately not used; the fan-out uses `CompletableFuture` on a
virtual-thread executor with a deadline. Each provider adapter is wrapped by
`ResilientFlightProvider`: Resilience4j core `Retry` (3 attempts, exponential
backoff from 500 ms, only on 5xx, connection and timeout errors; never on
parse errors) inside a `CircuitBreaker` (count window 4, 50 % failure rate,
30 s open). Applied programmatically, so no starter compatibility issue and
no annotation outside infrastructure. Timeouts are layered: a 2 s connect
timeout catches a dead host, the per-attempt stream timeout (300 s by default,
`providers.*.timeout`) bounds a whole fetch because Spring's JDK request
factory applies it to the body as well as the headers, and the run budget
(`flights.ingestion.provider-budget`) bounds every provider from the
orchestrator's side and cancels the task when exceeded. A provider dropping out never fails the
run: its rows for the run are deleted, the run is PARTIAL, the other two
providers' offers are resolved, and the API exposes which provider failed and
the breaker state.

### 2.5 Persistence
PostgreSQL with Flyway and plain JDBC (`JdbcClient` and `JdbcTemplate`).
Hand-written SQL gives control over batch inserts, the upsert, the streaming
cursor (fetch size inside a read-only transaction) and the composite indexes
the keyset queries need. JPA was rejected because the entity lifecycle adds
overhead to a write-heavy pipeline and a read-only search path.

### 2.6 Cache strategy
Redis caches search pages keyed by inventory version, normalised filters and
cursor. A completed run bumps the version, so stale pages are never served and
old keys expire by TTL without scanning or deleting. Every Redis error is
logged and treated as a miss, and after a failure Redis is skipped for ten
seconds, so an outage costs one 500 ms timeout per window. Measured with Redis
stopped: a search answered in 0.7 s on the first call and in milliseconds
afterwards, instead of the 180 s Lettuce's default 60 s command timeout would
have cost three times over.

Honest measurement at 100 000 itineraries: a route page costs about 7 ms
from PostgreSQL and about 15 ms from the cache, because deserialising the
cached page costs more than one indexed range scan on a warm database. The
cache is therefore a protection against load on the database, not a latency
win at this size. At one million itineraries the picture flips: a route page
costs 210 ms cold and 16 ms cached, and a filtered page 18 ms versus 11 ms.

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

### 2.8 Frontend
React 19 + TypeScript + Vite, served by nginx, which also proxies `/api` and
`/actuator` to the backend, so the browser only ever talks to relative paths.
The assessment asks for a dashboard "to search, filter, and visualize these
itineraries in real-time"; every element on the page maps to one of those
verbs and nothing else was added.

- Search and filter: one form with the four filters (origin, destination,
  max price, carrier), validated in the browser with the same rules as the
  API and mirrored into the URL so a search is bookmarkable. Results are a
  table sorted by price with a "Load more" button that follows the keyset
  cursor; there are no page numbers because the API has none. Every row shows
  the base price, the marked-up price and every provider's quote with the
  winner highlighted, so the dedup and cheapest-wins rules are visible per
  row. Rows last confirmed by a run older than the newest finished one carry
  a "stale" marker: they survived a provider failure.
- Visualize: `GET /api/itineraries/stats` takes the same four filters and
  returns the match count, the price range, a 20-bucket price histogram, the
  share of itineraries each provider won and the top carriers. The charts
  therefore describe the whole filtered inventory, not the pages loaded so
  far. The aggregation is one hash aggregate over the filtered rows on the
  four group columns at once (carrier, winning provider, stops, price
  bucket: at most a few thousand groups) rolled up in Java, served by the
  route indexes when a route is given and by one sequential scan when nothing
  is filtered, and cached in Redis per inventory version exactly like pages
  (timings in 3.4).
- Real-time: the page subscribes to `GET /api/ingestion/events`
  (Server-Sent Events). `run-started`, `provider-finished` and
  `run-completed` each carry the whole run, so the panel shows per-provider
  latency, accepted and rejected counts and errors as each provider finishes,
  and a "Run ingestion now" button starts one (a 409 is shown as such). On
  `run-completed` the stats and the first page of results are refetched in
  place; if the user had loaded several pages a banner offers to reload
  instead of pulling the list away. Provider circuit states come from
  `GET /api/providers/status`, polled every 3 s while a run is on or a
  breaker is not CLOSED and every 30 s otherwise. If the stream drops the
  browser reconnects, the server replays the last event, and the latest run
  is polled meanwhile.

Recharts draws the three charts at explicit pixel sizes measured with a
ResizeObserver, so they also render in jsdom tests. There is no router, no
global state library and no CSS framework: TanStack Query holds server state,
React state holds the form.

### 2.9 AI tools utilized
Claude Code (CLI) running the Claude Fable 5.1 model, used as a pair
programmer from the terminal. Plan-driven strategy was used to implement this solution. Two files make that work reproducible:
- `CLAUDE.md` at the repository root is the standing instruction set the
  agent loads every session: architecture rules, conventions, commands and
  the definition of done. It is the "agent system" engineered for this
  codebase.
- [prompts.md](prompts.md) is the chronological log of the prompts that
  produced non-trivial work, with what was accepted, changed or rejected.

Working method: every AI-generated change is reviewed and run locally. The
test suite and the ArchUnit rules gate what is kept.

## 3. Data model and pagination

### 3.1 Canonical model and deduplication signature
Adapters produce a `RawOffer`: provider, provider offer id, raw segments with
string codes and parsed times, price and currency. The domain validator turns
it into a typed `ProviderOffer` or rejects it with one reason: no segments,
dead leg, negative duration, broken connection, connection shorter than 30
minutes, layover above 24 hours, missing carrier, invalid price, unsupported
currency, malformed codes.

The deduplication key is the assessment's rule made literal. Two offers are
the same itinerary when they share airline, flight numbers, connections and
departure times, so `ItinerarySignature` is the ordered list of segments, each
written as carrier plus number, origin, destination and the departure instant
in UTC truncated to the minute, for example
`IB845|MAD|BCN|2026-11-02T06:15:00Z;BA2845|BCN|LHR|2026-11-02T09:05:00Z`.
Price, arrival time, provider ids and format quirks are excluded by
construction: Alpha's `0845` with a `+01:00` time, Beta's `845` and Gamma's
`IB845` at `06:15` UTC collapse to one key, which the unit test
`ItinerarySignatureTest` proves.

`BestOfferSelector` takes every provider's offer for one signature, picks the
cheapest (ties to the lower provider id for determinism), applies the markup
(`BigDecimal`, scale 2, HALF_UP: 85.50 becomes 89.78) and keeps all quotes as
provenance. The result is an `Itinerary`, the only thing the search index
stores.

### 3.2 Schema and indexes
`itinerary` holds one row per signature with the winning offer, the marked-up
price, the segments and quotes as JSONB, and the run that last confirmed it.
`provider_offer` is an insert-only snapshot per run, unique per (run,
signature, provider); that unique index is also the access path of the
resolve phase's ordered read. `ingestion_run` records each run with per-provider
counts, latency and error as JSONB. The hot tables carry the run id but no
foreign key, so bulk inserts pay no constraint check per row.

Indexes on `itinerary`: `(origin, destination, price, id)` serves the route
filter, the max-price bound, the price sort and the keyset cursor in one range
scan; `(origin, destination, carrier, price, id)` adds the carrier filter;
`(price, id)` serves the unfiltered sort; `(ingestion_run_id)` serves the
purge.

### 3.3 Keyset versus offset
The API paginates with an opaque cursor encoding the price and row id of the
last item served. The next page is `WHERE (price, id) > (:price, :id) ORDER BY
price, id LIMIT n`, one index range scan whatever the depth. Offset pagination
scans and discards every skipped row, so page 10 000 costs 200 000 rows.
Trade-off: no random page jumps. A page carries `hasNext`, the next cursor
and the inventory size; the exact filtered count comes from the stats
endpoint, which the dashboard shows as its headline.

Measured by `PaginationBenchmark` on the compose PostgreSQL after the
one-million ingestion below (954 057 itineraries, page size 20, 30 timed
iterations per cell, JDK 25, Docker Desktop on an Apple Silicon laptop):

| Filter | Page | Offset p50 | Offset p99 | Keyset p50 | Keyset p99 |
|---|---|---|---|---|---|
| none | 1 | 0.68 ms | 1.18 ms | 0.86 ms | 3.86 ms |
| none | 100 | 1.28 ms | 1.85 ms | 0.52 ms | 0.65 ms |
| none | 1 000 | 9.66 ms | 14.22 ms | 0.47 ms | 0.85 ms |
| none | 10 000 | 346.64 ms | 467.07 ms | 0.31 ms | 0.43 ms |
| MAD to BCN | 1 | 0.23 ms | 0.37 ms | 0.22 ms | 0.34 ms |
| MAD to BCN | 10 | 0.28 ms | 0.59 ms | 0.23 ms | 0.33 ms |
| MAD to BCN | 50 | 0.42 ms | 0.52 ms | 0.20 ms | 0.30 ms |

Offset cost grows linearly with depth and reaches a third of a second at page
10 000 over the whole inventory, while keyset stays at a third of a
millisecond, about a thousand times faster at that depth and flat throughout.
On a single route the difference is small because a route holds under two
thousand rows, which is exactly why the index covers the route filter first.

### 3.4 Volume
Default stack, 100 000 unique itineraries in the mock, chaos on (100 ms to 5 s
latency, 10 % failures), measured on the compose stack:

| Measure | Value |
|---|---|
| Provider offers received, Alpha / Beta / Gamma | 70 153 / 64 853 / 59 711 (exactly the mock's expected counts) |
| Offers rejected by the validator | 1 046 / 963 / 884, every reason observed |
| Offers stored for the run | 191 824 |
| Itineraries resolved | 95 412, equal to the distinct signatures of the run |
| Itineraries with three quotes | 25 942 |
| Rows whose price differs from base times 1.05 | 0 |
| Whole run, fan-out plus resolve | 13 s |
| Backend container memory after the run | 410 MiB |
| MAD to BCN keyset walk | 680 rows over 28 pages, ordered, no duplicate |

With Beta forced to fail the run is PARTIAL in 15 s, Beta's breaker is OPEN at
a 75 % failure rate, the other two providers' offers are resolved, search keeps
answering, and after the 30 s open window the next run is COMPLETED again.

One-million inventory, `MOCK_INVENTORY_SIZE=1000000`, chaos on, same stack:

| Measure | Value |
|---|---|
| Provider offers received, Alpha / Beta / Gamma | 699 679 / 649 330 / 599 418 |
| Offers rejected by the validator | 10 552 / 9 821 / 8 921 |
| Offers stored for the run | 1 919 133 |
| Fetch, validate and store per provider, in parallel | 36 s to 40 s |
| Resolve phase, read back ordered and upsert | about 60 s |
| Whole run | 104 s |
| Itineraries resolved | 954 057, equal to the distinct signatures |
| Itineraries with three quotes | 260 087 |
| Rows whose price differs from base times 1.05 | 0 |
| Backend container memory during the run | flat between 504 and 565 MiB |
| Table sizes after the run | itinerary 728 MB, provider_offer 989 MB |
| Route search, cold then cached | 210 ms then 16 ms |
| Route plus carrier plus max price, first then cached | 18 ms then 11 ms |
| MAD to BCN keyset walk | 18 pages of 100, complete, no duplicate |

Stats endpoint on the same one-million inventory, measured end to end
through nginx with the Redis key deleted before each cold call (three cold
calls, then the hit), the filters being those the dashboard sends:

| Filters | Cold | Cached |
|---|---|---|
| none (954 057 rows, parallel sequential scan and hash aggregate) | 0.52 s on the first call after a restart, then 0.26 s and 0.22 s | 10 ms |
| origin and destination | 15 ms, then 6 ms | 3 ms |
| origin, destination, carrier and max price | 10 ms, then 5 ms | 3 ms |
| carrier only | 130 ms | 3 ms |
| max price only | 160 ms | 3 ms |

`EXPLAIN ANALYZE` of the unfiltered aggregate: parallel sequential scan with
two workers, partial hash aggregate of 898 groups per worker, 178 ms inside
PostgreSQL. The first version of the query used `GROUPING SETS` and sorted the
whole table on disk (31 MB external merge, 0.8 s); grouping on the four
columns at once and rolling the marginals up in Java removed the sort. The
cold cost is paid once per inventory version and filter combination; the
dashboard's charts otherwise come from Redis.

The run is bounded by PostgreSQL write throughput, not by provider latency:
the mock streams its share in under five seconds and the backend spends the
rest validating and batch-inserting two million rows, then upserting one
million. Memory stays flat because nothing is held per run: provider bodies
are streamed, inserts are batched at 1 000 rows, and the resolve phase reads
through a server-side cursor.

## 4. Limitations and trade-offs
- Ingestion runs in-process. A production system would decouple it with a
  queue and run the resolve phase as a separate worker.
- Partial runs keep the failed provider's previously confirmed itineraries
  searchable; the run record and the provider status endpoint expose which
  provider is stale. Rows unconfirmed for three runs are purged.
- A retry that re-streams a provider after a mid-stream failure counts the
  re-sent offers again in `received`; stored rows are not duplicated.
- One currency, EUR, is supported. Offers in another currency are rejected
  with `UNSUPPORTED_CURRENCY` rather than converted.
- The carrier filter matches the marketing carrier of the first segment. A
  mixed-carrier itinerary is not found by its second carrier.
- Unfiltered stats aggregate the whole table in one sequential scan, paid
  once per inventory version and then served from Redis; filtered stats use
  the route indexes. The carrier chart shows the ten most frequent carriers.
- SSE subscribers live in the memory of one backend instance. A second
  instance would need a shared channel (for example Redis pub/sub) to fan
  run events out to every dashboard.
- The dashboard is an internal operations view with no authentication.
- `provider_offer` of the last three runs is kept, which at one million
  itineraries is about six million rows; retention is configurable.

## 5. Testing strategy
92 automated tests run by `mvn verify` in `backend/`, 32 by `npm test` in
`frontend/`:
- domain, 38: value objects, validator (one case per rejection reason using
  the mock's corruption kinds), signature invariance across provider formats,
  markup rounding, cheapest selection and tie-break.
- application, 21: the orchestration against in-memory ports (completed,
  partial and failed runs, rows of a failed provider dropped, concurrent
  start rejected, purge), cursor and query validation, search service with
  cache hit and miss for pages and for stats, price bucket splitting.
- infrastructure, 18: each adapter against WireMock payloads copied from the
  mock contracts including a malformed record, 500 and parse failures; the
  resilience decorator (retry then success, no retry on parse errors, timeout,
  breaker opening and failing fast); the API slice with problem details and
  the stats endpoint.
- bootstrap, 15: ArchUnit rules; Testcontainers PostgreSQL and Redis for
  idempotent inserts and ordered streaming, keyset pages covering every row
  once under every filter, stats equal to the walked pages under every
  filter, upsert and purge, run round trip, cache round trip with TTL for
  pages and stats; and the end-to-end pipeline against WireMock providers,
  both healthy and with one provider down.
- frontend, 32 (Vitest, Testing Library, jsdom): filter normalisation and URL
  round trip, problem-detail mapping, form validation, results table (prices,
  quotes, stale marker, load more, cursor), stats panel against a mocked
  endpoint, the SSE hook against a fake EventSource (run tracking, reconnect,
  refresh in place versus banner), run and provider panels in every state,
  and the whole page with every endpoint mocked including a 409 on start.
- CI runs all of the above and boots the compose stack.
