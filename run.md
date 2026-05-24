# Running IssueFlow

End-to-end setup, build, run, test and observability instructions for the IssueFlow backend.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (LTS) | Tested with Eclipse Temurin 21.0.11. |
| Docker | recent | Docker Desktop on Windows / macOS, or Docker Engine on Linux. Required for Postgres and the integration test. |
| Maven | (bundled) | Use the included `mvnw` / `mvnw.cmd` wrapper, no system install needed. |

## 1. Start the stack

```bash
docker compose up -d
```

`compose.yml` brings up six services on the `issueflow-net` bridge network:

| Service | Port(s) | Purpose |
|---|---|---|
| `db` (postgres:16) | `5432` | Application database (`issueflow`/`issueflow`/`issueflow`). |
| `issueflow` | `8080` | The Spring Boot app. Pulls the image set by `ISSUEFLOW_IMAGE`, defaulting to `issueflow:local` (produced by §2). |
| `otel-collector` | `4317` (OTLP gRPC), `4318` (OTLP HTTP), `8889` (Prometheus exporter) | Receives OTLP from the app; fans traces out to Jaeger and exposes metrics for Prometheus. |
| `jaeger` (all-in-one) | `16686` (UI), `4317` (OTLP) | Trace storage + UI. |
| `prometheus` | `9090` | Scrapes `issueflow:8080/actuator/prometheus` and `otel-collector:8889`. |
| `loki` | `3100` | Log storage (filesystem-backed, 7-day retention). |
| `promtail` | _internal_ | Tails Docker container logs via `/var/run/docker.sock` and ships them to Loki. |
| `grafana` | `3000` | Dashboards (`admin`/`admin`; anonymous Viewer enabled). |

If you only need Postgres for `./mvnw spring-boot:run` against the host JVM, you can start just the database with `docker compose up -d db`. Stop everything with `docker compose down`.

## 2. Build

```bash
./mvnw clean package -DskipTests          # macOS / Linux
mvnw.cmd clean package -DskipTests        # Windows
```

The build produces `target/issueflow-0.0.1-SNAPSHOT.jar`.

### Build the container image

The `spring-boot-maven-plugin` is configured (`pom.xml`) to produce an OCI image tagged `issueflow:local` via Paketo buildpacks — no `Dockerfile` is required.

```bash
./mvnw -DskipTests spring-boot:build-image            # macOS / Linux
mvnw.cmd -DskipTests spring-boot:build-image          # Windows
```

After the image exists locally, `docker compose up -d` will pick it up (the `issueflow` service defaults to `image: issueflow:local`). To run against a published image instead, set `ISSUEFLOW_IMAGE`, e.g. `ISSUEFLOW_IMAGE=ghcr.io/<owner>/issueflow:<tag> docker compose up -d`.

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

Logs are JSON (Logstash encoder) on stdout and include `traceId` / `spanId` from MDC. Business spans on `TicketService.create/update`, `EscalationService.escalate`, and `TicketCsvService.importFromCsv` are emitted via `@Observed`.

### Observability stack (Prometheus / Grafana / Jaeger / Loki)

When the app runs under `docker compose up -d` (§1), traces and metrics flow through the bundled OpenTelemetry collector and logs flow through Promtail → Loki. The wiring lives under `ops/`:

| File | Role |
|---|---|
| `ops/otel-collector-config.yaml` | Receives OTLP on `4317`/`4318`; exports traces to `jaeger:4317` and metrics on `:8889` for Prometheus scrape. |
| `ops/prometheus.yml` | Scrapes `issueflow:8080/actuator/prometheus` and the collector's `:8889` every 15 s. |
| `ops/loki-config.yaml` | Single-binary Loki, TSDB index + filesystem chunks under `/loki`, 7-day retention. |
| `ops/promtail-config.yaml` | Discovers containers via `docker_sd_configs`, peels the Docker JSON envelope, and JSON-parses the Logback payload for `issueflow` to surface `level`/`application` as labels. |
| `ops/grafana/provisioning/datasources/datasources.yaml` | Auto-provisions Prometheus (default, `uid=prometheus`), Jaeger (`uid=jaeger`), and Loki (`uid=loki`) datasources. The Loki datasource carries a derived field that turns `"traceId":"<hex>"` in any log line into a clickable link into Jaeger. |
| `ops/grafana/provisioning/dashboards/dashboards.yaml` | File-provider that loads JSONs from `ops/grafana/dashboards`. |
| `ops/grafana/dashboards/jvm-micrometer.json` | Grafana dashboard #4701 — JVM heap, GC, threads, CPU. |
| `ops/grafana/dashboards/spring-boot-statistics.json` | Grafana dashboard #6756 — Spring Boot HTTP / Tomcat / Hikari overview. |

The app is pre-configured (`application.yaml`) to send traces with 100 % sampling to `${OTEL_EXPORTER_OTLP_ENDPOINT:-http://otel-collector:4317}` and exposes `/actuator/prometheus` for the scrape job. The `opentelemetry-exporter-otlp` runtime dependency is what actually pushes spans over the wire — without it Micrometer Tracing drops them silently. Logs are emitted as Logback JSON (`src/main/resources/logback-spring.xml`) on stdout, with `traceId`/`spanId` lifted from MDC into top-level fields so Promtail can promote `level`/`application` to labels and Grafana can extract the `traceId` for trace correlation.

URLs once the stack is up:

| UI | URL | Notes |
|---|---|---|
| App | http://localhost:8080 | `/actuator/health`, `/actuator/prometheus`, Swagger at `/swagger-ui.html`. |
| Prometheus | http://localhost:9090 | `Status → Targets` should show `issueflow`, `otel-collector`, `prometheus` all `UP`. |
| Grafana | http://localhost:3000 | `admin` / `admin`; both dashboards under *Dashboards → Browse*. *Explore → Loki* queries logs (e.g. `{application="issueflow", level="WARN"}`). |
| Jaeger | http://localhost:16686 | Select service `issueflow` to see traces, e.g. for `POST /auth/login` or `POST /tickets`. |
| Loki | http://localhost:3100 | API only (`/ready`, `/loki/api/v1/labels`, `/loki/api/v1/query_range`); the UI is Grafana → Explore. |

## 8. CI/CD

GitHub Actions workflow `.github/workflows/ci-cd.yml` runs on every push and PR to `main` and `feature/**`:

1. **build-test** — Java 21, Maven cache, `./mvnw verify` (Surefire + Failsafe + JaCoCo). Uploads the coverage report as an artifact. Spotless runs in *ratchet* mode (`-Dspotless.ratchetFrom=origin/main`) so style is enforced only on the lines you actually changed.
2. **security-scan** — Trivy filesystem scan on the working tree, failing the job on `HIGH` / `CRITICAL` findings.
3. **docker-build-push** — Only on push to `main`. Builds the OCI image via `./mvnw spring-boot:build-image`, tags `latest` + the short SHA, and pushes to `ghcr.io/${github.repository_owner}/issueflow`.

## 9. Smoke test

After the app is up, exercise the full surface:

```powershell
powershell -File .smoke-final.ps1
```

The script logs in, walks each domain (users, projects, tickets, comments, dependencies, mentions, attachments, CSV import/export, audit logs) and prints a pass/fail summary.
