# Docker — Maintenance & Operations Guide

## Overview

The application runs as a three-service stack:

| Service    | Image                              | Host Port | Container Port |
|------------|------------------------------------|-----------|----------------|
| `postgres`  | `postgres:latest`                  | 5432      | 5432           |
| `redis`     | `redis:latest`                     | 6379      | 6379           |
| `app`       | built from `Dockerfile.dev`        | 81        | 8080 (Tomcat)  |
| *(debug)*   | *(same app container)*             | 5005      | 5005           |

Start order is enforced by `depends_on` with `service_healthy` conditions:
`postgres` and `redis` must pass their health checks before `app` starts.

---

## Repository File Map

```
first-project/
├── Dockerfile                                  # Production: multi-stage (Maven → Tomcat), bakes ROOT.war
├── Dockerfile.dev                              # Dev: Tomcat only, no WAR baked in — hot-deploy via volume
├── compose.yaml                                # Full-stack Docker Compose (all 3 services, dev hot-deploy)
├── .dockerignore                               # Excludes .git, .idea, target, *.md from build context
├── pom.xml                                     # WAR packaging; `docker` Maven profile — triggers compose up
└── src/main/resources/
    ├── application.yaml                        # All env vars have ${VAR:default} fallbacks
    └── db/
        └── init/
            └── init-flyway-user.sql            # Runs once on first postgres startup (creates DB users)
        └── migration/
            ├── V1_0_0__initialize.sql          # Flyway baseline schema
            └── V1_0_1__refresh_tokens.sql      # Flyway incremental migration
```

---

## Dockerfile — Production Multi-Stage Build

The `Dockerfile` has two stages so the final image contains only Tomcat + the WAR, not Maven or the JDK.

### Stage 1 — `builder` (`maven:3.9-eclipse-temurin-21`)
1. Copies `pom.xml` and runs `mvn dependency:go-offline` → cached as a separate layer.
2. Copies `src/` and runs `mvn package -DskipTests -q` → produces `target/ROOT.war`.

### Stage 2 — `runtime` (`tomcat:10.1-jre21-temurin`)
1. Removes bundled Tomcat sample apps (`webapps/*`).
2. Creates a non-root system user `appuser:appgroup` and chowns the Tomcat installation.
3. Copies `ROOT.war` from the builder stage to `/usr/local/tomcat/webapps/ROOT.war`.
4. Exposes ports `8080` (HTTP) and `5005` (JDWP debug).
5. Tomcat's default `CMD ["catalina.sh", "run"]` starts the server — no custom ENTRYPOINT needed.

> **Note:** `JAVA_TOOL_OPTIONS` is set to `""` in the image. `compose.yaml` overrides it to
> enable JDWP at runtime. This keeps the image usable standalone without a debugger.

> **Port note:** `server.port=81` in `application.yaml` only affects the embedded Tomcat used
> by `mvn spring-boot:run`. On an external Tomcat container, Tomcat listens on port 8080 by
> default. The host port 81 is set via the `compose.yaml` port mapping (`81:8080`).

---

## Dockerfile.dev — Development Hot-Deploy Image

`Dockerfile.dev` is intentionally minimal: it only sets up the Tomcat base image with no WAR baked in.

- Base image: `tomcat:10.1-jre21-temurin`
- Removes default sample apps from `webapps/`
- No custom entrypoint — relies on Tomcat's built-in `autoDeploy` mechanism
- The WAR is provided at runtime via a Docker volume mount (see *Hot-Deploy Workflow* below)

---

## compose.yaml — Service Definitions

### postgres

```yaml
image: postgres:latest
environment:
  POSTGRES_DB:       monolithic
  POSTGRES_PASSWORD: admin        # superuser password (local only)
  POSTGRES_USER:     sa           # superuser (local only)
ports:
  - '5432:5432'
volumes:
  - './src/main/resources/db/init:/docker-entrypoint-initdb.d'
```

The `docker-entrypoint-initdb.d` volume mount makes PostgreSQL execute
`init-flyway-user.sql` **once**, on the very first container start (when the data
directory is empty). After that the file is ignored even if the container restarts.

**init-flyway-user.sql** creates two application-level users:

| User     | Password       | Privileges                               | Purpose          |
|----------|----------------|------------------------------------------|------------------|
| `flyway`  | `flyway_admin`  | `CONNECT`, `USAGE`, `CREATE` on `public` | DDL (schema mgmt)|
| `nene`    | `475963218`     | `SELECT/INSERT/UPDATE/DELETE` on tables  | DML (app runtime)|

