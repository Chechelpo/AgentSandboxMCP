package io.github.chechelpo.frplm.sandboxMCP.utils.actions;

import java.util.function.Function;

public sealed interface ReadResult
        permits ReadResult.Success, ReadResult.Error {

    static Success success(
            String contents
    ) {
        return new Success(contents, false);
    }

    static Success success(
            String contents,
            boolean truncated
    ) {
        return new Success(contents, truncated);
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
            String contents,
            boolean truncated
    ) implements ReadResult {}


    record Error(
            ErrorType type,
            String message
    ) implements ReadResult {}


    enum ErrorType {
        NOT_FOUND,
        PERMISSION_DENIED,
        INVALID_PATH,
        IO_ERROR
    }
}