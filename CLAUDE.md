# Flights aggregation challenge: working rules for AI agents

## What this is
OTA flight aggregator. Ingest from 3 mock GDS providers (Alpha JSON, Beta XML,
Gamma CSV), validate, deduplicate, apply the 5% OTA markup, store in Postgres,
serve a keyset-paginated search API and a React dashboard. Requirements and
design: SOLUTION.md.

## Layout
- backend/domain: pure Java. Model, value objects, domain services, repository ports. No frameworks.
- backend/application: use cases and outbound ports. JDK + domain only.
- backend/infrastructure: adapters. Provider clients (alpha/beta/gamma), persistence (JdbcClient), cache (Redis), web (REST, SSE), config properties.
- backend/bootstrap: Spring Boot app, wiring, application.yml, Flyway migrations, architecture and integration tests.
- providers-mock: standalone Spring Boot app simulating the three GDS providers.
- frontend: React + TypeScript + Vite dashboard, served by nginx in Docker. src/api (fetch client, JSON types mirroring the backend records), src/search (filters, form, results, infinite query), src/charts (stats panel, Recharts), src/ingestion (SSE hook, run panel, provider circuits, history), src/test (fetch router, fake EventSource, fixtures).

## Architecture rules
Enforced by backend/bootstrap/src/test/java/com/edreams/flights/ArchitectureTest.java.
- Dependency direction: bootstrap -> infrastructure -> application -> domain. Never the reverse.
- domain and application never import org.springframework, jakarta, tools.jackson, com.fasterxml, io.github.resilience4j, java.sql.
- No framework annotations in domain or application. Beans are wired in bootstrap configuration classes.
- Money is BigDecimal with scale 2, never double. Times are Instant or OffsetDateTime with an explicit zone.
- Prefer records and sealed interfaces. Validate invariants in compact constructors.
- Provider parsers stream. Never load a full provider response into memory.
- Partial provider failure is a normal outcome, not an exception. Results carry which providers were missing.
- Adapters emit RawOffer (strings as received); only ItineraryValidator turns it into a typed ProviderOffer. Never validate or normalise codes in an adapter.
- Dedup and cheapest selection happen in BestOfferSelector over the run read back ordered by signature. Never move that rule into SQL.
- timestamptz columns are bound with OffsetDateTime, never java.sql.Timestamp. The JsonCodec keeps offsets (ADJUST_DATES_TO_CONTEXT_TIME_ZONE off).
- The dashboard calls only relative /api and /actuator paths (nginx proxies them) and never the mock's admin endpoint; chaos is driven with curl.
- In the dashboard SSE is the source of run state; polling /api/ingestion/runs/latest is the fallback while the stream is down. Charts come from /api/itineraries/stats, never from the loaded pages.
- The dashboard renders times from the ISO string's wall clock and offset, never converted through Date.
- providers.*.timeout bounds a whole fetch attempt (Spring's JdkClientHttpRequestFactory applies the read timeout to the streamed body). Never set it below the time a full inventory takes; the connect timeout is what catches a dead host.

## Conventions
- REST under /api. Errors as RFC 9457 ProblemDetail.
- Migrations in backend/bootstrap/src/main/resources/db/migration as V{n}__{description}.sql. Never edit an applied migration, add a new one.
- Tests: domain/application use plain JUnit 5 + AssertJ. Adapters use WireMock or @WebMvcTest. bootstrap uses Testcontainers with @SpringBootTest. ArchitectureTest stays green.
- Config lives in application.yml with ${ENV:default} placeholders. docker-compose.yml sets the env.
- Mock provider data is a pure function of MOCK_SEED and the itinerary index; chaos lives only in ChaosFilter. Contracts: providers-mock/README.md.
- providers-mock carries no test suite by decision. It is tooling, verified manually with curl. Backend adapter tests use WireMock stubs of the documented contracts.

## Commands
- Full stack: docker compose up --build (dashboard :3000, api :8080, mocks :8081)
- Backend tests: cd backend && mvn verify (Docker must be running for Testcontainers)
- Pagination benchmark (compose DB, after ingestion): cd backend && mvn -pl bootstrap -am test -Dsurefire.excludedGroups= -Dgroups=benchmark -Dtest=PaginationBenchmark -Dsurefire.failIfNoSpecifiedTests=false
- Backend against compose infra: docker compose up -d postgres redis providers-mock && cd backend && mvn -pl bootstrap spring-boot:run
- Frontend dev: cd frontend && npm install && npm run dev
- Frontend tests: cd frontend && npm test (jsdom; Testing Library cleanup is registered in src/test/setup.ts because vitest globals are off)
- Mocks: cd providers-mock && mvn spring-boot:run

## Definition of done for any change
1. Unit tests for the rule, adapter or component that changed.
2. mvn verify green in backend, ArchitectureTest included.
3. docker compose up --build still boots and /actuator/health reports UP.
4. SOLUTION.md updated when a decision or trade-off changes. prompts.md updated with the prompt used.
