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
| Java     | 21+      |
| Gradle   | 9.3.1 (wrapper included) |
| Redis    | 7+       |
| Docker   | 24+ (for containerised builds) |

> **GraalVM Native Image** compilation requires ~8 GB RAM and GraalVM 21 JDK.

## Quick Start

### 1. Get your Discord Public Key

Go to the [Discord Developer Portal](https://discord.com/developers/applications) → your app → **General Information** → copy the **Public Key**.

### 2. Start Redis

```bash
docker run -d -p 6379:6379 redis:7
```

### 3. Run the gateway

```bash
DISCORD_PUBLIC_KEY=<your-hex-public-key> ./gradlew bootRun
```

The gateway starts on `http://localhost:8080`.

### 4. Point Discord at your endpoint

In the Discord Developer Portal → your app → **General Information** → set **Interactions Endpoint URL** to:

```
https://<your-public-host>/api/discord/interactions
```

Discord will send a PING to verify — the gateway handles it automatically.

---

## Docker

### JVM image (recommended, ~200 MB)

```bash
# Build
docker build --target jvm -t interaction-gateway .

# Run
docker run \
  -e DISCORD_PUBLIC_KEY=<hex-key> \
  -e REDIS_HOST=host.docker.internal \
  -p 8080:8080 \
  interaction-gateway
```

### Native image (optional, ~50 MB, faster cold start — needs ~8 GB RAM to build)

```bash
docker build --target native --memory=8g -t interaction-gateway:native .

docker run \
  -e DISCORD_PUBLIC_KEY=<hex-key> \
  -e REDIS_HOST=host.docker.internal \
  -p 8080:8080 \
  interaction-gateway:native
```

---

## Configuration

All configuration is driven by environment variables:

| Variable             | Required | Default     | Description                                          |
|----------------------|----------|-------------|------------------------------------------------------|
| `DISCORD_PUBLIC_KEY` | **Yes**  | —           | Hex-encoded Ed25519 public key from the Discord Developer Portal |
| `REDIS_HOST`         | No       | `localhost` | Redis server hostname                                |
| `REDIS_PORT`         | No       | `6379`      | Redis server port                                    |
| `REDIS_PASSWORD`     | No       | *(empty)*   | Redis AUTH password                                  |

---

## API

| Method | Path                          | Description                        |
|--------|-------------------------------|------------------------------------|
| `POST` | `/api/discord/interactions`   | Receive Discord interaction events |

### Required headers (sent by Discord automatically)

| Header                   | Description                              |
|--------------------------|------------------------------------------|
| `X-Signature-Ed25519`    | Hex-encoded Ed25519 signature            |
| `X-Signature-Timestamp`  | Unix timestamp used in signature payload |

### Responses

| Condition               | HTTP | Body          |
|-------------------------|------|---------------|
| Missing/invalid signature | 401 | Error message |
| PING interaction        | 200  | `{"type":1}`  |
| Any other event         | 200  | `{"type":5}`  |

---

## Other build commands

```bash
# Run tests
./gradlew test

# Build fat JAR
./gradlew bootJar
java -jar build/libs/interaction-gateway-0.1.0-SNAPSHOT.jar

# GraalVM native binary (requires GraalVM 21 JDK installed locally)
./gradlew nativeCompile
./build/native/nativeCompile/interaction-gateway
```

---

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

---

## Project Structure

```
discord-webhook/
├── build.gradle.kts                    # Gradle build (Kotlin DSL)
├── settings.gradle.kts
├── gradlew / gradlew.bat               # Gradle wrapper
├── src/main/java/dev/discord/gateway/
│   ├── GatewayApplication.java
│   ├── config/GatewayConfig.java
│   ├── controller/InteractionController.java
│   ├── crypto/Ed25519Verifier.java
│   ├── filter/SignatureVerificationFilter.java
│   ├── filter/CachedBodyHttpServletRequest.java
│   └── service/EventForwardingService.java
├── src/main/resources/application.yml
├── src/test/...
├── Dockerfile                          # Multi-stage: JVM + native targets
├── k8s/                                # Kubernetes manifests
└── README.md
```

## How it works

1. Discord sends a signed `POST` to `/api/discord/interactions`
2. `SignatureVerificationFilter` reads the body, verifies the Ed25519 signature using the raw bytes of `timestamp + body` — returns `401` if invalid
3. `InteractionController` checks the interaction type:
   - Type `1` (PING) → immediate `{"type":1}` response
   - Everything else → pushed to the `discord:events` Redis Stream, responds `{"type":5}` (deferred)
4. Your downstream service reads from the `discord:events` stream and handles the interaction

## License

MIT
