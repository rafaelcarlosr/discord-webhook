# === Stage 1: Build GraalVM native image ===
# NOTE: native-image compilation needs ~8 GB RAM.
#       docker build --memory=8g ...
FROM ghcr.io/graalvm/native-image-community:25 AS builder
WORKDIR /build

# Install Maven
ARG MAVEN_VERSION=3.9.9
ADD https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz /tmp/maven.tar.gz
RUN tar xzf /tmp/maven.tar.gz -C /opt && rm /tmp/maven.tar.gz
ENV PATH="/opt/apache-maven-${MAVEN_VERSION}/bin:${PATH}"

# Cache dependencies (re-downloaded only when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build native image
COPY src ./src
RUN mvn -Pnative native:compile -DskipTests

# === Stage 2: Minimal runtime image ===
FROM debian:bookworm-slim
RUN groupadd --gid 1000 app && useradd --uid 1000 --gid app --shell /bin/false app
WORKDIR /app
COPY --from=builder /build/target/interaction-gateway .
RUN chmod +x interaction-gateway

USER app
EXPOSE 8080
ENTRYPOINT ["./interaction-gateway"]
