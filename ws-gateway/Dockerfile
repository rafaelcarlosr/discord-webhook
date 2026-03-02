# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /build

COPY gradle gradle
COPY gradlew gradlew.bat ./
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN ./gradlew bootJar --no-daemon -q

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

COPY --from=builder /build/build/libs/ws-gateway-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
