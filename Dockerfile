# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests \
    && cp target/AgentSandboxMCP-*.jar /tmp/agent-sandbox-mcp.jar

FROM eclipse-temurin:25-jre-noble AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends bubblewrap curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /tmp/agent-sandbox-mcp.jar /app/agent-sandbox-mcp.jar

ENV SERVER_ADDRESS=0.0.0.0

VOLUME ["/root/.sandbox-mcp"]
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/login >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/agent-sandbox-mcp.jar"]
