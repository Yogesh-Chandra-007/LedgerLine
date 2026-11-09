# Build the React UI first, then package it into Spring Boot's static resources.
FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# Build the Spring Boot application with Java 21.
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn --batch-mode dependency:go-offline
COPY backend/src ./src
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
RUN mvn --batch-mode -DskipTests package

# Small non-root runtime image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S -g 10001 ledgerline && adduser -S -u 10001 -G ledgerline ledgerline
COPY --from=backend-build --chown=ledgerline:ledgerline /workspace/backend/target/ledgerline-api-0.0.1-SNAPSHOT.jar /app/ledgerline.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/api/health" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/ledgerline.jar"]
