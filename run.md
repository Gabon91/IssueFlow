# Running IssueFlow

End-to-end setup, build, run, test and observability instructions for the IssueFlow backend.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (LTS) | Tested with Eclipse Temurin 21.0.11. |
| Docker | recent | Docker Desktop on Windows / macOS, or Docker Engine on Linux. Required for Postgres and the integration test. |
| Maven | (bundled) | Use the included `mvnw` / `mvnw.cmd` wrapper, no system install needed. |

## 1. Start Postgres

```bash
docker compose up -d
```

`compose.yml` brings up a single `postgres` service exposing `localhost:5432` with database / user / password all set to `issueflow`. Stop it later with `docker compose down`.

## 2. Build

```bash
./mvnw clean package -DskipTests          # macOS / Linux
mvnw.cmd clean package -DskipTests        # Windows
```

The build produces `target/issueflow-0.0.1-SNAPSHOT.jar`.

## 3. Run the application

```bash
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080`. Flyway applies migrations under `src/main/resources/db/migration` against the running Postgres, and `IssueFlowBootstrap` seeds the admin account on first boot.

### Default admin credentials

| Field | Value |
|---|---|
| username | `admin` |
| password | `admin12345` |

Change the password immediately in any non-local environment.

## 4. Configuration

Most configuration lives in `src/main/resources/application.yaml` and can be overridden via environment variables.

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/issueflow` | JDBC URL. |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `issueflow` | DB user. |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `issueflow` | DB password. |
| `issueflow.security.jwt.secret` | `ISSUEFLOW_JWT_SECRET` | `change-me-please-…` | HS256 signing key, ≥32 bytes in production. |
| `issueflow.security.jwt.expiration-seconds` | — | `3600` | Access-token TTL. |
| `issueflow.escalation.interval` | — | `PT5M` | Overdue-escalation scheduler period. |
| `spring.servlet.multipart.max-file-size` | — | `10MB` | Attachment upload cap. |

## 5. API documentation

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Static contract | [`openapi.yaml`](openapi.yaml) at the repo root |

Authenticate from Swagger UI by clicking *Authorize* and pasting the JWT returned by `POST /auth/login` as `Bearer <token>`.

## 6. Tests

```bash
./mvnw test                # 97 unit + slice tests on in-memory H2 (fast)
./mvnw verify              # adds 7 Testcontainers-backed integration tests
```

`verify` runs the failsafe-bound `ReadmeApiHappyPathIT`, which spins up a real `postgres:16-alpine` container and walks the README API table end to end: login → create user → create project → create ticket → comment with `@mention` → list audit log → logout.

### Windows / Docker Desktop note

Docker Desktop exposes its Linux engine on a non-standard named pipe. If Testcontainers cannot find Docker, set the env var:

```powershell
$env:DOCKER_HOST = "npipe:////./pipe/dockerDesktopLinuxEngine"
```

The pom is already wired to pass `DOCKER_HOST` through to the failsafe JVM.

### Coverage

```bash
./mvnw test                # writes target/site/jacoco/jacoco.csv + index.html
powershell -File .coverage-all.ps1
```

The helper prints per-class and per-package coverage and an aggregate for service-layer classes. Current service-layer line coverage is **82 %**, meeting the ≥80 % target from `ARCHITECTURE.md §9`.

## 7. Observability

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Aggregate health. |
| `GET /actuator/health/liveness` | Kubernetes liveness probe. |
| `GET /actuator/health/readiness` | Kubernetes readiness probe (includes `db`). |
| `GET /actuator/info` | Build / app metadata. |
| `GET /actuator/metrics` | Index of metric names. |
| `GET /actuator/metrics/issueflow.tickets.created` | Custom counter example. |
| `GET /actuator/prometheus` | Prometheus scrape endpoint. |

Logs are JSON (Logstash encoder) on stdout and include `trace_id` / `span_id` from MDC. Business spans on `TicketService.create/update`, `EscalationService.escalate`, and `TicketCsvService.importFromCsv` are emitted via `@Observed`.

## 8. Smoke test

After the app is up, exercise the full surface:

```powershell
powershell -File .smoke-final.ps1
```

The script logs in, walks each domain (users, projects, tickets, comments, dependencies, mentions, attachments, CSV import/export, audit logs) and prints a pass/fail summary.
