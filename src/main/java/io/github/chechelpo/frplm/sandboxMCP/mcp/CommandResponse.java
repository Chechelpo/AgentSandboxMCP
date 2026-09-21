package io.github.chechelpo.frplm.sandboxMCP.mcp;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.CommandResult;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record CommandResponse(
        boolean success,
        String output,
        String error,
        Integer exitCode,
        Duration executionTime,
        List<ArtifactResponse> artifacts
) {

    public CommandResponse {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(artifacts, "artifacts");
        artifacts = List.copyOf(artifacts);
    }

    @Contract("_ -> new")
    public static @NonNull CommandResponse fromSuccess(
            CommandResult.@NonNull Success success
    ) {
        return new CommandResponse(
                true,
                success.stdout(),
                success.stderr(),
                success.exitCode(),
                success.executionTime(),
                success.artifacts().stream()
                        .map(ArtifactResponse::from)
                        .toList()
        );
    }

    @Contract("_ -> new")
    public static @NonNull CommandResponse fromError(
            CommandResult.@NonNull Error error
    ) {
        return new CommandResponse(
                false,
                "",
                error.stderr().isBlank()
                        ? error.message()
                        : error.message() + System.lineSeparator() + error.stderr(),
                error.exitCode(),
                error.executionTime(),
                List.of()
        );
    }

    public static CommandResponse parse(CommandResult result){
        Objects.requireNonNull(result);

        return result.fold(
                CommandResponse::fromSuccess,
                CommandResponse::fromError
        );
    }

    public record ArtifactResponse(
            String path,
            String mimeType,
            long size
    ) {

        public static ArtifactResponse from(
                CommandResult.Artifact artifact
        ) {
            return new ArtifactResponse(
                    artifact.path(),
                    artifact.mimeType(),
                    artifact.size()
            );
        }
    }
}
