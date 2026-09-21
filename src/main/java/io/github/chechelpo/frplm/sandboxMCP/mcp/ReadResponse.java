package io.github.chechelpo.frplm.sandboxMCP.mcp;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ReadResult;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record ReadResponse(
        boolean success,
        String contents,
        boolean truncated,
        String error
) {

    @Contract("_ -> new")
    public static @NonNull ReadResponse fromSuccess(
            ReadResult.@NonNull Success success
    ) {
        return new ReadResponse(
                true,
                success.contents(),
                success.truncated(),
                ""
        );
    }

    @Contract("_ -> new")
    public static @NonNull ReadResponse fromError(
            ReadResult.@NonNull Error error
    ) {
        return new ReadResponse(
                false,
                "",
                false,
                error.message()
        );
    }

    public static ReadResponse parse(
            ReadResult result
    ) {
        Objects.requireNonNull(result);

        return result.fold(
                ReadResponse::fromSuccess,
                ReadResponse::fromError
        );
    }
}
