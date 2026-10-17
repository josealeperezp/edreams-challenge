# Flight Aggregation & Cache Optimization System

OTA flight aggregator. It ingests itineraries from three mock GDS providers
(Alpha returns JSON lists, Beta nested XML, Gamma CSV streams), validates and
deduplicates them, applies the OTA markup, stores the result in PostgreSQL and
serves a keyset-paginated search API with a React dashboard.

> Status: scaffold. The whole stack boots end to end; business logic lands
> slice by slice. Design and trade-offs live in [SOLUTION.md](SOLUTION.md).

## Run the full stack

```bash
docker compose up --build
```

| Service          | URL                                                                  |
|------------------|----------------------------------------------------------------------|
| Dashboard        | http://localhost:3000                                                |
| Backend API      | http://localhost:8080/api/system/info                                |
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

# frontend with hot reload, proxies /api and /actuator to :8080
cd frontend && npm install && npm run dev

# mock providers on :8081
cd providers-mock && mvn spring-boot:run
```
