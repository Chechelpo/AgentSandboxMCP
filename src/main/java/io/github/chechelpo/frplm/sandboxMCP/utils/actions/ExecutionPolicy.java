package io.github.chechelpo.frplm.sandboxMCP.utils.actions;

import java.time.Duration;

public record ExecutionPolicy(
        Duration timeout,
        boolean killOnTimeout,
        boolean captureOutput
) {
    public static final ExecutionPolicy DEFAULT = new ExecutionPolicy(
            Duration.ofSeconds(30),
            true,
            true
    );

    public ExecutionPolicy {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public ExecutionPolicy withTimeout(Duration newTimeout) {
        return new ExecutionPolicy(newTimeout, killOnTimeout, captureOutput);
    }
}
