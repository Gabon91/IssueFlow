# Project Guidelines

> Source of truth for agent behaviour on this codebase. Read this **before** any task. Apply the same rules verbatim when this file is copied into a new Spring Boot project.

## 1. Source of truth

| Artifact | Role |
|---|---|
| `openapi.yaml` | Wire contract. Endpoints, request/response shapes, status codes, RBAC via `x-roles`. |
| `ARCHITECTURE.md` | Phases, module layout, ADRs, technology constraints. |
| `db/migration/V*__*.sql` | Schema reality. Existing files are immutable. |
| `README.md` | Public-facing API table used as the integration-test contract. |

Before any implementation step the agent **must** retrieve and consult `openapi.yaml` + `ARCHITECTURE.md`, then list every JPA entity, repository, service, DTO, mapper, migration, and security rule the slice will touch. No edit lands without that retrieval step.

## 2. Environment (Windows + Docker Desktop)

- **JDK 21** (`Eclipse Adoptium jdk-21.x-hotspot`). `JAVA_HOME` set in every build script.
- **Maven**: only the wrapper (`mvnw.cmd`) — never a global `mvn`.
- **Docker Desktop**: `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` is required for Testcontainers; forward it on every `verify` invocation.
- **Testcontainers**: pin `org.testcontainers:testcontainers-bom` to **1.21.4** (or the latest tested release recorded in `pom.xml`). Older versions return `BadRequestException` against Docker Engine ≥ 29.x.
- **Postgres**: shared dev instance from `compose.yml` on `localhost:5432`. Integration tests still spin their own ephemeral container.
- **Bootstrap script convention**: every repo carries `.run-app.cmd`, `.wait-up.ps1`, `.smoke-final.ps1`, `.coverage-report.ps1` at the root.

## 3. Coding standards

- **Java 21 records** for every DTO (`*CreateRequest`, `*UpdateRequest`, `*Response`, `*Entry`, `*Page`).
- **MapStruct** for entity ↔ DTO mapping; one `*Mapper` interface per aggregate.
- **RFC-7807** for every error response via one `@RestControllerAdvice` → `ApiError` carrying `timestamp/status/error/message/path/traceId/fieldErrors`.
- **AOP `@Audited`** on every state-changing service method; `AuditAspect` writes to `audit_log`.
- **`@PreAuthorize`** on every controller method, derived from `x-roles` in `openapi.yaml`.
- **Bean Validation** (`@NotBlank`, `@Email`, `@Size`, `@Pattern`) on every `*Request` field; `@Valid` on every controller parameter.
- **Optimistic locking** (`@Version`) on aggregates that mutate from multiple sources.
- **Soft delete** (`deleted_at`, `deleted_by`) where the architecture calls for it — never physical deletion.
- **`Clock` bean injection** — services never call `Instant.now()` / `LocalDateTime.now()` directly.
- **Package layout**: `{aggregate}/{Controller,Service,Repository,dto/}`, entity in the aggregate root or `domain/`.

## 4. Testing & quality

- **JaCoCo** line coverage on `service/` packages: **≥ 80 %**. The build fails the threshold check at `verify`.
- **Per slice**: one Mockito service test + one `@WebMvcTest` controller test.
- **Per feature**: one happy-path `*IT` under `src/test/java/.../it/`, executed by Maven Failsafe.
- **Testcontainers**: use the **Static Initializer** pattern from `DEVELOPER_SKILLS.md §1`. Never rely on `@DynamicPropertySource` callbacks to start the container.
- **H2** is allowed for slice/unit tests; Postgres-via-Testcontainers is mandatory for `*IT`.

## 5. Workflow (executed for every task)

1. **Plan.** Add the task to the task list with explicit acceptance criteria; transition to `[/]`.
2. **Retrieve.** List every entity, repository, service, DTO, mapper, migration, and security rule the slice touches.
3. **Edit.** Conservative diffs that respect existing conventions; one slice per task.
4. **Verify.** `mvnw.cmd clean verify` (104+ tests stay green, coverage gate green); if the app is up, run `.smoke-final.ps1`.
5. **Transition.** Mark the task `[x]` only after step 4 passes.

Phase shortcut: when the user says **"Implement Phase N"**, the agent reads the matching § in `ARCHITECTURE.md`, cross-references `openapi.yaml`, generates the task list for the slices in that phase, then executes, verifies, and reports — no per-slice confirmation.

## 6. Communication rules

- **Do not** ask for confirmation on CRUD slices that are fully specified in `openapi.yaml`. Implement, then report.
- **Do** ask before: introducing a new dependency, modifying an existing migration, weakening security or validation, or skipping the coverage gate.
- **Proactively** create the Flyway migration and `@PreAuthorize` annotation that the slice needs.
- **Never** create documentation files (`*.md`) unless explicitly requested. `README.md`, `ARCHITECTURE.md`, `openapi.yaml`, `run.md`, `prompts.md`, `GUIDELINES.md`, `DEVELOPER_SKILLS.md` are the only narrative files maintained.
- **Never** commit, push, merge, or change ticket status without explicit instruction.
- **Never** echo secrets (JWT, BCrypt hash, DB password, API keys) into command lines, commit messages, or logs. If a check needs a secret, read it from `.env` or `application.yaml` via Spring config — never inline it.

## 7. Dependency management

- Use Maven for **every** dependency change: `mvnw.cmd dependency:add` / explicit `<dependency>` edit only when no equivalent command exists.
- Pin versions through a BOM (`spring-boot-dependencies`, `testcontainers-bom`, `micrometer-bom`).
- Removing a dependency requires checking with the codebase-retrieval step that no live import references it.

## 8. Verification artefacts the agent owns

| File | Purpose |
|---|---|
| `.run-app.cmd` | Set `JAVA_HOME` + start spring-boot with logs into `app.log`. |
| `.wait-up.ps1` | Poll `/actuator/health` until 200. |
| `.smoke-final.ps1` | End-to-end happy + safeguard checks against the running app. |
| `.coverage-all.ps1` / `.coverage-report.ps1` | Run JaCoCo, print per-class line coverage, fail under threshold. |

These files are checked into the repo and updated whenever an endpoint is added — keeping the smoke script in step with the API table is part of the slice.