Default privileges are set so any table/sequence `flyway` creates in the future is
automatically accessible to `nene` — no manual grants needed after migrations.

### redis

```yaml
image: redis:latest
ports:
  - '6379:6379'
```

No persistence configuration; data is lost when the container is removed.
For persistent sessions, add a named volume and `--appendonly yes` to the command.

### app

```yaml
build:
  context: .
  dockerfile: Dockerfile.dev
ports:
  - '81:8080'   # Tomcat HTTP (8080) exposed as 81 on host
  - '5005:5005' # JDWP remote debug
volumes:
  - ./target:/usr/local/tomcat/webapps  # hot-deploy: WAR dropped here → Tomcat auto-redeploys
```

---

## Environment Variables

All variables consumed by `application.yaml` (`${VAR:default}` syntax).
The `compose.yaml` overrides the defaults so services resolve each other by
Docker network hostname instead of `localhost`.

| Variable                                    | compose.yaml value                            | application.yaml default         |
|---------------------------------------------|-----------------------------------------------|----------------------------------|
| `SPRING_DATASOURCE_URL`                     | `jdbc:postgresql://postgres:5432/monolithic`  | `localhost:5432`                 |
| `SPRING_DATASOURCE_USERNAME`                | `nene`                                        | *(no default — required)*        |
| `SPRING_DATASOURCE_PASSWORD`                | `475963218`                                   | *(no default — required)*        |
| `SPRING_FLYWAY_URL`                         | `jdbc:postgresql://postgres:5432/monolithic`  | `localhost:5432`                 |
| `SPRING_FLYWAY_USER`                        | `flyway`                                      | *(no default — required)*        |
| `SPRING_FLYWAY_PASSWORD`                    | `flyway_admin`                                | *(no default — required)*        |
| `SPRING_DATA_REDIS_HOST`                    | `redis`                                       | `localhost`                      |
| `SPRING_DATA_REDIS_PORT`                    | `6379`                                        | `6379`                           |
| `JAVA_TOOL_OPTIONS`                         | `-agentlib:jdwp=...address=*:5005`            | `""` (debug disabled)            |

> **⚠ Security:** `SPRING_DATASOURCE_PASSWORD`, `SPRING_FLYWAY_PASSWORD`, and
> `SECURITY_JWT_SECRET` in `compose.yaml` are **local-only placeholders**.
> Externalize all secrets (Docker secrets, Vault, env injection) for any
> non-local environment.

---

## Maven `docker` Profile

Defined in `pom.xml`, the `docker` profile uses `exec-maven-plugin` to run
`docker compose up` during the **`verify`** phase:

```xml
<execution>
  <phase>verify</phase>
  <goal>exec</goal>
  <!-- runs: docker compose -f compose.yaml up --build --no-recreate -d -->
</execution>
```

Flags used:
- `--build` — always rebuilds the `app` image from `Dockerfile.dev`
- `--no-recreate` — does not restart postgres/redis if they are already running
- `-d` — detached mode

---

## Common Commands

### First-time start (via Maven profile — recommended)

```bash
# Build WAR + bring up all containers
mvn verify -P docker -DskipTests
```

### Hot-deploy (day-to-day development)

```bash
# Rebuild WAR and let Tomcat auto-redeploy (~10 seconds, no docker command needed)
mvn package -DskipTests
```

### Start / Stop (direct Docker Compose)

```bash
# Full rebuild of dev image + start all services
docker compose -f compose.yaml up --build

# Subsequent starts (no rebuild)
docker compose -f compose.yaml up -d

# Stop containers, keep DB volume
docker compose -f compose.yaml down

# Stop + wipe DB volume (full reset — re-runs init-flyway-user.sql on next start)
docker compose -f compose.yaml down -v

# Restart only the app (infra stays running)
docker compose -f compose.yaml restart app
```

### Logs

```bash
docker compose -f compose.yaml logs -f           # all services
docker compose -f compose.yaml logs -f app       # app only (Tomcat output)
docker compose -f compose.yaml logs -f postgres  # postgres only
```

### Check health / status

```bash
docker compose -f compose.yaml ps
```

### Connect to PostgreSQL inside the container

```bash
docker exec -it <postgres-container-name> psql -U sa -d monolithic
```

---

## Hot-Deploy Workflow

