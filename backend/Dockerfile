# syntax=docker/dockerfile:1.7

# ─────────────────────────────────────────────────────────────────
# Stage 1: build the executable jar with Maven
# ─────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /workspace

# 1a. Copy ONLY the pom files first so the dependency layer caches
#     even when sources change. Layer order:
#       parent pom  → modules/*/pom.xml  → app/pom.xml  → sources
COPY pom.xml ./
COPY app/pom.xml app/pom.xml
COPY modules/shared/pom.xml       modules/shared/pom.xml
COPY modules/auth/pom.xml         modules/auth/pom.xml
COPY modules/order/pom.xml        modules/order/pom.xml
COPY modules/delivery/pom.xml     modules/delivery/pom.xml
COPY modules/payment/pom.xml      modules/payment/pom.xml
COPY modules/notification/pom.xml modules/notification/pom.xml
COPY modules/bot/pom.xml          modules/bot/pom.xml
COPY modules/miniapp/pom.xml      modules/miniapp/pom.xml
COPY modules/webadmin/pom.xml     modules/webadmin/pom.xml

# 1b. Warm the local Maven cache. We resolve all dependencies of the
#     reactor (incl. test scope) but skip the actual compile.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -ntp dependency:go-offline -DskipTests || true

# 1c. Copy sources and build. Skipping tests in the container build —
#     tests are run on the host via ./mvnw verify (and in CI if added later).
COPY app/src       app/src
COPY modules       modules

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -ntp -DskipTests -pl app -am package

# 1d. The Spring Boot Maven Plugin produces app/target/delivery-app.jar
#     (finalName=delivery-app in app/pom.xml).
RUN test -f app/target/delivery-app.jar

# ─────────────────────────────────────────────────────────────────
# Stage 2: minimal JRE runtime
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Create a non-root user for the app. Spring Boot doesn't need root.
RUN addgroup -S app && adduser -S app -G app -u 1001 -h /home/app

# wget for healthcheck (curl is ~1 MB heavier on alpine; wget is busybox-bundled)
# Note: alpine's busybox wget supports -q (quiet) and -O - (stdout) — enough for /actuator/health.

WORKDIR /app
COPY --from=builder --chown=app:app /workspace/app/target/delivery-app.jar /app/app.jar

USER app

# Spring profile: prod. Reviewer overrides via env if needed.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Healthcheck — Spring Boot Actuator. Container is "unhealthy" until /actuator/health
# returns 200 (Flyway done, datasource UP, etc.).
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"' || exit 1

# ${JAVA_OPTS} expansion needs shell form; use exec form via sh -c.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
