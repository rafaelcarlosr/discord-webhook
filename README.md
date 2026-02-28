# Discord Interaction Gateway

A **stateless API gateway** for Discord Interaction webhooks. It verifies Ed25519 signatures and forwards event payloads onto a **Redis Stream** for downstream processing — no in-memory state, no sessions, safe to scale to zero.

## Architecture

```
Discord ──POST /api/discord/interactions──► Gateway (Spring Boot)
                                                │
                                    Ed25519 signature verified
                                                │
                                      ┌─────────▼──────────┐
                                      │  Redis Stream       │
                                      │  discord:events     │
                                      └────────────────────┘
                                                │
                                      Downstream consumers
```

- **PING** interactions receive an immediate `{"type":1}` pong.
- All other interactions get `{"type":5}` (deferred) and are pushed onto the stream.

## Requirements

| Tool     | Version  |
|----------|----------|
| Java     | 25       |
| Gradle   | 8.14.3 (wrapper included) |
| Redis    | 7+       |
| Docker   | 24+ (for containerised builds) |

> **GraalVM Native Image** compilation requires ~8 GB RAM and GraalVM 25 JDK.

## Quick Start

### 1. Clone and configure

```bash
git clone <repo-url>
cd discord-webhook
```

Set your Discord public key and Redis connection via environment variables (see [Configuration](#configuration)).

### 2. Run (JVM mode)

```bash
DISCORD_PUBLIC_KEY=<hex-key> ./gradlew bootRun
```

### 3. Run tests

```bash
./gradlew test
```

### 4. Build a fat JAR

```bash
./gradlew bootJar
java --enable-preview -jar build/libs/interaction-gateway-0.1.0-SNAPSHOT.jar
```

### 5. Build & run native binary

```bash
./gradlew nativeCompile          # requires GraalVM 25
./build/native/nativeCompile/interaction-gateway
```

### 6. Docker

```bash
# Build (native image — needs ~8 GB RAM)
docker build --memory=8g -t interaction-gateway .

# Run
docker run \
  -e DISCORD_PUBLIC_KEY=<hex-key> \
  -e REDIS_HOST=localhost \
  -p 8080:8080 \
  interaction-gateway
```

## Configuration

All configuration is driven by environment variables:

| Variable             | Required | Default     | Description                                          |
|----------------------|----------|-------------|------------------------------------------------------|
| `DISCORD_PUBLIC_KEY` | **Yes**  | —           | Hex-encoded Ed25519 public key from the Discord Developer Portal |
| `REDIS_HOST`         | No       | `localhost` | Redis server hostname                                |
| `REDIS_PORT`         | No       | `6379`      | Redis server port                                    |
| `REDIS_PASSWORD`     | No       | *(empty)*   | Redis AUTH password                                  |

## API

| Method | Path                          | Description                        |
|--------|-------------------------------|------------------------------------|
| `POST` | `/api/discord/interactions`   | Receive Discord interaction events |

### Request headers (required by Discord)

| Header                   | Description                              |
|--------------------------|------------------------------------------|
| `X-Signature-Ed25519`    | Hex-encoded Ed25519 signature            |
| `X-Signature-Timestamp`  | Unix timestamp used in signature payload |

### Responses

| Condition           | HTTP | Body          |
|---------------------|------|---------------|
| Missing/bad signature | 401 | Error message |
| PING interaction    | 200  | `{"type":1}`  |
| Any other event     | 200  | `{"type":5}`  |

## Project Structure

```
discord-webhook/
├── build.gradle.kts                    # Gradle build (Kotlin DSL)
├── settings.gradle.kts
├── gradlew / gradlew.bat               # Gradle wrapper
├── gradle/wrapper/
├── src/main/java/dev/discord/gateway/
│   ├── GatewayApplication.java
│   ├── config/GatewayConfig.java
│   ├── controller/InteractionController.java
│   ├── crypto/Ed25519Verifier.java
│   └── filter/SignatureVerificationFilter.java
├── src/main/resources/application.yml
├── src/test/...
├── Dockerfile                          # Multi-stage native image build
├── k8s/                                # Kubernetes manifests
├── CLAUDE.md                           # AI assistant context
└── README.md
```

## Kubernetes Deployment

Manifests are under `k8s/`. The deployment uses **KEDA HTTP add-on** for scale-to-zero autoscaling.

```bash
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/secret.yml          # add DISCORD_PUBLIC_KEY first
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
kubectl apply -f k8s/ingress-gke.yml
kubectl apply -f k8s/keda-http-scaled-object.yml
```

## Development Notes

- **Java 25 preview features** are enabled in the Gradle build for both compilation and tests.
- **Redis Streams** (`discord:events`) provide durability; consumers can use consumer groups for at-least-once processing.
- The **signature filter** runs before Spring's dispatcher — invalid requests never reach the controller.

## License

MIT
