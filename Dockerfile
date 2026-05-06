# syntax=docker/dockerfile:1

# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build/services/identity-service

COPY services/identity-service/pom.xml ./pom.xml
COPY services/identity-service/src ./src
COPY contracts /build/contracts

RUN mvn -B -DskipTests clean package

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system appgroup \
    && useradd --system --gid appgroup --create-home appuser

WORKDIR /app
COPY --from=builder /build/services/identity-service/target/identity-service-*.jar /app/app.jar

EXPOSE 8081 9091

USER appuser:appgroup
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