```
mvn package -DskipTests
      │
      ▼
target/ROOT.war updated on host filesystem
      │
      ▼  (Docker volume mount: ./target → /usr/local/tomcat/webapps)
ROOT.war visible inside the running Tomcat container
      │
      ▼  (Tomcat autoDeploy=true, checks every ~10s)
Tomcat undeploys old context → deploys new WAR
      │
      ▼
Changes live — no docker command, no JVM restart
```

**Key points:**
- Tomcat's `autoDeploy` is enabled by default in its `server.xml`
- The check interval (`backgroundProcessorDelay`) defaults to **10 seconds**
- Context path is `/` because the WAR is named `ROOT.war`
- All endpoints remain at the same URLs (e.g. `/sign-in`, `/swagger-ui.html`)
- Remote debug (port 5005) stays connected across redeploys

---

## Remote Debugging

Port `5005` is exposed and `JAVA_TOOL_OPTIONS` in `compose.yaml` enables JDWP:

```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

- `suspend=n` — Tomcat starts immediately; it does not wait for a debugger
- Connect your IDE (IntelliJ / VS Code) to `localhost:5005` using a
  *Remote JVM Debug* run configuration
- The debug connection survives WAR redeploys (same JVM process)

---

## .dockerignore

Excludes the following from the Docker build context (keeps image builds fast
and prevents secrets/IDE files from leaking into the image):

```
.git
.idea
*.md
formatter.xml
target
```

> The `target/` directory is excluded from the **production** `Dockerfile` build
> context because the multi-stage build compiles source inside Docker itself.
> For dev (`Dockerfile.dev`), `target/` is provided via a runtime volume mount —
> not the build context — so this exclusion does not affect hot-deploy.

---

## First-Time Setup Checklist

1. Install **Docker Desktop** (or Docker Engine + Compose plugin).
2. Clone the repository.
3. Run `mvn verify -P docker -DskipTests` from the project root.
4. Wait for the `app` container to become healthy (check with `docker compose -f compose.yaml ps`).
5. Open **http://localhost:81/swagger-ui.html**.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `app` fails to start — DB connection refused | `postgres` health check not passed yet | Wait and check `docker compose logs postgres` |
| `app` fails — `flyway` migration error | `init-flyway-user.sql` did not run (volume already existed) | Run `docker compose down -v` then restart |
| Port 5432 / 6379 / 81 already in use | Another local process bound the port | Stop the conflicting process or change the host port in `compose.yaml` |
| Changes not reflected after `mvn package` | Tomcat hasn't redeployed yet | Wait ~10s; check `docker compose logs -f app` for `Deploying web application` |
| WAR not deploying — Tomcat reports parse error | Broken build (compilation error in new code) | Fix the error and re-run `mvn package -DskipTests` |
| `app` image rebuild needed | `Dockerfile.dev` itself changed | Run `docker compose -f compose.yaml up --build app` |
| Expired JWT causes 401 on public endpoints | `BearerTokenAuthenticationFilter` runs before `permitAll` check | Fixed in `SecurityConfig` via `publicPathAwareBearerTokenResolver` — see `config/SecurityConfig.java` |

## Overview

The application runs as a three-service stack:

| Service    | Image                          | Host Port | Container Port |
|------------|--------------------------------|-----------|----------------|
| `postgres`  | `postgres:latest`              | 5432      | 5432           |
| `redis`     | `redis:latest`                 | 6379      | 6379           |
| `app`       | built from `Dockerfile`        | 81        | 81             |
| *(debug)*   | *(same app container)*         | 5005      | 5005           |

Start order is enforced by `depends_on` with `service_healthy` conditions:
`postgres` and `redis` must pass their health checks before `app` starts.

---

## Repository File Map

```
first-project/
├── Dockerfile                                  # Multi-stage image build (Maven → JRE)
├── compose.yaml                                # Full-stack Docker Compose (all 3 services)
├── .dockerignore                               # Excludes .git, .idea, target, *.md from build context
├── pom.xml                                     # `docker` Maven profile — triggers compose up at verify phase
└── src/main/resources/
    ├── application.yaml                        # All env vars have ${VAR:default} fallbacks
    └── db/
        └── init/
            └── init-flyway-user.sql            # Runs once on first postgres startup (creates DB users)
        └── migration/
            ├── V1_0_0__initialize.sql          # Flyway baseline schema
            └── V1_0_1__refresh_tokens.sql      # Flyway incremental migration
