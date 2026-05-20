# Project Guidelines

## Project Structure & Module Organization

This repository is a Spring Boot 4 monolith built with Maven and Java 21. Application code lives under `src/main/java/personal/monolithic`, organized by concern: `controller`, `service`, `dao`, `entity`, `config`, `security`, `dto`, `constants`, and `exception`. Runtime configuration is in `src/main/resources/application.yaml`. Database migrations are managed with Flyway in `src/main/resources/db/migration`. Tests live under `src/test/java/personal/monolithic`.

## Build & Run Commands

```bash
mvn compile                  # Compile and validate annotations/mappings
mvn test                     # Run full test suite (requires Docker for Testcontainers)
mvn spring-boot:run          # Start the app (auto-starts Docker Compose services)
mvn clean verify             # Full clean build + tests; use before opening a PR
```

**Run a single test class:**
```bash
mvn test -Dtest=MonolithicApplicationTests
```

The app starts on **port 81** (not 8080). Swagger UI: `http://localhost:81/swagger-ui.html`.

Docker Compose (`compose.yaml`) auto-starts PostgreSQL and Redis when running locally via Spring Boot's Docker Compose integration.

## Architecture

Layered monolith: `controller` → `service` → `dao` → `entity`. Business logic belongs in services, data access in DAOs, controllers stay thin.

**Security flow:**
- Stateless JWT (HS256, self-signed secret via `security.jwt.secret`).
- `JwtService` issues access tokens (`sub` = username, `uid` = user UUID) and rotates refresh tokens stored in PostgreSQL.
- On every authenticated request, `SecurityConfig.jwtAuthoritiesConverter()` loads the user's roles and permissions from the DB and constructs authority strings in the format `ROLENAME_PERMISSION1PERMISSION2` (permissions sorted alphabetically, e.g. `ADMINISTRATOR_READWRITE`).
- `@EnableMethodSecurity` is active — use `@PreAuthorize` for endpoint-level access control.

**Public endpoints** (no JWT required): `/sign-in`, `/refresh-token`, `/health-check`, Swagger UI/docs, `/error`.

**Correlation ID:** Every request gets an `X-Correlation-Id` header (generated if absent). It is stored in both SLF4J MDC and Log4j2 `ThreadContext` and echoed in the response header. Include it when tracing logs.

**Error handling:** `GlobalHandleController` (`@RestControllerAdvice`) maps domain exceptions to RFC 9457 `ProblemDetail` responses. Add new exception types there — do not throw raw `ResponseEntity` from services.

## Coding Style & Naming Conventions

Use 4-space indentation and standard Java formatting. Keep package names lowercase. Class names use `PascalCase`; methods and fields use `camelCase`; constants use `UPPER_SNAKE_CASE`. Keep controllers thin, place business logic in services, and data access in DAOs. Name DTOs by purpose, for example `LoginRequest` and `CurrentUserResponse`. Preserve the existing annotation-driven style with Lombok, JPA, and Spring stereotypes.

## Key Conventions

### Soft Delete
All entities use soft delete via Hibernate annotations:
```java
@SQLDelete(sql = "UPDATE T_... SET STATE = 'DELETED' WHERE ID = ?")
@SQLRestriction("state = 'ACTIVE'")
```
Never issue raw `DELETE` SQL — call the JPA `delete()` method so the `@SQLDelete` hook fires.

### Base Entity
All entities extend `BaseEntity`, which carries the `STATE` column (`EntityState.ACTIVE` / `EntityState.DELETE`). Use `@SuperBuilder` on subclasses.

### Table & Column Naming
Tables and columns use `UPPER_SNAKE_CASE`. All column name constants live in `CommonColumns`. Always reference constants instead of bare strings (e.g., `COL_ID`, `COL_STATE`).

### Named Queries
Complex JPQL queries are declared as `@NamedQuery` on the entity class and referenced by the constant name (e.g., `User.NQ_FIND_BY_USR_OR_EMAIL`). Prefer named queries over inline JPQL in DAOs for readability.

### MapStruct
All mappers are Spring-managed beans (`-Amapstruct.defaultComponentModel=spring` is set in the compiler plugin). Inject mappers with `@RequiredArgsConstructor` like any other Spring bean.

### Logging
Uses **Log4j2** (Logback is explicitly excluded from all starters). Use `@Slf4j` from Lombok. Log structured key=value pairs, e.g. `log.info("Sign-in succeeded username={}", user.getUsr())`.

### DTOs
Named by purpose as Java `record`s (e.g., `LoginRequest`, `TokenResponse`). Group by feature in sub-packages under `dto/` (e.g., `dto/auth/`, `dto/user/`).

### Database Migrations
Schema is managed exclusively by Flyway (`ddl-auto: none`). Never edit an applied migration. New migrations follow the naming pattern `V<major>_<minor>_<patch>__<description>.sql` (e.g., `V1_0_2__add_column.sql`).

### JWT Secret Constraint
`security.jwt.secret` must be **at least 32 bytes** (enforced in `JwtService.buildKey()`). The value in `application.yaml` is a local-only placeholder — externalize for any non-local environment.

## Testing Guidelines

Tests use JUnit 5 with Spring Boot Test and Testcontainers. `TestcontainersConfiguration` provides `@ServiceConnection` beans for PostgreSQL and Redis — import it in tests that need the full stack. Test classes use the `*Tests` suffix. Prefer focused service or controller tests over large integration-only coverage. Run `mvn test` locally before submitting changes.

## Commit & Pull Request Guidelines

Use simple imperative commit messages, e.g. `Add refresh token rotation` or `Fix Swagger bearer auth wiring`. Keep commits scoped to one concern. PRs should include a short summary, impacted endpoints or tables, config or migration changes, and evidence of validation such as `mvn test` or `mvn compile`.

## Security & Configuration Tips

Do not commit real secrets in `application.yaml`. Keep JWT secrets, database credentials, and environment-specific values externalized for non-local environments. When adding schema changes, always add a new Flyway migration instead of editing an applied one.
