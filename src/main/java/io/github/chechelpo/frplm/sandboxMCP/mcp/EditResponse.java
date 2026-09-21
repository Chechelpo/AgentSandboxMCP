package io.github.chechelpo.frplm.sandboxMCP.mcp;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.EditResult;

import java.util.Objects;

public record EditResponse(
        boolean success,
        int replacements,
        long bytesWritten,
        String error
) {
    public static EditResponse parse(EditResult result) {
        Objects.requireNonNull(result, "result");
        return result.fold(
                value -> new EditResponse(
                        true,
                        value.replacements(),
                        value.bytesWritten(),
                        ""
                ),
                value -> new EditResponse(false, 0, 0, value.message())
        );
    }
}