```

---

## Dockerfile — Multi-Stage Build

The `Dockerfile` has two stages so the final image contains only a JRE, not Maven or the JDK.

### Stage 1 — `builder` (`maven:3.9-eclipse-temurin-21`)
1. Copies `pom.xml` and runs `mvn dependency:go-offline` → cached as a separate layer.
   Re-downloading dependencies only happens when `pom.xml` changes.
2. Copies `src/` and runs `mvn package -DskipTests -q` → produces `target/monolithic-0.0.1-SNAPSHOT.jar`.

### Stage 2 — `runtime` (`eclipse-temurin:21-jre-jammy`)
1. Creates a non-root system user `appuser:appgroup`.
2. Copies the JAR from the builder stage to `/app/app.jar`.
3. Exposes ports `81` (HTTP) and `5005` (JDWP debug).
4. `ENTRYPOINT ["java", "-jar", "app.jar"]`

> **Note:** `JAVA_TOOL_OPTIONS` is set to `""` in the image. `compose.yaml` overrides it to
> enable JDWP at runtime (see *Environment Variables* below). This keeps the image usable
> standalone without a debugger attached.

---

## compose.yaml — Service Definitions

### postgres

```yaml
image: postgres:latest
environment:
  POSTGRES_DB:       monolithic
  POSTGRES_PASSWORD: admin        # superuser password (local only)
  POSTGRES_USER:     sa           # superuser (local only)
ports:
  - '5432:5432'
volumes:
  - './src/main/resources/db/init:/docker-entrypoint-initdb.d'
```

The `docker-entrypoint-initdb.d` volume mount makes PostgreSQL execute
`init-flyway-user.sql` **once**, on the very first container start (when the data
directory is empty). After that the file is ignored even if the container restarts.

**init-flyway-user.sql** creates two application-level users:

| User     | Password       | Privileges                               | Purpose          |
|----------|----------------|------------------------------------------|------------------|
| `flyway`  | `flyway_admin`  | `CONNECT`, `USAGE`, `CREATE` on `public` | DDL (schema mgmt)|
| `nene`    | `475963218`     | `SELECT/INSERT/UPDATE/DELETE` on tables  | DML (app runtime)|

Default privileges are set so any table/sequence `flyway` creates in the future is
automatically accessible to `nene` — no manual grants needed after migrations.

### redis

```yaml
image: redis:latest
ports:
  - '6379:6379'
```

No persistence configuration; data is lost when the container is removed.
For persistent sessions, add a named volume and `--appendonly yes` to the command.

### app

```yaml
build:
  context: .
  dockerfile: Dockerfile
ports:
  - '81:81'     # HTTP
  - '5005:5005' # JDWP remote debug
volumes:
  - ./target:/app/target   # hot-deploy mount (see workflow below)
```

---

## Environment Variables

All variables consumed by `application.yaml` (`${VAR:default}` syntax).
The `compose.yaml` overrides the defaults so services resolve each other by
Docker network hostname instead of `localhost`.

| Variable                                    | compose.yaml value                            | application.yaml default         |
|---------------------------------------------|-----------------------------------------------|----------------------------------|
| `SPRING_DATASOURCE_URL`                     | `jdbc:postgresql://postgres:5432/monolithic`  | `localhost:5432`                 |
| `SPRING_DATASOURCE_USERNAME`                | `nene`                                        | *(no default — required)*        |
| `SPRING_DATASOURCE_PASSWORD`                | `475963218`                                   | *(no default — required)*        |
| `SPRING_FLYWAY_URL`                         | `jdbc:postgresql://postgres:5432/monolithic`  | `localhost:5432`                 |
| `SPRING_FLYWAY_USER`                        | `flyway`                                      | *(no default — required)*        |
| `SPRING_FLYWAY_PASSWORD`                    | `flyway_admin`                                | *(no default — required)*        |
| `SPRING_DATA_REDIS_HOST`                    | `redis`                                       | `localhost`                      |
| `SPRING_DATA_REDIS_PORT`                    | `6379`                                        | `6379`                           |
| `JAVA_TOOL_OPTIONS`                         | `-agentlib:jdwp=...address=*:5005`            | `""` (debug disabled)            |
| `SERVER_PORT`                               | *(not set — uses default)*                    | `81`                             |
| `SECURITY_JWT_SECRET`                       | *(not set — uses default)*                    | `change-this-secret-key-...`     |

