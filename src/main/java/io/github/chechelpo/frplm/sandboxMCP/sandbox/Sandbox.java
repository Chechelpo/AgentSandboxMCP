package io.github.chechelpo.frplm.sandboxMCP.sandbox;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.CommandResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ExecutionPolicy;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ReadResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.SandboxRequest;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.EditResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.WriteResult;
import io.github.chechelpo.frplm.sandboxMCP.workspace.Subprocess;

import java.nio.file.Path;

/// Sandbox through which to run commands.
public interface Sandbox {

    CommandResult execute(
            SandboxRequest request,
            ExecutionPolicy policy
    );

    Subprocess startSubprocess(
            SandboxRequest request,
            ExecutionPolicy policy
    );

    ReadResult read(
            Path path,
            int fromLine,
            int toLine
    );

    EditResult edit(
            Path path,
            String content,
            int fromLine,
            int toLine
    );

    WriteResult write(Path path, String content);
}
