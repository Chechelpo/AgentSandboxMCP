package io.github.chechelpo.frplm.sandboxMCP.workspace;

import io.github.chechelpo.frplm.sandboxMCP.config.PathManager;
import io.github.chechelpo.frplm.sandboxMCP.sandbox.BindMount;
import io.github.chechelpo.frplm.sandboxMCP.sandbox.Sandbox;
import io.github.chechelpo.frplm.sandboxMCP.sandbox.SandboxFactory;
import io.github.chechelpo.frplm.sandboxMCP.sessions.ClientIdentity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// # Workspace factory
///
/// Factory in charge of creating and assigning workspaces for agents.
@Component
public class WorkspaceFactory {

    private final PathManager pathManager;
    private final SandboxFactory sandboxFactory;

    public WorkspaceFactory(PathManager pathManager, SandboxFactory sandboxFactory) {
        this.pathManager = pathManager;
        this.sandboxFactory = sandboxFactory;
    }

    /// Creates a workspace folder based on the client identity.
    public Workspace getForIdentity(ClientIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        Path rootWorkspace = pathManager.getPathFor(
                PathManager.Values.AGENT_WORKSPACES
        );

        Path agentWorkspace = rootWorkspace.resolve(
                identity.workspaceId()
        );

        return createForDirectory(agentWorkspace);
    }

    public Workspace getForStableWorkspace(UUID id) {
        Objects.requireNonNull(id, "id");

        Path stableRoot = pathManager.getPathFor(
                PathManager.Values.STABLE_WORKSPACES
        );
        return createForDirectory(stableRoot.resolve(id.toString()));
    }

    private Workspace createForDirectory(Path workspaceDirectory) {
        createWorkspaceDirectory(workspaceDirectory);

        Sandbox agentSandbox = sandboxFactory.create(
                List.of(
                        BindMount.of(
                                workspaceDirectory,
                                Path.of("/workspace"),
                                BindMount.Access.WRITE
                        )
                )
        );

        return new AgentWorkspace(agentSandbox);
    }

    private void createWorkspaceDirectory(Path workspace) {
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create workspace: " + workspace,
                    e
            );
        }
    }
}
