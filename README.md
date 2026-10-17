# Flight Aggregation & Cache Optimization System

OTA flight aggregator. It ingests itineraries from three mock GDS providers
(Alpha returns JSON lists, Beta nested XML, Gamma CSV streams), validates and
deduplicates them, applies the OTA markup, stores the result in PostgreSQL and
serves a keyset-paginated search API with a React dashboard.

> Status: complete. Backend (ingestion, deduplication, pricing, keyset search,
> cache, SSE) and dashboard (search and filters, charts over the filtered
> inventory, live run and provider panels). Design and trade-offs live in
> [SOLUTION.md](SOLUTION.md).

## Run the full stack

```bash
docker compose up --build
```

| Service          | URL                                                                  |
|------------------|----------------------------------------------------------------------|
| Dashboard        | http://localhost:3000                                                |
| Search API       | http://localhost:8080/api/itineraries?origin=MAD&destination=BCN&maxPrice=150&carrier=IB&limit=20 |
| Search stats     | http://localhost:8080/api/itineraries/stats?origin=MAD&destination=BCN      |
| Ingestion        | `POST /api/ingestion/runs`, `GET /api/ingestion/runs/latest`, `GET /api/ingestion/events` (SSE) |
| Provider status  | http://localhost:8080/api/providers/status                           |
| Backend info     | http://localhost:8080/api/system/info                                |
| Backend health   | http://localhost:8080/actuator/health                                |
| Mock providers   | http://localhost:8081/alpha/flights, `/beta/flights`, `/gamma/flights` |
| Mock metadata    | http://localhost:8081/alpha/meta, `/beta/meta`, `/gamma/meta`          |
| Mock chaos admin | http://localhost:8081/admin/chaos, see [providers-mock/README.md](providers-mock/README.md) |
| PostgreSQL       | `localhost:15432`, database/user/password `flights`                  |
| Redis            | `localhost:16379`                                                    |

Host ports for PostgreSQL and Redis are non-default on purpose so the stack
boots next to a local database.

## Repository layout

```
backend/            Spring Boot 4 / Java 25, Maven multi-module (hexagonal)
  domain/           pure Java domain model and rules, no frameworks
  application/      use cases and ports, JDK + domain only
  infrastructure/   adapters: provider clients, persistence, cache, web
  bootstrap/        Spring Boot app, config, Flyway migrations, arch + integration tests
providers-mock/     standalone Spring Boot app simulating the 3 GDS providers (tooling, no tests)
frontend/           React + TypeScript + Vite dashboard, served by nginx
docs/ai/            how AI tools were used, prompt log
```

## Local development

Requirements: JDK 25, Maven 3.9+, Node 24, Docker. With sdkman and nvm:
`sdk use java 25.0.1-amzn` and `nvm use` inside `frontend/` (reads `.nvmrc`).

```bash
# infrastructure only, then run the backend from Maven
docker compose up -d postgres redis providers-mock
cd backend && mvn -pl bootstrap spring-boot:run

# backend tests (Testcontainers needs a running Docker daemon)
cd backend && mvn verify

# pagination benchmark against the compose database, after an ingestion
cd backend && mvn -pl bootstrap -am test -Dsurefire.excludedGroups= -Dgroups=benchmark -Dtest=PaginationBenchmark -Dsurefire.failIfNoSpecifiedTests=false

# frontend with hot reload, proxies /api and /actuator to :8080
cd frontend && npm install && npm run dev

# mock providers on :8081
cd providers-mock && mvn spring-boot:run
```

## Demo script

1. `docker compose up --build` and open http://localhost:3000. The boot run
   appears live in the Ingestion panel: run started, each provider as it
   finishes with its latency and counts, run completed. Results and charts
   fill in once it completes.
2. Search `MAD` to `BCN`, then add a max price or a carrier. The headline
   counts the whole match, the charts describe it, the table lists it by
   price with every provider's quote; "Load more" follows the keyset cursor
   shown in the footer. The filters are in the URL.
3. Break a provider and watch the system degrade instead of fail:

   ```bash
   curl -s -X PUT localhost:8081/admin/chaos/beta -H 'content-type: application/json' \
        -d '{"enabled":true,"latencyMinMs":0,"latencyMaxMs":0,"errorRate":1}'
   ```

   Click "Run ingestion now". Beta fails after its retries, its breaker turns
   OPEN in the Providers panel, the run ends PARTIAL, search keeps answering,
   and rows that only Beta confirmed earlier are marked stale.
4. Reset Beta with `curl -s -X DELETE localhost:8081/admin/chaos/beta`, wait
   for the breaker to show HALF OPEN (30 s), run again: COMPLETED, breaker
   CLOSED, stale markers gone.
