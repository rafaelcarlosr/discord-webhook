# ============================================================
# Stage 1 (shared): build the fat JAR
# Used by both the JVM and native targets.
# ============================================================
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Cache dependencies separately from source
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ============================================================
# Stage 2a: JVM runtime image  (default target)
# Build with:  docker build .
#              docker build --target jvm .
# Needs ~512 MB RAM at runtime.
# ============================================================
FROM eclipse-temurin:25-jre AS jvm
RUN groupadd --gid 1000 app && useradd --uid 1000 --gid app --shell /bin/false app
WORKDIR /app
COPY --from=build /build/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]

# ============================================================
# Stage 2b: GraalVM native-image builder  (optional target)
# Build with:  docker build --target native --memory=8g .
# Needs ~8 GB RAM to compile; produces a ~50 MB binary.
# ============================================================
FROM ghcr.io/graalvm/native-image-community:21 AS native-builder
WORKDIR /build

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q

COPY src ./src
RUN ./gradlew nativeCompile --no-daemon -x test

FROM debian:bookworm-slim AS native
RUN groupadd --gid 1000 app && useradd --uid 1000 --gid app --shell /bin/false app
WORKDIR /app
COPY --from=native-builder /build/build/native/nativeCompile/interaction-gateway .
RUN chmod +x interaction-gateway
USER app
EXPOSE 8080
ENTRYPOINT ["./interaction-gateway"]
