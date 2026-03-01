# === Stage 1: Build GraalVM native image ===
# NOTE: native-image compilation needs ~8 GB RAM.
#       docker build --memory=8g ...
FROM ghcr.io/graalvm/native-image-community:25 AS builder
WORKDIR /build

# Copy Gradle wrapper and build files first (layer caching)
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Cache dependencies (re-downloaded only when build files change)
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q

# Build native image
COPY src ./src
RUN ./gradlew nativeCompile --no-daemon -x test

# === Stage 2: Minimal runtime image ===
FROM debian:bookworm-slim
RUN groupadd --gid 1000 app && useradd --uid 1000 --gid app --shell /bin/false app
WORKDIR /app
COPY --from=builder /build/build/native/nativeCompile/interaction-gateway .
RUN chmod +x interaction-gateway

USER app
EXPOSE 8080
ENTRYPOINT ["./interaction-gateway"]
