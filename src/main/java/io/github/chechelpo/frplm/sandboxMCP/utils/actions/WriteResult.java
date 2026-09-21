package io.github.chechelpo.frplm.sandboxMCP.utils.actions;

import java.util.function.Function;

public sealed interface WriteResult
        permits WriteResult.Success, WriteResult.Error {

    static Success success(
            long bytesWritten,
            boolean replaced
    ) {
        return new Success(bytesWritten, replaced);
    }

    static Success success(
            long bytesWritten
    ) {
        return new Success(bytesWritten, false);
    }

    static Error error(
            ErrorType type,
            String message
    ) {
        return new Error(type, message);
    }


    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isError() {
        return this instanceof Error;
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


    record Success(
            long bytesWritten,
            boolean replaced
    ) implements WriteResult {}


    record Error(
            ErrorType type,
            String message
    ) implements WriteResult {}


    enum ErrorType {
        FILE_ALREADY_EXISTS,
        PERMISSION_DENIED,
        INVALID_PATH,
        PARENT_DIRECTORY_MISSING,
        DISK_FULL,
        IO_ERROR
    }
}