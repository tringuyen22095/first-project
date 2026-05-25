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
# Stage 2 – Runtime (Tomcat)
# Uses the official Tomcat 10.1 image (Jakarta EE / Servlet 5+ required by
# Spring Boot 3+/4+).  A non-root system user is created to reduce attack
# surface.  The WAR produced by Stage 1 is deployed as ROOT.war so the
# context path stays at / — matching the embedded-server behaviour.
# =============================================================================
FROM tomcat:10.1-jre21-temurin AS runtime

# Remove the bundled Tomcat sample applications
RUN rm -rf /usr/local/tomcat/webapps/*

RUN groupadd --system appgroup \
 && useradd  --system --gid appgroup appuser \
 && chown -R appuser:appgroup /usr/local/tomcat

COPY --from=builder /build/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
RUN chown appuser:appgroup /usr/local/tomcat/webapps/ROOT.war
USER appuser

# 8080 – Tomcat HTTP port
# 5005 – JDWP remote-debug port
EXPOSE 8080 5005

# JAVA_TOOL_OPTIONS is read by the JVM before main() is called.
# compose.yaml sets it to enable JDWP; leave it empty here so the
# image works without debugging when run standalone.
ENV JAVA_TOOL_OPTIONS=""
# Tomcat's default CMD (catalina.sh run) is used — no custom ENTRYPOINT needed.
