# Docker Setup — Complete Guide

This guide covers every file, every decision, and every command involved in building and
running the full application stack (PostgreSQL + Redis + Spring Boot app) inside Docker
containers. It also explains how to trigger the entire workflow from a single Maven
command, enable hot-deploy during development, and attach a remote debugger from your IDE.

---

## Table of Contents

1. [Background & Motivation](#1-background--motivation)
2. [Architecture Overview](#2-architecture-overview)
3. [File Structure](#3-file-structure)
4. [Dockerfile — Multi-Stage Production Build](#4-dockerfile--multi-stage-production-build)
5. [Dockerfile.dev — Hot-Deploy Development Image](#5-dockerfiledev--hot-deploy-development-image)
6. [docker/dev-entrypoint.sh — JAR Watcher](#6-dockerdev-entrypointsh--jar-watcher)
7. [.dockerignore](#7-dockerignore)
8. [compose.yaml — Infrastructure Services](#8-composeyaml--infrastructure-services)
9. [docker-compose.yml — Full Stack (App + Infra)](#9-docker-composeyml--full-stack-app--infra)
    - [Health checks](#91-health-checks-on-postgres-and-redis)
    - [The `app` service](#92-the-app-service)
    - [depends_on with health conditions](#93-depends_on-with-health-conditions)
    - [Environment variable overrides](#94-environment-variable-overrides)
    - [Remote debugging (JDWP)](#95-remote-debugging-jdwp)
10. [Maven `docker` Profile](#10-maven-docker-profile)
11. [How It All Fits Together](#11-how-it-all-fits-together)
12. [Workflow: Full Docker Stack](#12-workflow-full-docker-stack)
13. [Workflow: Hot Deploy from IDE](#13-workflow-hot-deploy-from-ide)
14. [Workflow: Connecting Your IDE Debugger](#14-workflow-connecting-your-ide-debugger)
15. [Difference Between compose.yaml and docker-compose.yml](#15-difference-between-composeyaml-and-docker-composeyml)
16. [Design Decisions & Why](#16-design-decisions--why)
17. [Useful Commands Reference](#17-useful-commands-reference)
18. [Troubleshooting](#18-troubleshooting)

---

## 1. Background & Motivation

**Before these changes**, the project used Spring Boot's built-in Docker Compose
integration (`spring-boot-docker-compose`) to automatically start PostgreSQL and Redis
when the application was launched via `mvn spring-boot:run` or from IntelliJ. This
worked for local development but had several limitations:

- The application itself always ran on the host JVM — it could never be deployed as a
  Docker image without additional setup.
- Running the fully containerized stack required either typing `docker compose up` manually
  or creating a bespoke IntelliJ run configuration that every developer had to recreate.
- There was no standardized way to run a remote debugger against the containerized app.
- There was no hot-deploy path: every code change required a full container rebuild.

**After these changes**:

- The application is built into a production Docker image using a **multi-stage
  `Dockerfile`** that runs Maven inside the build stage so no pre-built JAR is required.
- A **`Dockerfile.dev`** variant mounts the JAR from the host and watches for changes,
  enabling sub-5-second hot-deploy without rebuilding the container image.
- **`docker-compose.yml`** orchestrates the full three-container stack with health-checked
  dependencies and an optional JDWP debug port.
- A Maven profile (`-P docker`) packages the JAR and invokes `docker compose up`
  automatically — no IDE run configuration needed.
- **`compose.yaml`** is preserved unchanged for the `mvn spring-boot:run` local
  development path, keeping that workflow entirely unaffected.

---

## 2. Architecture Overview

```
┌────────────────────────────────────────────────────────┐
│                  Docker Network (bridge)               │
│                                                        │
│   ┌──────────────┐     ┌────────────┐                  │
│   │  postgres    │     │   redis    │                  │
│   │  port 5432   │     │  port 6379 │                  │
│   └──────┬───────┘     └─────┬──────┘                  │
│          │ JDBC              │ Lettuce                 │
│   ┌──────▼───────────────────▼──────┐                  │
│   │              app                │                  │
│   │  HTTP  → port 81                │                  │
│   │  JDWP  → port 5005              │                  │
│   └──────┬──────────────────────────┘                  │
└──────────┼─────────────────────────────────────────────┘
           │ exposed to host
     ┌─────▼──────┐   ┌────────────┐
     │  Browser / │   │  IDE       │
     │  Swagger   │   │  Debugger  │
     │ :81        │   │  :5005     │
     └────────────┘   └────────────┘
```

All three services run on the same Docker-managed bridge network. Service names
(`postgres`, `redis`) act as hostnames inside this network, which is why the app uses
`postgres:5432` instead of `localhost:5432` when running in a container.

---

## 3. File Structure

```
first-project/
├── Dockerfile               ← Multi-stage production image (Maven + JRE, builds JAR inside Docker)
├── Dockerfile.dev           ← Dev image: JRE + watcher only, JAR mounted from host
├── .dockerignore            ← Excludes irrelevant files from Docker build context
├── docker-compose.yml       ← Full stack (postgres + redis + app) — used for Docker deployment
├── compose.yaml             ← postgres + redis only — used by Spring Boot's local-dev integration
├── pom.xml                  ← Contains the 'docker' Maven profile
├── docker/
│   └── dev-entrypoint.sh   ← Polling watcher: detects JAR changes, restarts JVM
└── src/
    └── main/resources/
        ├── application.yaml
        └── db/
            ├── init/
            │   └── init-flyway-user.sql   ← Creates the flyway DDL user on first DB init
            └── migration/
                └── *.sql
```

---

## 4. Dockerfile — Multi-Stage Production Build

**File:** `Dockerfile` (project root)

```dockerfile
# Stage 1 – Build
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2 – Runtime
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system appgroup \
 && useradd  --system --gid appgroup appuser

COPY --from=builder /build/target/monolithic-0.0.1-SNAPSHOT.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 81 5005

ENV JAVA_TOOL_OPTIONS=""

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Why multi-stage?

A single-stage image that included Maven and the full JDK would be ~700 MB. By separating
build from runtime the final image is ~250 MB and contains no compiler, no Maven, and no
build tooling — only the JRE and the application JAR.

### Stage 1 — Builder

| Instruction | What it does | Why |
|-------------|-------------|-----|
| `FROM maven:3.9-eclipse-temurin-21 AS builder` | Uses the official Maven 3.9 image bundled with Eclipse Temurin JDK 21. | Everything needed to compile and package the Spring Boot application is available in a single well-maintained image. Matches `java.version=21` in `pom.xml`. |
| `WORKDIR /build` | Sets the working directory for all subsequent instructions in this stage. | Keeps all build artefacts under one known path. |
| `COPY pom.xml .` | Copies only the POM descriptor before copying source code. | **Layer-caching trick:** Maven dependency downloads are slow. By copying `pom.xml` first and running `dependency:go-offline` as a separate `RUN` layer, Docker can cache that layer and reuse it on every subsequent build as long as `pom.xml` has not changed. Source code changes (which are frequent) do not invalidate the dependency cache. |
| `RUN mvn dependency:go-offline -q` | Downloads all compile, runtime, and plugin dependencies into the local Maven cache inside this layer. | The `-q` flag suppresses Maven's verbose output to keep build logs readable. |
| `COPY src ./src` | Copies the entire `src/` tree. | Now that dependencies are cached, only source changes invalidate from here forward. |
| `RUN mvn package -DskipTests -q` | Compiles source, runs annotation processors (Lombok, MapStruct), and produces the Spring Boot fat JAR. Tests are skipped (`-DskipTests`) because the Docker build is a packaging step — tests are run separately by `mvn test` or in CI. |

### Stage 2 — Runtime

| Instruction | What it does | Why |
|-------------|-------------|-----|
| `FROM eclipse-temurin:21-jre-jammy AS runtime` | Uses the Eclipse Temurin **JRE** (not JDK) on Ubuntu 22.04 LTS (Jammy). | JRE only — no compiler, no `javac`, no `jshell`. Reduces image size and attack surface. Jammy is a long-term-support Ubuntu release with reliable package availability. |
| `WORKDIR /app` | Sets the working directory to `/app`. | |
| `RUN groupadd … && useradd …` | Creates a system group `appgroup` and a system user `appuser`. | **Security best practice:** containers should never run as `root`. A non-root process cannot write outside its working directory, cannot modify system files, and limits the blast radius of any container escape vulnerability. |
| `COPY --from=builder … app.jar` | Copies the fat JAR from the builder stage into the runtime image. | Only the JAR crosses the stage boundary — Maven, the JDK, and the source tree are left behind in the discarded builder layer. |
| `RUN chown appuser:appgroup app.jar` | Ensures the JAR is owned by `appuser` before switching to that user. | Without this, the file would be owned by root and unreadable by `appuser` depending on the base image's `umask`. |
| `USER appuser` | Switches to the non-root user for all subsequent instructions and at container runtime. | |
| `EXPOSE 81 5005` | Documents that the container listens on port 81 (HTTP) and port 5005 (JDWP). | `EXPOSE` is metadata — it does not publish ports to the host. Port publishing is configured in `docker-compose.yml`. |
| `ENV JAVA_TOOL_OPTIONS=""` | Sets a safe default (empty) for the JDWP options variable. | `JAVA_TOOL_OPTIONS` is a standard JVM environment variable read before `main()` is called. The `docker-compose.yml` sets this to enable JDWP for debugging. The empty default here means the image runs without a debug agent when used standalone. |
| `ENTRYPOINT ["java", "-jar", "app.jar"]` | Launches the Spring Boot application. Exec form (JSON array) is used. | **Exec form** makes `java` PID 1 inside the container. PID 1 receives OS signals directly (including `SIGTERM` from `docker compose down`), enabling Spring Boot's graceful shutdown. Shell form wraps the command in `/bin/sh -c`, which swallows signals. |

### Why not Spring Boot Buildpacks or Jib?

| Tool | Pros | Reason not chosen |
|------|------|-------------------|
| `spring-boot:build-image` (Buildpacks) | No Dockerfile required, smart layer splitting | Downloads builders from internet on first run; layers are opaque |
| `jib-maven-plugin` | Fast, daemon-less, no Dockerfile | Pushes directly to a registry by default; less obvious local workflow |
| Hand-written Dockerfile | Every layer is explicit and auditable | — |

The hand-written Dockerfile was chosen for full transparency and because it integrates
naturally with Docker Compose's `build:` directive.

---

## 5. Dockerfile.dev — Hot-Deploy Development Image

**File:** `Dockerfile.dev` (project root)

```dockerfile
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system appgroup \
 && useradd  --system --gid appgroup appuser

COPY docker/dev-entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh \
 && chown appuser:appgroup /entrypoint.sh

USER appuser

EXPOSE 81 5005

ENV JAVA_TOOL_OPTIONS=""

ENTRYPOINT ["/entrypoint.sh"]
```

`Dockerfile.dev` is a **lightweight development image** that does not bake the JAR into
the image. Instead, the JAR is provided at container startup via a Docker volume mount
from the host (`./target:/app/target`). The entrypoint is a shell script watcher that
detects when the JAR changes on disk and restarts the JVM automatically.

### Comparison: production vs. development image

| | `Dockerfile` (production) | `Dockerfile.dev` (development) |
|---|---|---|
| JAR source | Baked into image at build time (Maven runs inside Docker) | Mounted from host at container start |
| Build time | ~3–5 min (downloads dependencies, compiles) | ~10 s (only copies the shell script) |
| Code update cycle | Requires `docker compose up --build` | IDE builds JAR on host → JVM restarts in ≤ 5 s |
| Entrypoint | `java -jar app.jar` directly | `dev-entrypoint.sh` (polling watcher) |
| Use case | CI/CD, staging, `mvn verify -P docker` | Daily development (`docker-compose.yml up`) |

---

## 6. docker/dev-entrypoint.sh — JAR Watcher

**File:** `docker/dev-entrypoint.sh`

This POSIX shell script is the entrypoint for `Dockerfile.dev`. It implements a
**polling hot-deploy loop**:

```
┌─────────────────────────────────────────────────┐
│ dev-entrypoint.sh                               │
│                                                 │
│  1. Wait for JAR to appear at /app/target/...   │
│  2. Record JAR modification timestamp           │
│  3. Start JVM in background (java -jar)         │
│  4. Every 3 seconds:                            │
│       • stat the JAR → get current timestamp   │
│       • if timestamp changed:                   │
│           – kill old JVM (SIGTERM → SIGKILL)   │
│           – wait for it to exit                 │
│           – start new JVM                       │
│  5. On SIGTERM (docker compose down):           │
│       – forward signal to JVM                   │
│       – wait for graceful shutdown              │
└─────────────────────────────────────────────────┘
```

### Why polling instead of inotify?

`inotifywait` (from `inotify-tools`) is more efficient but has a critical limitation on
macOS and Windows: Docker Desktop uses a userspace filesystem bridge to share host files
into the Linux VM. File system change events from the host do **not** reliably propagate
as kernel `inotify` events inside the container. **Polling with `stat -c %Y`** (checking
the file's modification timestamp every few seconds) works correctly on all three major
platforms.

### Why `sleep` in background + `wait`?

```sh
sleep "$POLL_SEC" &
wait $!
```

If `sleep` were called in the foreground, the shell would be blocked during the sleep and
could not respond to signals. Running `sleep` in the background and calling `wait` on its
PID keeps the shell signal-responsive. When Docker sends `SIGTERM` (on `docker compose
down`), the `trap` fires immediately, the JVM is killed gracefully, and the container
exits cleanly.

### JAVA_TOOL_OPTIONS is transparent

The script calls `java -jar "$JAR"` without explicitly passing `JAVA_TOOL_OPTIONS`. The
JVM reads it from the environment on every invocation automatically. This means JDWP, GC
flags, heap settings, or any JVM option set via this environment variable apply without
any modification to the script itself.

---

## 7. .dockerignore

**File:** `.dockerignore` (project root)

```
.git
.idea
*.md
formatter.xml
target
```

### Why this file matters

Before Docker builds an image, it sends the entire **build context** (the directory tree
at the `context:` path) to the Docker daemon. Without a `.dockerignore`, this means
sending git history, IDE configuration, documentation, and the `target/` directory — none
of which belong in the final image.

### What each exclusion does

| Entry | Reason |
|-------|--------|
| `.git` | Git history is irrelevant to the image and can be hundreds of megabytes. |
| `.idea` | IntelliJ project files have no runtime relevance. |
| `*.md` | Documentation files are not part of the runtime image. |
| `formatter.xml` | Code style configuration has no runtime relevance. |
| `target` | The entire Maven output directory is excluded because the multi-stage `Dockerfile` runs `mvn package` inside the builder stage — it builds its own JAR from source. Pre-built artefacts from the host would never be used and would only bloat the build context. |

> **Important:** `src/` is intentionally **not** excluded. The `Dockerfile` builder stage
> does `COPY src ./src` to compile the application inside Docker. Excluding `src` from
> `.dockerignore` would cause the `COPY` instruction to fail.

---

## 8. compose.yaml — Infrastructure Services

**File:** `compose.yaml` (project root)

`compose.yaml` is used exclusively by **Spring Boot's Docker Compose integration**
(`spring-boot-docker-compose`) when running the application on the host JVM via
`mvn spring-boot:run`. Spring Boot reads this file at startup, starts only the
infrastructure services it needs (postgres, redis), and connects to them over
`localhost`.

```yaml
services:
  postgres:
    image: 'postgres:latest'
    environment:
      - 'POSTGRES_DB=monolithic'
      - 'POSTGRES_PASSWORD=admin'
      - 'POSTGRES_USER=sa'
    ports:
      - '5432:5432'
    volumes:
      - './src/main/resources/db/init:/docker-entrypoint-initdb.d'
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U sa -d monolithic" ]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  redis:
    image: 'redis:latest'
    ports:
      - '6379:6379'
    healthcheck:
      test: [ "CMD", "redis-cli", "ping" ]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 5s
```

Health checks were added to both services so that Spring Boot's integration waits for
them to be ready before considering the startup sequence complete.

The redis port was changed from `'6379'` (random host port) to `'6379:6379'` (fixed
host port). The original random mapping worked because Spring Boot's Docker Compose
integration inspects the mapped port dynamically. The fixed mapping also makes Redis
accessible from host-side debugging tools (Redis Insight, `redis-cli` on the host).

> **This file is not used by `docker-compose.yml` or the Maven `docker` profile.** It
> exists solely for the `mvn spring-boot:run` local development path.

---

## 9. docker-compose.yml — Full Stack (App + Infra)

**File:** `docker-compose.yml` (project root)

`docker-compose.yml` is the full-stack compose file. It defines all three services and is
the file used by the Maven `docker` profile and by direct `docker compose` commands for
Docker-based deployments.

```yaml
services:
  postgres:
    image: 'postgres:latest'
    environment:
      - POSTGRES_DB=monolithic
      - POSTGRES_PASSWORD=admin
      - POSTGRES_USER=sa
    ports:
      - '5432:5432'
    volumes:
      - './src/main/resources/db/init:/docker-entrypoint-initdb.d'
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U sa -d monolithic"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  redis:
    image: 'redis:latest'
    ports:
      - '6379:6379'
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 5s

  app:
    build:
      context: .
      dockerfile: Dockerfile.dev   # swap to Dockerfile for a fully self-contained build
    ports:
      - '81:81'
      - '5005:5005'
    volumes:
      - ./target:/app/target       # hot-deploy: mount host target/ so watcher detects changes
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/monolithic
      - SPRING_DATASOURCE_USERNAME=sa
      - SPRING_DATASOURCE_PASSWORD=admin
      - SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/monolithic
      - SPRING_FLYWAY_USER=flyway
      - SPRING_FLYWAY_PASSWORD=flyway_admin
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_DATA_REDIS_PORT=6379
      - SPRING_DOCKER_COMPOSE_ENABLED=false
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: on-failure

volumes:
  postgres_data:
```

### 9.1 Health checks on postgres and redis

#### PostgreSQL

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U sa -d monolithic"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 10s
```

`pg_isready` is the official PostgreSQL utility for probing whether a server is ready to
accept connections. It exits with code 0 when ready, non-zero otherwise — exactly what
Docker's health check mechanism requires.

- `-U sa` — the username (matches `POSTGRES_USER`).
- `-d monolithic` — the database (matches `POSTGRES_DB`).
- `start_period: 10s` — failed checks during the first 10 seconds are not counted against
  the retry budget. This prevents false `unhealthy` status during the normal PostgreSQL
  initialization period.
- `retries: 5` × `interval: 10s` — Docker waits up to ~60 seconds before declaring the
  container unhealthy.

#### Redis

```yaml
healthcheck:
  test: ["CMD", "redis-cli", "ping"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 5s
```

`redis-cli ping` sends a `PING` command and expects `PONG`. Redis starts much faster than
PostgreSQL so `start_period` is shorter (5 s).

**Why health checks are necessary:** Without them, `depends_on` only waits for the
container *process* to start — not for the *service* inside it to be ready. PostgreSQL in
particular executes initialization scripts on first boot (creating the database, setting
up roles) which takes several seconds. If the Spring Boot app starts before PostgreSQL is
ready, Flyway migration will fail with `Connection refused` and the application will crash.

### 9.2 The `app` service

#### `build` directive

```yaml
build:
  context: .
  dockerfile: Dockerfile.dev
```

For day-to-day development, `Dockerfile.dev` is used — it builds in seconds because it
only copies the watcher script. Swap to `dockerfile: Dockerfile` to get a fully
self-contained image where Maven compiles the code inside Docker (suitable for CI or
sharing the image without distributing the JAR).

#### `volumes`

```yaml
volumes:
  - ./target:/app/target
```

The entire Maven `target/` directory is bind-mounted from the host into the container at
`/app/target`. When your IDE rebuilds the JAR on the host, the updated file is immediately
visible inside the container at the path `dev-entrypoint.sh` monitors. This is the
mechanism that makes hot-deploy possible.

#### `postgres_data` named volume

```yaml
volumes:
  postgres_data:
```

A named Docker volume persists the PostgreSQL data directory (`/var/lib/postgresql/data`)
across `docker compose down` restarts. The database is only wiped when you explicitly run
`docker compose down -v`.

#### `restart: on-failure`

If the JVM exits with a non-zero code (e.g. startup failure because a dependency was
momentarily unavailable despite the health check), Docker automatically restarts the `app`
container. This prevents a transient infrastructure hiccup from permanently killing the
dev session.

### 9.3 `depends_on` with health conditions

```yaml
depends_on:
  postgres:
    condition: service_healthy
  redis:
    condition: service_healthy
```

Docker Compose will **not start `app`** until both `postgres` and `redis` report
`healthy`. This completely eliminates the race condition where the app tries to connect
to a database that has not finished initializing.

The condition also applies to already-running containers. When you run
`docker compose up app` (targeting only the app service), Docker queries the current
health status of the running `postgres` and `redis` containers before starting `app`.

| Scenario | Behaviour |
|----------|-----------|
| postgres + redis not running | Compose starts all three. App waits for health checks to pass. |
| postgres + redis already running and healthy | `--no-recreate` skips them. Only `app` is built and started. |
| postgres + redis running but not yet healthy | `app` start is delayed until both pass their health checks. |

### 9.4 Environment variable overrides

Inside Docker, services communicate over the bridge network using **service names as
hostnames**. The `application.yaml` hardcodes `localhost` URLs for the `mvn
spring-boot:run` workflow. The environment block in `docker-compose.yml` overrides every
connection string at runtime so the containerized app uses service names instead.

Spring Boot's externalized-configuration rule: `SPRING_DATASOURCE_URL` overrides
`spring.datasource.url` (dots → underscores, uppercase).

| Environment variable | Value | Spring Boot property overridden |
|---------------------|-------|--------------------------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/monolithic` | `spring.datasource.url` |
| `SPRING_DATASOURCE_USERNAME` | `sa` | `spring.datasource.username` |
| `SPRING_DATASOURCE_PASSWORD` | `admin` | `spring.datasource.password` |
| `SPRING_FLYWAY_URL` | `jdbc:postgresql://postgres:5432/monolithic` | `spring.flyway.url` |
| `SPRING_FLYWAY_USER` | `flyway` | `spring.flyway.user` |
| `SPRING_FLYWAY_PASSWORD` | `flyway_admin` | `spring.flyway.password` |
| `SPRING_DATA_REDIS_HOST` | `redis` | `spring.data.redis.host` |
| `SPRING_DATA_REDIS_PORT` | `6379` | `spring.data.redis.port` |
| `SPRING_DOCKER_COMPOSE_ENABLED` | `false` | `spring.docker.compose.enabled` |
| `JAVA_TOOL_OPTIONS` | `-agentlib:jdwp=…` | JVM agent (JDWP remote debug) |

`SPRING_DOCKER_COMPOSE_ENABLED=false` is **critical**: it disables Spring Boot's Docker
Compose integration inside the container. Without it, the app would try to invoke
`docker compose` from inside the container — which fails because no Docker CLI is
installed in the JRE image — and crash on startup.

`application.yaml` is **not modified**. It continues to work for the local-dev
`mvn spring-boot:run` path where `localhost` is correct.

### 9.5 Remote debugging (JDWP)

```yaml
- JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

JDWP (Java Debug Wire Protocol) is the standard mechanism for IDE remote debugging.

| JDWP parameter | Meaning |
|----------------|---------|
| `transport=dt_socket` | Use a TCP socket (not shared memory) |
| `server=y` | The JVM listens for incoming debugger connections (IDE connects to JVM, not the other way around) |
| `suspend=n` | The JVM starts immediately without waiting for a debugger — do not use `suspend=y` in dev or the app will never start unless a debugger connects first |
| `address=*:5005` | Listen on all network interfaces on port 5005 inside the container |

The port is forwarded to the host with:

```yaml
ports:
  - '5005:5005'
```

Your IDE connects to `localhost:5005`. Docker forwards the connection to the JVM inside
the container.

> ⚠️ **Security note:** Never expose port 5005 in a production or internet-facing
> environment. JDWP has no authentication and gives complete control of the JVM to anyone
> who can connect.

---

## 10. Maven `docker` Profile

**File:** `pom.xml` — `<profiles>` section

```xml
<profiles>
    <profile>
        <id>docker</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>exec-maven-plugin</artifactId>
                    <version>3.5.0</version>
                    <executions>
                        <execution>
                            <id>docker-compose-up</id>
                            <phase>verify</phase>
                            <goals>
                                <goal>exec</goal>
                            </goals>
                            <configuration>
                                <executable>docker</executable>
                                <arguments>
                                    <argument>compose</argument>
                                    <argument>up</argument>
                                    <argument>--build</argument>
                                    <argument>--no-recreate</argument>
                                    <argument>-d</argument>
                                </arguments>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### Why a Maven profile instead of an IntelliJ run configuration?

An IntelliJ run configuration is stored in `.idea/` (gitignored by default) or in an
`.xml` file that must be shared and maintained per developer. A Maven profile is defined
once in `pom.xml`, is version-controlled, and works identically from IntelliJ, VS Code,
the terminal, or a CI pipeline — no IDE setup required.

### Why `exec-maven-plugin`?

`exec-maven-plugin` executes an external process as part of the Maven build. It runs
`docker compose up` exactly as if you had typed it in a terminal, inheriting the current
working directory (project root) so Docker Compose finds `docker-compose.yml`. This is
simpler and more transparent than alternatives like `dockerfile-maven-plugin` or
`fabric8/docker-maven-plugin` because it delegates the entire container lifecycle to
Docker Compose.

### Why bind to the `verify` phase?

Maven's default lifecycle runs in this order:

```
validate → compile → test → package → verify → install → deploy
```

The Spring Boot fat JAR is produced at the **`package`** phase
(`spring-boot-maven-plugin:repackage`). Binding the `docker compose up` execution to
**`verify`** guarantees that the JAR exists before the `Dockerfile` builder stage tries
to `COPY` it (production image) or before the watcher container starts looking for it
(dev image). Binding to `package` itself would be ambiguous because ordering of multiple
goals within the same phase depends on plugin declaration order.

### `docker compose up` flags explained

| Flag | Meaning |
|------|---------|
| `--build` | Rebuilds the `app` image from `Dockerfile.dev` (or `Dockerfile`) before starting. Ensures the container reflects the latest build artefacts. Without this, Docker Compose may start from a stale cached image. |
| `--no-recreate` | Does not stop or recreate containers that are already running and whose configuration has not changed. If `postgres` and `redis` are already healthy, they are left completely untouched. Only the `app` container is started or recreated. |
| `-d` | Detached mode. All containers run in the background and the Maven process returns immediately. Without `-d`, Maven would block indefinitely until all containers were stopped. |

---

## 11. How It All Fits Together

### Path A — Full Docker stack via Maven profile

```
mvn verify -P docker
     │
     ├─ compile   (javac + Lombok + MapStruct annotation processors)
     ├─ test      (JUnit 5 + Testcontainers — spins up own DB/Redis containers)
     ├─ package ──► target/monolithic-0.0.1-SNAPSHOT.jar   (Spring Boot fat JAR)
     │
     └─ verify ──► exec: docker compose up --build --no-recreate -d
                         │
                         ├─ postgres  ──► pg_isready ──► healthy ──────────────┐
                         ├─ redis     ──► redis-cli ping ──► healthy ──────────┤
                         │                                                     │
                         └─ app  ────────────────────── depends_on (healthy) ──┘
                                │
                                ├─ Dockerfile.dev builds image (~10 s)
                                ├─ ./target mounted into container
                                ├─ dev-entrypoint.sh starts JVM
                                ├─ Env vars override localhost URLs → service names
                                └─ App available at http://localhost:81
```

### Path B — Local development via Spring Boot's Docker Compose integration

```
mvn spring-boot:run
     │
     └─ Spring Boot reads compose.yaml
           │
           ├─ postgres  ──► started if not already running
           └─ redis     ──► started if not already running
                │
                └─ Spring Boot app runs as native JVM process on host
                       datasource: localhost:5432
                       redis:      localhost:6379
```

---

## 12. Workflow: Full Docker Stack

### Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- Java 21 on the host (for `mvn compile` / `mvn test` before Docker)
- Maven 3.9+ on the host

### First run — start the entire stack

```bash
mvn verify -P docker -DskipTests
```

What happens step by step:

1. Maven compiles the project and packages the fat JAR.
2. `docker compose up --build --no-recreate -d` is invoked.
3. Docker builds the `app` image from `Dockerfile.dev` (~10 s).
4. Docker pulls `postgres:latest` and `redis:latest` (first time only).
5. `postgres` and `redis` containers start; Docker polls their health checks.
6. On first PostgreSQL startup, `init-flyway-user.sql` runs once and creates the
   `flyway` DDL user.
7. Once both are healthy, the `app` container starts.
8. `dev-entrypoint.sh` detects the JAR at `/app/target/…`, starts the JVM.
9. Spring Boot starts, Flyway runs migrations, the app is live at `http://localhost:81`.

### Subsequent runs — rebuild app only (infra already running)

```bash
mvn verify -P docker -DskipTests
# --no-recreate leaves postgres and redis untouched
```

Or target only the app service without Maven:

```bash
docker compose up app
# Checks health conditions of postgres/redis before starting app
```

### Full reset (wipe database)

```bash
docker compose down -v
mvn verify -P docker -DskipTests
```

---

## 13. Workflow: Hot Deploy from IDE

Once the dev stack is running via `docker-compose.yml up`, you can update running code
without rebuilding or restarting any container.

### How it works

```
Host machine                          Docker container
─────────────────────────────────     ──────────────────────────────────────────────
src/  ──► IDE compiles ──► target/    volume mount      /app/target/
                           *.jar  ─────────────────────► *.jar
                                                              │
                                                    dev-entrypoint.sh polls
                                                    every 3 seconds
                                                              │
                                                    timestamp changed?
                                                              │
                                                    yes → kill old JVM (SIGTERM)
                                                           → start new JVM
                                                              │
                                                    new code is live ✓
```

### IntelliJ IDEA — Rebuild JAR on demand

1. Open the **Maven** panel (right sidebar).
2. Navigate to **Lifecycle → package**.
3. Right-click → **Create Run Configuration** → add `-DskipTests`.
4. Optionally assign a keyboard shortcut (e.g. `Ctrl+Shift+F9`).
5. Press the shortcut after making changes — Maven rebuilds the JAR — the container picks
   it up within ~3 seconds.

### IntelliJ IDEA — Rebuild as "Before launch" step

1. Open **Run → Edit Configurations**.
2. Select or create any run/debug configuration.
3. In **Before launch**, click `+` → **Run Maven Goal** → enter `package -DskipTests`.
4. Every time you run or debug that configuration, the JAR is rebuilt and the container
   JVM restarts automatically.

### What you see in container logs

```
[hot-deploy] JAR changed at 14:23:07, restarting JVM...
[hot-deploy] JVM running (PID 42)
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
...
```

---

## 14. Workflow: Connecting Your IDE Debugger

The `app` container listens on port 5005 for JDWP connections whenever
`JAVA_TOOL_OPTIONS` contains the debug agent flag. Connect your IDE while the stack is
running.

### IntelliJ IDEA

1. **Run → Edit Configurations → + → Remote JVM Debug**
2. **Host:** `localhost`
3. **Port:** `5005`
4. **Debugger mode:** `Attach to remote JVM`
5. Click **OK**, then click the **Debug** button (bug icon) next to the new configuration.
6. Set breakpoints anywhere in your source — they will hit inside the running container.

### Visual Studio Code

Create or edit `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Docker Remote Debug",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005
    }
  ]
}
```

Go to **Run and Debug** (`Ctrl+Shift+D`), select `Docker Remote Debug`, click the play
button.

---

## 15. Difference Between compose.yaml and docker-compose.yml

| | `compose.yaml` | `docker-compose.yml` |
|---|---|---|
| **Purpose** | Local development only | Full Docker deployment |
| **Services** | postgres, redis | postgres, redis, app |
| **Used by** | Spring Boot Docker Compose integration (`mvn spring-boot:run`) | `docker compose up`, Maven `-P docker` profile |
| **App runs as** | Native JVM process on host machine | Container (via `Dockerfile.dev` or `Dockerfile`) |
| **DB hostname** | `localhost` (host JVM connects directly) | `postgres` (Docker service name) |
| **Hot deploy** | N/A (IDE restart) | Volume-mounted JAR + watcher |
| **Remote debug** | IDE attaches to host JVM process directly | IDE attaches to JDWP on `localhost:5005` |

When you run `mvn spring-boot:run`, Spring Boot reads `compose.yaml`, starts only
postgres and redis, and the app runs as a regular Java process on your machine.

When you run `mvn verify -P docker` or `docker compose up`, all three services run as
containers on a shared Docker network.

---

## 16. Design Decisions & Why

### No multi-stage build used with the Maven profile

When the Maven `docker` profile runs, `Dockerfile.dev` is used (not the production
`Dockerfile`). The Maven build has already compiled and packaged the JAR. Rebuilding it
again inside a multi-stage Docker build would be redundant and slow. The multi-stage
`Dockerfile` is reserved for contexts where a pre-built JAR is not available — CI
pipelines, sharing the image without a Maven environment, or building via
`docker build .` directly.

### `application.yaml` was not modified

Connection URLs remain as `localhost` in `application.yaml`. This preserves the
`mvn spring-boot:run` local development workflow without any additional Spring profiles
or conditional configuration. Environment variables set in `docker-compose.yml` override
the YAML values at runtime only when the app runs inside Docker, following Spring Boot's
externalized-configuration hierarchy.

### Named volume for postgres data

`docker-compose.yml` uses a named volume (`postgres_data`) instead of a bind-mount for
PostgreSQL data. Named volumes are managed entirely by Docker, survive `docker compose
down`, and are portable across host machines. The data is explicitly wiped only with
`docker compose down -v`.

### Non-root container user

Both `Dockerfile` and `Dockerfile.dev` create a dedicated `appuser` system account and
run the JVM as that user. This follows the principle of least privilege: the JVM process
cannot write outside `/app`, cannot modify system files, and cannot escalate privileges.

### Two compose files instead of one with profiles

Docker Compose supports service profiles (e.g. `--profile dev`). A single file with
profiles was considered but rejected in favour of two separate files because:

- `compose.yaml` is the conventional filename Spring Boot's Docker Compose integration
  looks for — it must contain only the services Spring Boot should auto-manage.
- Separating concerns into two files makes the intent unambiguous: `compose.yaml` = infra
  for local dev; `docker-compose.yml` = full stack for Docker.

---

## 17. Useful Commands Reference

```bash
# ── Maven profile ──────────────────────────────────────────────────────────
# Build JAR + build image + start full stack (detached)
mvn verify -P docker

# Same, skip tests for faster iteration
mvn verify -P docker -DskipTests

# ── Docker Compose — full stack ────────────────────────────────────────────
# Start all services (uses docker-compose.yml by default)
docker compose up --build -d

# Restart only the app (leave postgres/redis running)
docker compose up app

# Rebuild app image without cache
docker compose build --no-cache app

# Stream app logs
docker compose logs -f app

# Check health and status of all services
docker compose ps

# Open a shell inside the running app container
docker exec -it $(docker compose ps -q app) sh

# Connect to postgres with psql
docker exec -it $(docker compose ps -q postgres) psql -U sa -d monolithic

# ── Stopping / resetting ───────────────────────────────────────────────────
# Stop containers, keep volumes (database data preserved)
docker compose stop

# Remove containers, keep volumes (database data preserved)
docker compose down

# Remove containers AND volumes (full reset, database wiped)
docker compose down -v

# ── Application endpoints ──────────────────────────────────────────────────
# API base:    http://localhost:81
# Swagger UI:  http://localhost:81/swagger-ui.html
# Health:      http://localhost:81/health-check
```

---

## 18. Troubleshooting

### App fails with "Connection refused" to postgres

The app started before postgres was healthy. Check health status:

```bash
docker compose ps
docker compose logs postgres
```

If postgres is stuck in `starting`, verify that port 5432 is not already bound by a local
PostgreSQL instance on the host.

### `init-flyway-user.sql` was not executed

PostgreSQL init scripts run **only once**, on the very first start of a fresh data
directory. If a `postgres_data` volume already exists from a previous run, the script is
skipped. Wipe the volume and restart:

```bash
docker compose down -v
docker compose up --build -d
```

### IDE cannot connect to the debugger

1. Confirm the app container is running: `docker compose ps`
2. Confirm port 5005 is mapped: `docker compose port app 5005`
3. Check app logs for `Listening for transport dt_socket at address: 5005` — this line
   confirms JDWP is active. If it is absent, `JAVA_TOOL_OPTIONS` was not set correctly.
4. Verify no local firewall blocks port 5005.

### JVM does not restart after IDE rebuilds JAR

1. Check watcher logs: `docker compose logs -f app` — look for `[hot-deploy] JAR changed`.
2. If nothing appears, the modification timestamp inside the container may not have
   updated. Some build tools write to a temp file and atomically rename it — which
   preserves the original inode but may not update `mtime` inside the volume. Force a
   timestamp update with:
   ```bash
   touch target/monolithic-0.0.1-SNAPSHOT.jar
   ```
3. Confirm the volume mount is correct:
   ```bash
   docker inspect $(docker compose ps -q app) | grep -A 5 Mounts
   ```

### `mvn spring-boot:run` and `docker compose up` conflict

Both workflows use the same host ports (5432, 6379). Running both simultaneously causes
port conflicts. Use one workflow at a time:

- Local dev: `mvn spring-boot:run` (Spring Boot manages `compose.yaml`)
- Full Docker: `mvn verify -P docker` (Docker Compose manages `docker-compose.yml`)
