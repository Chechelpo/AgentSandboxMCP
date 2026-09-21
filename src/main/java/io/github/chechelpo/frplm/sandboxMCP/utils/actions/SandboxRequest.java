package io.github.chechelpo.frplm.sandboxMCP.utils.actions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SandboxRequest(
        String executable,
        List<String> args,
        Map<String, String> environment,
        Path workingDirectory
) {
    public SandboxRequest(String executable, List<String> args) {
        this(executable, args == null ? List.of() : args, Map.of(), Path.of("/workspace"));
    }

    public SandboxRequest(String executable, List<String> args, Map<String, String> environment) {
        this(executable, args == null ? List.of() : args, environment, Path.of("/workspace"));
    }

    public SandboxRequest {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(workingDirectory, "workingDirectory");

        if (executable.isBlank()) {
            throw new IllegalArgumentException(
                    "Executable cannot be blank"
            );
        }

        if (!workingDirectory.isAbsolute()) {
            throw new IllegalArgumentException("workingDirectory must be an absolute sandbox path");
        }

        args = List.copyOf(args);
        environment = Map.copyOf(environment);
        workingDirectory = workingDirectory.normalize();
    }
}
