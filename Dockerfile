# Build image after run mvn build separately
# =============================================================================
# FROM eclipse-temurin:21-jre-alpine
# WORKDIR /app
# COPY target/*.jar app.jar
# EXPOSE 81
# ENTRYPOINT ["java", "-jar", "app.jar"]

# Dockerfile cover all of it, include build project & copy target build jar file
# =============================================================================
# Stage 1 – Build
# Uses the official Maven image bundled with Eclipse Temurin JDK 21.
# We copy pom.xml first and run dependency:go-offline so Docker can cache
# the dependency layer separately from source code.  Re-running the build
# only downloads dependencies again when pom.xml actually changes.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# =============================================================================
# Stage 2 – Runtime
# Uses a lean JRE-only image (no compiler, no Maven) to keep the final image
# small and reduce the attack surface.
# A non-root system user (appuser) is created so the process does not run
# as root inside the container.
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system appgroup \
 && useradd  --system --gid appgroup appuser

COPY --from=builder /build/target/monolithic-0.0.1-SNAPSHOT.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

# 81   – application HTTP port (matches server.port in application.yaml)
# 5005 – JDWP remote-debug port
EXPOSE 81 5005

# JAVA_TOOL_OPTIONS is read by the JVM before main() is called.
# The docker-compose.yml sets it to enable JDWP; leave it empty here so the
# image works without debugging when run standalone.
ENV JAVA_TOOL_OPTIONS=""

ENTRYPOINT ["java", "-jar", "app.jar"]
