# Build context for this Dockerfile is the "services" directory (see
# docker-compose.yml: context: ./services, dockerfile: exception-service/Dockerfile).
# That's so the build can see the reactor's pom.xml and common-lib module.
#
# Phase 3 DevOps hardening (feat/devops-infra):
#   - Pinned to maven:3.9.9-eclipse-temurin-21-alpine (lighter build image)
#   - BuildKit cache mount on /root/.m2 for faster rebuilds
#   - Runtime stage: eclipse-temurin:21-jre-alpine (JRE only, not JDK)
#   - Non-root user: appuser:appgroup
#   - Correct port: 8084
#   - Container-aware JVM flags via JAVA_OPTS

# ------------------------------------------------------------------------------
# Stage 1: Build
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /reactor

COPY pom.xml pom.xml
COPY common-lib common-lib
COPY exception-service exception-service

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl common-lib,exception-service -am -DskipTests package

# ------------------------------------------------------------------------------
# Stage 2: Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
COPY --from=build /reactor/exception-service/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8084

# Container-aware JVM: cap heap at 75% of cgroup limit, use G1GC, fast SecureRandom
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
