# CLAUDE.md — Discord Interaction Gateway

## Project Overview

**Discord Interaction Gateway** (`dev.discord:interaction-gateway`) is a stateless Spring Boot 4 API gateway that receives Discord Interaction webhook events, verifies their Ed25519 signatures, and forwards the payloads onto a Redis Stream for downstream consumers.

## Tech Stack

| Layer       | Technology                              |
|-------------|------------------------------------------|
| Language    | Java 25                                  |
| Framework   | Spring Boot 4.0.0                        |
| Build       | Gradle 8.14.3 (Kotlin DSL)               |
| Redis       | Spring Data Redis (Lettuce driver)       |
| Native      | GraalVM Native Image (optional profile)  |
| Container   | Docker multi-stage (GraalVM 25 builder)  |
| Deployment  | Kubernetes + KEDA HTTP autoscaling       |

## Project Structure

```
discord-webhook/
├── build.gradle.kts          # Gradle build script (Kotlin DSL)
├── settings.gradle.kts       # Root project settings
├── gradlew / gradlew.bat     # Gradle wrapper scripts
├── gradle/wrapper/           # Gradle wrapper configuration
├── src/
│   ├── main/
│   │   ├── java/dev/discord/gateway/
│   │   │   ├── GatewayApplication.java          # Spring Boot entry point
│   │   │   ├── config/GatewayConfig.java         # Filter + verifier beans
│   │   │   ├── controller/InteractionController.java
│   │   │   ├── crypto/Ed25519Verifier.java       # Signature verification
│   │   │   ├── filter/SignatureVerificationFilter.java
│   │   │   └── filter/CachedBodyHttpServletRequest.java
│   │   └── resources/application.yml
│   └── test/
│       └── java/dev/discord/gateway/
│           ├── controller/InteractionControllerTest.java
│           ├── crypto/Ed25519VerifierTest.java
│           └── filter/SignatureVerificationFilterTest.java
├── Dockerfile                # Multi-stage: GraalVM native build → debian-slim
├── k8s/                      # Kubernetes manifests
│   ├── namespace.yml
│   ├── deployment.yml
│   ├── service.yml
│   ├── ingress-gke.yml
│   ├── keda-http-scaled-object.yml
│   └── secret.yml
├── CLAUDE.md                 # This file
└── README.md
```

## Common Commands

```bash
# Run locally (JVM mode)
./gradlew bootRun

# Run tests
./gradlew test

# Build fat JAR
./gradlew bootJar

# Build GraalVM native image
./gradlew nativeCompile

# Run native binary (after nativeCompile)
./build/native/nativeCompile/interaction-gateway

# Docker: build native image container
docker build --memory=8g -t interaction-gateway .

# Docker: run locally
docker run -e DISCORD_PUBLIC_KEY=<hex-key> \
           -e REDIS_HOST=localhost \
           -p 8080:8080 interaction-gateway
```

## Environment Variables

| Variable           | Required | Default     | Description                                 |
|--------------------|----------|-------------|---------------------------------------------|
| `DISCORD_PUBLIC_KEY` | Yes    | —           | Hex-encoded Ed25519 public key (Discord Dev Portal) |
| `REDIS_HOST`       | No       | `localhost` | Redis server host                           |
| `REDIS_PORT`       | No       | `6379`      | Redis server port                           |
| `REDIS_PASSWORD`   | No       | (empty)     | Redis AUTH password                         |

## Key Design Decisions

- **Stateless gateway**: no DB, no session state — scales to zero safely with KEDA.
- **Ed25519 signature verification** happens in a servlet filter before the controller, returning `401` for invalid/missing signatures — a Discord security requirement.
- **Redis Streams** (`discord:events` key): chosen over pub/sub for durability and consumer-group replay support.
- **GraalVM Native Image**: optional; drastically reduces cold-start time for serverless deployments. Compile with `./gradlew nativeCompile`.

## Development Notes

- Java 25 uses `--enable-preview` features. Gradle is configured to pass preview flags for both `compileJava` and `test` tasks.
- The Gradle wrapper targets **Gradle 8.14.3**. Do not upgrade without testing native compilation.
- Tests use `spring-boot-starter-test` (JUnit 5 + Mockito). Run with `./gradlew test`.
