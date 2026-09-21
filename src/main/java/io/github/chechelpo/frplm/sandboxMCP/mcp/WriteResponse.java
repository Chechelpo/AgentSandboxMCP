package io.github.chechelpo.frplm.sandboxMCP.mcp;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.WriteResult;

import java.util.Objects;

public record WriteResponse(
        boolean success,
        long bytesWritten,
        boolean replaced,
        String error
) {
    public static WriteResponse parse(WriteResult result) {
        Objects.requireNonNull(result, "result");
        return result.fold(
                value -> new WriteResponse(
                        true,
                        value.bytesWritten(),
                        value.replaced(),
                        ""
                ),
                value -> new WriteResponse(false, 0, false, value.message())
        );
    }
}
