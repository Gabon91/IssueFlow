# Developer Skills — Spring Boot Reference Patterns

Recipes distilled from the IssueFlow build. Each pattern is the *expected* implementation; deviating requires a stated reason and an architectural decision recorded in `ARCHITECTURE.md`.

## 1. Testcontainers — Static Initializer pattern

Use a `static { … }` block (not `@DynamicPropertySource` callbacks alone) so the container is **up before** Spring reads the property values. This avoids the `Could not connect to Docker` race on Windows + Docker Desktop.

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class FeatureHappyPathIT {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app").withUsername("app").withPassword("app");

    static { POSTGRES.start(); }              // key line

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

Pair with `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` and the Testcontainers BOM pin in `pom.xml` (see `GUIDELINES.md §2`).

## 2. RFC-7807 error model

One record + one advice:

```java
public record ApiError(
    Instant timestamp, int status, String error, String message,
    String path, String traceId, Map<String,String> fieldErrors) {}
```

`@RestControllerAdvice` maps: domain `ResourceNotFoundException`→404, `IllegalStateTransitionException`→409, `BlockedByDependencyException`→422, `OptimisticLockingFailureException`→409, `MaxUploadSizeExceededException`→413, `UnsupportedMimeTypeException`→415, `MethodArgumentNotValidException`/`ConstraintViolationException`→400, `BadCredentialsException`→401, `AccessDeniedException`→403. `traceId` pulled from MDC (`trace_id` key populated by Micrometer Tracing).

## 3. AOP `@Audited`

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface Audited { String entity(); String action(); }
```

`AuditAspect` (`@Around`) resolves the post-call entity id from the return value or argument index, then writes an `AuditLog` row with `actor = SecurityContextHolder.getContext().getAuthentication().getName()` (or the literal `SYSTEM` for scheduled jobs). All mutations on aggregate roots carry this annotation.

## 4. JWT + Caffeine denylist

- HS256 via `jjwt`. Claims: `sub`, `iss`, `aud`, `exp`, `jti`.
- `JwtAuthenticationFilter` registered **before** `UsernamePasswordAuthenticationFilter` in `SecurityConfig`.
- `POST /auth/logout` puts `jti` into a Caffeine cache (`expireAfterWrite = token TTL`); the filter rejects denied `jti`s with 401.
- Passwords hashed with `BCryptPasswordEncoder(12)`.

## 5. MapStruct convention

One interface per aggregate. Ignore identity and version columns on `toEntity`; provide an `update(@MappingTarget …)` for PATCH-style updates.

```java
@Mapper(componentModel = "spring")
public interface TicketMapper {
    TicketResponse toResponse(Ticket entity);
    @Mapping(target = "id",      ignore = true)
    @Mapping(target = "version", ignore = true)
    Ticket toEntity(TicketCreateRequest req);
    void update(@MappingTarget Ticket target, TicketUpdateRequest req);
}
```

## 6. Flyway migrations

- Filename: `V{N}__snake_case_summary.sql`, monotonically increasing `N` per branch.
- **Existing migrations are immutable.** Schema changes always land as a new `V{N+1}__*.sql`.
- Repeatable migrations (`R__*.sql`) only for views / seed data — never for tables.

## 7. `@PreAuthorize` rules — driven by `x-roles`

Mirror the `x-roles` extension in `openapi.yaml`. Standard idioms:

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PreAuthorize("hasRole('ADMIN') or @ticketSecurity.isAssignee(#id, authentication)")
```

The `@…Security` beans live in `common/security/` and centralise the row-level checks. Method security is enabled via `@EnableMethodSecurity` in `SecurityConfig`.

## 8. Observability

- Actuator exposed: `health` (with `db` indicator + `liveness` / `readiness` groups), `info`, `prometheus`, `metrics`.
- Custom metrics named `{domain}.{event}.{outcome}` (`tickets.created`, `csv.import.failed`, `tickets.overdue`).
- `@Observed` on every multi-step service entry point (`create`, `update`, scheduled jobs).
- JSON logs via `logback-spring.xml` + `LogstashEncoder`. MDC keys: `trace_id`, `span_id`, `user`.

## 9. Smoke test script (`.smoke-final.ps1`)

Idempotent, randomises usernames/project names per run, covers (one section per ARCHITECTURE phase contract):

1. admin login → token
2. create developer + verify login
3. project + tickets
4. dependencies + DONE-while-blocked guard
5. workload reporting
6. comments + mentions inbox
7. attachments (upload, download, MIME guard)
8. CSV export + re-import
9. audit log + RBAC
10. user safeguards (self-delete, assigned-delete, soft-delete + restore)

Every section prints `PASS …` / `FAIL …` with relevant ids; exit code is non-zero on the first failure. Each new endpoint added in `openapi.yaml` requires a matching section here in the same slice.

## 10. `Clock` injection

```java
@Configuration
class ClockConfig { @Bean Clock systemClock() { return Clock.systemUTC(); } }
```

Services accept `Clock` via the constructor and use `Instant.now(clock)`. Tests substitute `Clock.fixed(Instant.parse(…), ZoneOffset.UTC)` for deterministic escalation / deadline behaviour. Pair with `@EnableScheduling` and `@Scheduled(fixedDelayString = "${app.escalation.interval}")` for the background jobs.

## 11. Failsafe wiring (pom.xml essentials)

```xml
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <includes><include>**/*IT.java</include></includes>
    <environmentVariables>
      <DOCKER_HOST>npipe:////./pipe/dockerDesktopLinuxEngine</DOCKER_HOST>
    </environmentVariables>
  </configuration>
  <executions>
    <execution><goals><goal>integration-test</goal><goal>verify</goal></goals></execution>
  </executions>
</plugin>
```

JaCoCo `check` execution enforces the 80 % service-line gate (`GUIDELINES.md §4`). Both plugins are bound to `verify`, so `mvnw.cmd clean verify` is the single command the agent runs at the end of every task.
