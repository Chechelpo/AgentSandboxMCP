package io.github.chechelpo.frplm.sandboxMCP.workspace;

import io.github.chechelpo.frplm.sandboxMCP.sandbox.Sandbox;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AgentWorkspace implements Workspace {

    private final Sandbox sandbox;

    private final List<Subprocess> runningProcesses =
            new CopyOnWriteArrayList<>();

    private final List<Subprocess> deadProcesses =
            new CopyOnWriteArrayList<>();

    public AgentWorkspace(Sandbox sandbox) {
        this.sandbox = Objects.requireNonNull(sandbox);
    }


    @Override
    public CommandResult run(
            SandboxRequest command,
            int timeout,
            ExecutionPolicy policy
    ) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(policy);

        if (timeout <= 0) {
            throw new IllegalArgumentException(
                    "timeout must be positive"
            );
        }

        validateCommand(command);

        return sandbox.execute(
                command,
                policy.withTimeout(java.time.Duration.ofSeconds(timeout))
        );
    }


    @Override
    public Subprocess startSubprocess(
            SandboxRequest command,
            ExecutionPolicy policy
    ) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(policy);

        validateCommand(command);

        Subprocess process = sandbox.startSubprocess(
                command,
                policy
        );

        runningProcesses.add(process);

        return process;
    }


    @Override
    public ReadResult readFile(
            Path path,
            int fromLine,
            int toLine
    ) {
        validateRange(fromLine, toLine);
        validatePath(path);

        return sandbox.read(
                path,
                fromLine,
                toLine
        );
    }


    @Override
    public EditResult editFile(
            Path path,
            String content,
            int fromLine,
            int toLine
    ) {
        Objects.requireNonNull(content);

        validateRange(fromLine, toLine);
        validatePath(path);

        return sandbox.edit(
                path,
                content,
                fromLine,
                toLine
        );
    }


    @Override
    public WriteResult writeFile(
            Path path,
            String content
    ) {
        Objects.requireNonNull(content);

        validatePath(path);

        return sandbox.write(
                path,
                content
        );
    }


    @Override
    public List<Subprocess> getRunningProcess() {
        cleanupProcesses();

        return List.copyOf(runningProcesses);
    }


    @Override
    public List<Subprocess> getDeadSubprocess() {
        cleanupProcesses();

        return List.copyOf(deadProcesses);
    }


    private void cleanupProcesses() {
        runningProcesses.removeIf(process -> {
            if (process.exitCode().isPresent()) {
                deadProcesses.add(process);
                return true;
            }

            return false;
        });
    }


    private void validateCommand(
            SandboxRequest command
    ) {
        if (command.executable().isBlank()) {
            throw new IllegalArgumentException(
                    "Executable cannot be blank"
            );
        }
    }


    private void validateRange(
            int fromLine,
            int toLine
    ) {
        if (fromLine < 0) {
            throw new IllegalArgumentException(
                    "fromLine cannot be negative"
            );
        }

        if (toLine < fromLine) {
            throw new IllegalArgumentException(
                    "toLine cannot be smaller than fromLine"
            );
        }
    }


    private void validatePath(Path path) {
        Objects.requireNonNull(path);

        Path normalized = path.normalize();
        boolean escapes = path.isAbsolute()
                ? !normalized.startsWith(Path.of("/workspace"))
                : normalized.startsWith("..");

        if (escapes) {
            throw new SecurityException(
                    "Path escapes workspace"
            );
        }
    }
}
