package io.github.chechelpo.frplm.sandboxMCP.utils.actions;

import java.util.function.Function;

public sealed interface EditResult
        permits EditResult.Success, EditResult.Error {

    static Success success(
            int replacements,
            long bytesWritten
    ) {
        return new Success(replacements, bytesWritten);
    }

    static Success success(
            int replacements
    ) {
        return new Success(replacements, 0);
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
            int replacements,
            long bytesWritten
    ) implements EditResult {

        public boolean changed() {
            return replacements > 0;
        }
    }


    record Error(
            ErrorType type,
            String message
    ) implements EditResult {}


    enum ErrorType {
        FILE_NOT_FOUND,
        PERMISSION_DENIED,
        INVALID_PATH,
        INVALID_EDIT,
        NO_MATCH_FOUND,
        AMBIGUOUS_MATCH,
        IO_ERROR
    }
}