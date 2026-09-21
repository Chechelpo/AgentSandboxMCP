package io.github.chechelpo.frplm.sandboxMCP;

import io.github.chechelpo.frplm.sandboxMCP.config.ApiKeyManager;
import io.github.chechelpo.frplm.sandboxMCP.config.PathManager;
import io.github.chechelpo.frplm.sandboxMCP.config.WebUiAccountManager;
import io.github.chechelpo.frplm.sandboxMCP.sessions.ClientIdentity;
import io.github.chechelpo.frplm.sandboxMCP.workspace.StableWorkspaceManager;
import io.github.chechelpo.frplm.sandboxMCP.workspace.WorkspaceFactory;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
        "sandbox-mcp.config-file=${java.io.tmpdir}/agent-sandbox-mcp-test/paths.json",
        "sandbox-mcp.api-keys-file=${java.io.tmpdir}/agent-sandbox-mcp-test/api-keys.json",
        "sandbox-mcp.web.password=test-password"
        }
)
@AutoConfigureMockMvc
class AgentSandboxMcpApplicationTests {

    private static final String INITIALIZE_REQUEST = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": {
                "protocolVersion": "2025-06-18",
                "capabilities": {},
                "clientInfo": {
                  "name": "test",
                  "version": "1.0"
                }
              }
            }
            """;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Autowired
    MockMvc mvc;

    @Autowired
    ApiKeyManager apiKeys;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PathManager paths;

    @Autowired
    StableWorkspaceManager stableWorkspaces;

    @Autowired
    WorkspaceFactory workspaceFactory;

    @LocalServerPort
    int port;

    @TempDir
    Path temporaryDirectory;

    @Test
    void contextLoads() {
    }

    @Test
    void mcpRequestsRequireAnApiKey() throws Exception {
        HttpResponse<String> response = sendMcpInitialize(null);

        assertEquals(401, response.statusCode());
        assertEquals("Bearer", response.headers()
                .firstValue("WWW-Authenticate")
                .orElseThrow());
    }

    @Test
    void internalSseDispatchesAreNotReauthenticated() throws Exception {
        mvc.perform(get("/mcp").with(request -> {
                    request.setDispatcherType(DispatcherType.ASYNC);
                    return request;
                }))
                .andExpect(result -> assertNotEquals(
                        401,
                        result.getResponse().getStatus()
                ));

        mvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }))
                .andExpect(result -> assertNotEquals(
                        401,
                        result.getResponse().getStatus()
                ))
                .andExpect(result -> assertNotEquals(
                        403,
                        result.getResponse().getStatus()
                ));
    }

    @Test
    void configuredBearerKeyAuthenticatesMcpRequests() throws Exception {
        ApiKeyManager.CreatedApiKey key = apiKeys.create("integration-test", null);

        try {
            HttpResponse<String> response = sendMcpInitialize(key.secret());

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"protocolVersion\""));
        } finally {
            apiKeys.revoke(key.key().id());
        }
    }

    @Test
    void stableWorkspacesRequireDescriptionsAndPersist() throws Exception {
        Path stableRoot = temporaryDirectory.resolve("stable-workspaces");
        Path transientRoot = temporaryDirectory.resolve("transient-workspaces");
        paths.setPathFor(PathManager.Values.STABLE_WORKSPACES, stableRoot);
        paths.setPathFor(PathManager.Values.AGENT_WORKSPACES, transientRoot);
        ClientIdentity creator = new ClientIdentity("test-user", "api-key");

        StableWorkspaceManager.StableWorkspaceDetails created =
                stableWorkspaces.create(
                        "Reference project",
                        "Keep durable reference files for the campaign",
                        creator
                );

        assertEquals("Reference project", created.name());
        assertEquals(
                "Keep durable reference files for the campaign",
                created.description()
        );
        assertEquals("test-user", created.createdBy());
        assertTrue(Files.isDirectory(stableRoot.resolve(created.id().toString())));
        assertTrue(stableWorkspaces.list().contains(created));

        workspaceFactory.getForIdentity(creator).writeFile(
                Path.of("transient-only.txt"),
                "short-lived"
        );
        assertTrue(Files.exists(
                transientRoot.resolve(creator.workspaceId())
                        .resolve("transient-only.txt")
        ));
        assertFalse(Files.exists(
                stableRoot.resolve(created.id().toString())
                        .resolve("transient-only.txt")
        ));

        stableWorkspaces.getByName("reference PROJECT").writeFile(
                Path.of("persistent.txt"),
                "kept across sessions"
        );
        assertEquals(
                "kept across sessions",
                Files.readString(
                        stableRoot.resolve(created.id().toString()).resolve("persistent.txt")
                )
        );
        assertFalse(Files.exists(
                transientRoot.resolve(creator.workspaceId())
                        .resolve("persistent.txt")
        ));

        StableWorkspaceManager reloaded = new StableWorkspaceManager(
                paths,
                workspaceFactory,
                mapper
        );
        assertTrue(reloaded.list().contains(created));
        assertThrows(
                IllegalArgumentException.class,
                () -> reloaded.create("Missing description", " ", creator)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> reloaded.create("reference PROJECT", "Duplicate", creator)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> reloaded.getByName("unknown")
        );
    }

    @Test
    void webUiStillAcceptsItsOwnLogin() throws Exception {
        mvc.perform(get("/api/config/keys")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void webUiCanCreateAndRevokeKeys() throws Exception {
        String name = "web-ui-test-" + UUID.randomUUID();
        String secret = "asmcp_test_" + UUID.randomUUID();

        mvc.perform(post("/api/config/keys")
                        .with(httpBasic("admin", "test-password"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"" + name
                                + "\",\"key\":\"" + secret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value(secret))
                .andExpect(jsonPath("$.key.name").value(name));

        mvc.perform(get("/api/config/keys")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(secret)));
        assertTrue(Files.readString(apiKeys.configFile()).contains(secret));

        UUID id = apiKeys.list().stream()
                .filter(key -> key.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();

        mvc.perform(delete("/api/config/keys/{id}", id)
                        .with(httpBasic("admin", "test-password"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(apiKeys.list().stream().noneMatch(key -> key.id().equals(id)));
    }

    @Test
    void webUiPasswordCannotBeChanged() throws Exception {
        String changedPassword = "changed-test-password";

        mvc.perform(put("/api/config/account/password")
                        .with(httpBasic("admin", "test-password"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"password\":\"" + changedPassword + "\"}"))
                .andExpect(status().is4xxClientError());

        mvc.perform(get("/api/config/account")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
        mvc.perform(get("/api/config/account")
                        .with(httpBasic("admin", changedPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webUiPasswordAlwaysComesFromConfiguration() {
        WebUiAccountManager configured = new WebUiAccountManager(
                passwordEncoder,
                "configured-admin",
                "configured-test-password"
        );

        assertEquals("configured-admin", configured.details().username());
        assertTrue(passwordEncoder.matches(
                "configured-test-password",
                configured.loadUserByUsername("configured-admin").getPassword()
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebUiAccountManager(passwordEncoder, "admin", "")
        );
    }

    private HttpResponse<String> sendMcpInitialize(String secret)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_REQUEST));
        if (secret != null) {
            request.header("Authorization", "Bearer " + secret);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