> **⚠ Security:** `SPRING_DATASOURCE_PASSWORD`, `SPRING_FLYWAY_PASSWORD`, and
> `SECURITY_JWT_SECRET` in `compose.yaml` are **local-only placeholders**.
> Externalize all secrets (Docker secrets, Vault, env injection) for any
> non-local environment.

---

## Maven `docker` Profile

Defined in `pom.xml`, the `docker` profile uses `exec-maven-plugin` to run
`docker compose up` during the **`verify`** phase:

```xml
<execution>
  <phase>verify</phase>
  <goal>exec</goal>
  <!-- runs: docker compose -f compose.yaml up --build --no-recreate -d -->
</execution>
```

Flags used:
- `--build` — always rebuilds the `app` image from `Dockerfile`
- `--no-recreate` — does not restart postgres/redis if they are already running
- `-d` — detached mode

---

## Common Commands

### Start (via Maven profile — recommended)

```bash
# Full build + bring up all containers in background
mvn verify -P docker

# Skip tests for faster iteration
mvn verify -P docker -DskipTests
```

### Start / Stop (direct Docker Compose)

```bash
# First run or full rebuild
docker compose -f compose.yaml up --build

# Subsequent starts (no rebuild)
docker compose -f compose.yaml up -d

# Stop containers, keep DB volume
docker compose -f compose.yaml down

# Stop + wipe DB volume (full reset — re-runs init-flyway-user.sql on next start)
docker compose -f compose.yaml down -v

# Restart only the app (infra stays running)
docker compose -f compose.yaml restart app
```

### Logs

```bash
docker compose -f compose.yaml logs -f           # all services
docker compose -f compose.yaml logs -f app       # app only
docker compose -f compose.yaml logs -f postgres  # postgres only
```

### Check health / status

```bash
docker compose -f compose.yaml ps
```

### Connect to PostgreSQL inside the container

```bash
docker exec -it <postgres-container-name> psql -U sa -d monolithic
```

---

## Hot-Deploy Workflow

The `compose.yaml` mounts `./target` into the container at `/app/target`.
When an IDE (e.g., IntelliJ) rebuilds the project, the updated JAR is written to
`target/monolithic-0.0.1-SNAPSHOT.jar` on the host, which is immediately visible
inside the container via the volume mount.

> The standard `Dockerfile` `ENTRYPOINT` runs `/app/app.jar` (copied at image build
> time), **not** the mounted volume path. Hot-deploy via the volume requires a
> custom entrypoint script (e.g., `dev-entrypoint.sh`) that watches the JAR at
> `/app/target/monolithic-0.0.1-SNAPSHOT.jar` and restarts the JVM when its
> timestamp changes. Add such a script and update `compose.yaml`'s `command:`
> field if you want true hot-deploy without rebuilding the image.

---

## Remote Debugging

Port `5005` is exposed and `JAVA_TOOL_OPTIONS` in `compose.yaml` enables JDWP:

```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

- `suspend=n` — the JVM starts immediately; it does not wait for a debugger
- Connect your IDE (IntelliJ / VS Code) to `localhost:5005` using a
  *Remote JVM Debug* run configuration

---

## .dockerignore

Excludes the following from the Docker build context (keeps image builds fast
and prevents secrets/IDE files from leaking into the image):

```
.git
.idea
*.md
formatter.xml
target
```

> The `target/` directory is excluded from the build context because the
> multi-stage `Dockerfile` compiles the source inside Docker itself.

---

## First-Time Setup Checklist

1. Install **Docker Desktop** (or Docker Engine + Compose plugin).
2. Clone the repository.
3. Run `mvn verify -P docker -DskipTests` from the project root.
4. Wait for the `app` container to become healthy (check with `docker compose -f compose.yaml ps`).
5. Open **http://localhost:81/swagger-ui.html**.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `app` fails to start — DB connection refused | `postgres` health check not passed yet | Wait and check `docker compose logs postgres` |
| `app` fails — `flyway` migration error | `init-flyway-user.sql` did not run (volume already existed) | Run `docker compose down -v` then restart |
| Port 5432 / 6379 / 81 already in use | Another local process bound the port | Stop the conflicting process or change the host port in `compose.yaml` |
| Changes to source not reflected in container | Image not rebuilt | Run `docker compose -f compose.yaml up --build` |
| Expired JWT causes 401 on public endpoints | `BearerTokenAuthenticationFilter` runs before `permitAll` check | Fixed in `SecurityConfig` via `publicPathAwareBearerTokenResolver` — see `config/SecurityConfig.java` |
