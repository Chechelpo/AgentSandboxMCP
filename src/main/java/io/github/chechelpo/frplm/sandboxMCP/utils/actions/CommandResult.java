package io.github.chechelpo.frplm.sandboxMCP.utils.actions;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface CommandResult
        permits CommandResult.Success, CommandResult.Error {

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isError() {
        return this instanceof Error;
    }

    default CommandResult ifSuccess(Consumer<Success> consumer) {
        if (this instanceof Success success) {
            consumer.accept(success);
        }

        return this;
    }

    default CommandResult ifError(Consumer<Error> consumer) {
        if (this instanceof Error error) {
            consumer.accept(error);
        }

        return this;
    }

    default <T> T fold(
            Function<Success, T> onSuccess,
            Function<Error, T> onError
    ) {
        return switch (this) {
            case Success success -> onSuccess.apply(success);
            case Error error -> onError.apply(error);
        };
    }


    @Contract("_, _, _, _ -> new")
    static @NonNull Success success(
            int exitCode,
            String stdout,
            String stderr,
            Duration executionTime
    ) {
        return new Success(
                exitCode,
                stdout,
                stderr,
                executionTime,
                List.of()
        );
    }

    record Success(
            int exitCode,
            String stdout,
            String stderr,
            Duration executionTime,
            List<Artifact> artifacts
    ) implements CommandResult {

        public Success {
            artifacts = List.copyOf(artifacts);
        }

        public boolean hasOutput() {
            return !stdout.isBlank();
        }
    }


    @Contract("_, _ -> new")
    static @NonNull Error error(
            ErrorType type,
            String message
    ) {
        return new Error(
                type,
                message,
                "",
                null,
                null
        );
    }

    record Error(
            ErrorType type,
            String message,
            String stderr,
            Integer exitCode,
            Duration executionTime
    ) implements CommandResult {}


    enum ErrorType {
        INVALID_COMMAND,
        PERMISSION_DENIED,
        TIMEOUT,
        PROCESS_FAILED,
        IO_ERROR,
        SANDBOX_FAILURE
    }


    record Artifact(
            String path,
            String mimeType,
            long size
    ) {}
}