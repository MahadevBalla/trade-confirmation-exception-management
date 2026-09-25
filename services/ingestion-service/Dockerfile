# Build context for this Dockerfile is the "services" directory (see
# docker-compose.yml: context: ./services, dockerfile: ingestion-service/Dockerfile).
# That's so the build can see the reactor's pom.xml and common-lib module.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /reactor

COPY pom.xml pom.xml
COPY common-lib common-lib
COPY ingestion-service ingestion-service

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl common-lib,ingestion-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /reactor/ingestion-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
