# syntax=docker/dockerfile:1
# Multi-stage build: React app -> Spring Boot jar (with the React build inside) -> slim runtime image.

# 1. Frontend
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2. Backend (tests run in CI with Testcontainers, not inside the image build)
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=frontend /app/frontend/dist ./frontend/dist
RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

# 3. Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S vrms && adduser -S vrms -G vrms
COPY --from=backend /app/app.jar ./app.jar
USER vrms
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
