package io.github.chechelpo.frplm.sandboxMCP.workspace;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.*;

import java.nio.file.Path;
import java.util.List;

/// # Agent workspace
///
/// An agent workspace is a folder owned by a particular agent which exposes the following capabilities:
///
/// 1. **Read:** Reading files via lines, including documents
/// 2. **Write:** Creating new files with an initial content
/// 3. **Edit:** Editing existing files within its workspace
/// 4. **Bash:** Arbitrary bash use, using system provided utilities
/// 5. **Subprocess:** Creating and managing subprocesses
///
/// The sandbox must provide internal path masking, giving any of the process a sandbox through which the path _/sandbox_ is masked
/// to the workspace root.
public interface Workspace {

    /// Runs a blocking command within a timeout.
    CommandResult run(SandboxRequest command, int timeout, ExecutionPolicy policy);

    /// Starts a non-blocking subprocess
    Subprocess startSubprocess(SandboxRequest command, ExecutionPolicy policy);


    ReadResult readFile(Path path, int fromLine, int toLine);
    EditResult editFile(Path path, String content, int fromLine, int toLine);
    WriteResult writeFile(Path path, String content);

    List<Subprocess> getRunningProcess();
    List<Subprocess> getDeadSubprocess();
}
