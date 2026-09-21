# AgentSandboxMCP

AgentSandboxMCP is a Spring Boot MCP server that gives agents isolated access to
managed workspaces. Its command runner uses Linux namespaces through
[bubblewrap](https://github.com/containers/bubblewrap).

## Run with Docker Compose

Docker Engine with the Compose plugin is required. Create your local environment
file and edit the credentials before starting the service:

```sh
cp .env.example .env
# Edit .env and replace SANDBOX_MCP_WEB_PASSWORD.
docker compose up --build -d
```

Compose automatically reads `.env`; the file is ignored by both Git and the
Docker build context so its credentials are not committed or copied into the
image. Open <http://127.0.0.1:8080> and sign in with the configured username and
password.

`SANDBOX_MCP_WEB_PASSWORD` is required. The username and password from `.env`
are authoritative on every startup, including when the Docker data volume
already exists. They cannot be changed through the Web UI or API. After editing
the credentials, recreate the service to apply them:

```sh
docker compose up --build --force-recreate -d
```

API keys, path configuration, and managed workspaces are stored in the
`sandbox-mcp-data` Docker volume, so they survive container replacement. Web UI
credentials are not stored in that volume. The Web UI creates the bearer API
keys used by MCP clients.

The MCP endpoint is `http://127.0.0.1:8080/mcp`. The host port can be changed,
while the container port stays the same:

Set `SANDBOX_MCP_PORT=9090` in `.env`, then run `docker compose up -d`.

Check status or stop the service with:

```sh
docker compose ps
docker compose down
```

`docker compose down` preserves application data. Add `--volumes` only when you
intentionally want to delete all persisted configuration and workspaces.

## Security notes

The published port is bound to host loopback by default. Put an authenticated
TLS reverse proxy in front of the service before making it reachable from other
machines.

Bubblewrap creates nested namespaces and bind mounts, so the Compose service is
granted `SYS_ADMIN` and unconfined AppArmor, seccomp, and system-path profiles.
These permissions apply to the container, not directly to sandboxed agent
commands, but they are security-sensitive. Run the service only on a trusted
Docker host and do not mount sensitive host directories into it.

## Build or run the image directly

Compose supplies the Linux permissions needed by the command sandbox. To build
the image separately:

```sh
docker build -t agent-sandbox-mcp .
```

For a direct container run, provide the equivalent permissions and persistent
storage:

```sh
docker run --rm \
  --init \
  --env-file .env \
  --cap-add SYS_ADMIN \
  --security-opt apparmor=unconfined \
  --security-opt seccomp=unconfined \
  --security-opt systempaths=unconfined \
  -p 127.0.0.1:8080:8080 \
  -v sandbox-mcp-data:/root/.sandbox-mcp \
  agent-sandbox-mcp
```

## Local development

The non-containerized test suite and application can be run with the Maven
wrapper. Local command-sandbox tests require `bwrap` on Linux.

```sh
SANDBOX_MCP_WEB_PASSWORD='local-development-password' ./mvnw test
SANDBOX_MCP_WEB_PASSWORD='local-development-password' ./mvnw spring-boot:run
```
