# Repository Guidelines

## Project Structure & Module Organization
This repository is a Spring Boot 4 monolith built with Maven and Java 21. Application code lives under `src/main/java/personal/monolithic`, organized by concern: `controller`, `service`, `dao`, `entity`, `config`, `security`, `dto`, `constants`, and `exception`. Runtime configuration is in `src/main/resources/application.yaml`. Database migrations are managed with Flyway in `src/main/resources/db/migration`. Tests live under `src/test/java/personal/monolithic`.

## Build, Test, and Development Commands
- `mvn compile`: compile the project and validate annotations, mappings, and generated code.
- `mvn test`: run the JUnit test suite.
- `mvn spring-boot:run`: start the application locally.
- `mvn clean verify`: full clean build with tests; use before opening a PR.

If Docker Compose is available, Spring Boot can start supporting services from `compose.yaml` during local runs.

## Coding Style & Naming Conventions
Use 4-space indentation and standard Java formatting. Keep package names lowercase. Class names use `PascalCase`; methods and fields use `camelCase`; constants use `UPPER_SNAKE_CASE`. Keep controllers thin, place business logic in services, and data access in DAOs. Name DTOs by purpose, for example `LoginRequest` and `CurrentUserResponse`. Preserve the existing annotation-driven style with Lombok, JPA, and Spring stereotypes.

## Testing Guidelines
Tests use JUnit 5 with Spring Boot Test and Testcontainers. Name test classes with the `*Tests` suffix or describe the target clearly, for example `MonolithicApplicationTests`. Prefer focused service or controller tests over large integration-only coverage. Run `mvn test` locally before submitting changes.

## Commit & Pull Request Guidelines
Git history is not available in this workspace, so follow a simple imperative style for commit messages, such as `Add refresh token rotation` or `Fix Swagger bearer auth wiring`. Keep commits scoped to one concern. PRs should include a short summary, impacted endpoints or tables, config or migration changes, and evidence of validation such as `mvn test` or `mvn compile`.

## Security & Configuration Tips
Do not commit real secrets in `application.yaml`. Keep JWT secrets, database credentials, and environment-specific values externalized for non-local environments. When adding schema changes, always add a new Flyway migration instead of editing an applied one.
