package io.github.chechelpo.frplm.sandboxMCP.mcp;

import io.github.chechelpo.frplm.sandboxMCP.sessions.Session;
import io.github.chechelpo.frplm.sandboxMCP.sessions.SessionManager;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ExecutionPolicy;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.SandboxRequest;
import io.github.chechelpo.frplm.sandboxMCP.workspace.StableWorkspaceManager;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class WorkspaceTools {

    private final SessionManager sessions;
    private final StableWorkspaceManager stableWorkspaces;

    public WorkspaceTools(
            SessionManager sessions,
            StableWorkspaceManager stableWorkspaces
    ) {
        this.sessions = sessions;
        this.stableWorkspaces = stableWorkspaces;
    }


    @McpTool(
            name = "execute",
            description = "Execute a command in the caller's transient workspace, or "
                    + "in a stable workspace when workspaceName is provided"
    )
    public CommandResponse execute(
            @McpToolParam(required = true)
            String command,

            @McpToolParam(required = false)
            List<String> args,

            @McpToolParam(
                    required = false,
                    description = "Stable workspace name; omit to use the caller's transient workspace"
            )
            String workspaceName
    ) {
        return CommandResponse.parse(
                sessions.getForClient(workspaceName).workspace()
                    .run(
                            new SandboxRequest(command, args),
                            30,
                            ExecutionPolicy.DEFAULT
                    )
        );
    }

    @McpTool(
            name = "read_file",
            description = "Read a zero-based, end-exclusive range of lines from a transient or stable workspace"
    )
    public ReadResponse readFile(
            @McpToolParam(required = true) String path,
            @McpToolParam(required = true) int fromLine,
            @McpToolParam(required = true) int toLine,
            @McpToolParam(
                    required = false,
                    description = "Stable workspace name; omit to use the caller's transient workspace"
            ) String workspaceName
    ) {
        return ReadResponse.parse(
                sessions.getForClient(workspaceName).workspace().readFile(
                        Path.of(path),
                        fromLine,
                        toLine
                )
        );
    }

    @McpTool(
            name = "write_file",
            description = "Create a new file in a transient or stable workspace"
    )
    public WriteResponse writeFile(
            @McpToolParam(required = true) String path,
            @McpToolParam(required = true) String content,
            @McpToolParam(
                    required = false,
                    description = "Stable workspace name; omit to use the caller's transient workspace"
            ) String workspaceName
    ) {
        return WriteResponse.parse(
                sessions.getForClient(workspaceName).workspace()
                        .writeFile(Path.of(path), content)
        );
    }

    @McpTool(
            name = "edit_file",
            description = "Replace a zero-based, end-exclusive range of lines in a transient or stable workspace"
    )
    public EditResponse editFile(
            @McpToolParam(required = true) String path,
            @McpToolParam(required = true) String content,
            @McpToolParam(required = true) int fromLine,
            @McpToolParam(required = true) int toLine,
            @McpToolParam(
                    required = false,
                    description = "Stable workspace name; omit to use the caller's transient workspace"
            ) String workspaceName
    ) {
        return EditResponse.parse(
                sessions.getForClient(workspaceName).workspace().editFile(
                        Path.of(path),
                        content,
                        fromLine,
                        toLine
                )
        );
    }

    @McpTool(
            name = "create_stable_workspace",
            description = "Create a named, durable workspace in the configured stable-workspaces directory"
    )
    public StableWorkspaceManager.StableWorkspaceDetails createStableWorkspace(
            @McpToolParam(required = true) String name,
            @McpToolParam(required = true) String description
    ) {
        return stableWorkspaces.create(
                name,
                description,
                sessions.getCurrentIdentity()
        );
    }

    @McpTool(
            name = "list_stable_workspaces",
            description = "List stable workspace names and descriptions. Pass a name "
                    + "as workspaceName to execute, read_file, write_file, or edit_file."
    )
    public List<StableWorkspaceManager.StableWorkspaceDetails>
    listStableWorkspaces() {
        sessions.getCurrentIdentity();
        return stableWorkspaces.list();
    }
}
