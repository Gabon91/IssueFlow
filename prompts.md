# AI-Assisted Development Log

## Tooling

| Tool | Role |
|---|---|
| **Claude Opus 4.7** | Base model. |
| **Augment Agent** | Agentic harness providing codebase retrieval, edit tooling, and process control. |
| Cursor / IntelliJ | Local editor and review surface. |

All code in this repository was written by a human reviewer guiding the Augment Agent through small, scoped slices. Every generated change was inspected and accepted before commit; the agent did not have autonomous push or merge permission.

## Workflow

Each phase from `ARCHITECTURE.md` was driven through a four-step loop:

1. **Plan** — restate the slice in the agent's task list, link the relevant `openapi.yaml` rows and `ARCHITECTURE.md` sections.
2. **Retrieve** — ask the agent to enumerate every symbol, JPA mapping, repository method, and DTO it would touch.
3. **Edit** — produce a minimal, conservative diff that respected existing conventions (DTO records, MapStruct mappers, service / controller pairs, package layout).
4. **Verify** — run `mvnw test`, then the `.smoke-final.ps1` and Swagger UI checks before marking the task done.

The agent's task list was the single source of truth for in-flight work, with every task explicitly transitioned to `[/]` then `[x]`.

## Representative prompts

The prompts below are paraphrased from the working transcripts; phrasing was iterated as gaps surfaced.

### Phase 1 — Persistence

> *"Implement the JPA entities in ARCHITECTURE.md §3 — User, Project, Ticket, Comment, Attachment, AuditLog, TicketDependency, CommentMention — with the foreign keys, enums, and indexes listed there. Use UUID-less `Long` ids per the spec, optimistic locking on Ticket, soft-delete columns where indicated, and matching Flyway migrations under `db/migration`."*

### Phase 2 — Core API & auth

> *"Wire stateless JWT auth: BCrypt password hashing, HS256 tokens via jjwt with issuer/audience claims, a `JwtAuthenticationFilter` ahead of `UsernamePasswordAuthenticationFilter`, and a Caffeine-backed `TokenDenyList` so `POST /auth/logout` actually invalidates the bearer. `@PreAuthorize` rules per the openapi.yaml `x-roles` extensions."*

> *"Build the Users / Projects / Tickets / Comments CRUD slices behind controllers that match the README API table exactly. Use Spring Data JPA, MapStruct DTO mappers, and centralised RFC-7807 errors via `@RestControllerAdvice` → `ApiError`."*

### Phase 3 — Advanced features

> *"Add ticket dependencies with cycle detection, project workload reporting from `UserRepository.findDevelopersOrderedByOpenLoad`, mentions inbox with pagination, multipart attachments with a 10 MB cap and MIME whitelist (`image/png`, `image/jpeg`, `application/pdf`, `text/plain`), CSV import/export via Apache Commons CSV, and an `@Audited` AOP aspect feeding the `audit_log` table."*

> *"Schedule overdue-ticket escalation with `@EnableScheduling`. Make the cadence configurable via `issueflow.escalation.interval` and write audit entries with `actor=SYSTEM`. Tests must use a `Clock` abstraction so they're deterministic."*

### Phase 4 — Cross-cutting concerns

> *"Wire actuator probes (`liveness`, `readiness` including `db`), expose Prometheus and standard metrics, add custom counters (`issueflow.tickets.created/auto_assigned/escalated`, `issueflow.csv.import.tickets`) and a `issueflow.tickets.overdue` gauge, structured JSON logs via Logstash encoder with `trace_id`/`span_id` from MDC, and `@Observed` spans on `TicketService.create/update`, `EscalationService.escalate`, `TicketCsvService.importFromCsv`."*

> *"Audit the validation surface: ensure every `*CreateRequest`/`*UpdateRequest` uses `@NotBlank` / `@Email` / `@Size` / `@Pattern` and every controller method uses `@Valid`. Confirm `GlobalExceptionHandler` covers `ResourceNotFoundException`, `IllegalStateTransitionException`, `OptimisticLockException`, `BlockedByDependencyException`, `MaxUploadSizeExceededException` → 413, `ConstraintViolationException` → 400, `BadCredentialsException` → 401, `AccessDeniedException` → 403."*

### Phase 5 — Quality & documentation

> *"Add `jacoco-maven-plugin` bound to the `test` phase, then report per-class line coverage on `service/`. We need to hit ≥80 % aggregate on the service layer."*

> *"Author one Testcontainers-backed `@SpringBootTest(RANDOM_PORT)` integration test (`ReadmeApiHappyPathIT`) that uses RestAssured to walk the README API table: login → create user → create project → create ticket → add comment with `@mention` → read ticket → list audit log → logout. Keep H2 for unit/slice tests; spin Postgres only for this single IT, behind a static initializer to dodge the `@DynamicPropertySource` ordering problem."*

> *"Write `run.md` and `prompts.md` per ARCHITECTURE.md §9."*

## What the agent was explicitly forbidden to do

- Add documentation files unless the user asked.
- Touch dependency versions without going through Maven (`mvnw -DartifactId=…`) or an approved BOM bump.
- Skip or weaken validation, security, or audit rules to make a test green.
- Generate test data via Flyway seed scripts in production migrations.
- Push or commit; every change landed via review.

## Lessons captured

- **Tight loops beat large prompts.** A 30-line slice with explicit acceptance criteria converged in one or two iterations; broad "implement Phase 3" prompts always required restarts.
- **Retrieval first, edit second.** Asking the agent to list affected symbols before producing a diff caught almost every downstream breakage in advance.
- **Tests are part of the slice.** Each prompt named the exact unit / slice test class to add, so coverage didn't drift between commits.
- **External services need version pins.** The Docker Engine compatibility fix (Testcontainers `1.21.4`) is documented in `pom.xml` and `run.md` so the next person doesn't rediscover the same `BadRequestException`.
