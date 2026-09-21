package io.github.chechelpo.frplm.sandboxMCP.workspace;

import java.io.InputStream;

/// # Subprocess
///
/// An agent created subprocess, running in a workspace through a sandbox. Supports:
///
/// 1. **Termination**
/// 2. **Reading output**
/// 3. **Stdin input from the agent**
public interface Subprocess extends AutoCloseable {

    /**
     * Unique identifier of this process instance.
     */
    String id();

    /**
     * Process exit status. Empty while still running.
     */
    java.util.Optional<Integer> exitCode();

    /**
     * Whether the process is still alive.
     */
    boolean isAlive();

    /**
     * Standard output stream.
     */
    InputStream stdout();

    /**
     * Standard error stream.
     */
    InputStream stderr();

    /**
     * Send termination signal.
     */
    void terminate();

    /**
     * Force kill if graceful termination fails.
     */
    void kill();

    @Override
    default void close() {
        terminate();
    }
}