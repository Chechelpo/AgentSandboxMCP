package io.github.chechelpo.frplm.sandboxMCP.sandbox;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.CommandResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.EditResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ExecutionPolicy;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ReadResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.SandboxRequest;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.WriteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxSandboxTests {

    @TempDir
    Path workspace;

    @Test
    void createsReadsAndEditsWorkspaceFiles() {
        LinuxSandbox sandbox = sandbox();

        WriteResult.Success written = assertInstanceOf(
                WriteResult.Success.class,
                sandbox.write(Path.of("notes.txt"), "one\ntwo\nthree")
        );
        assertEquals(13, written.bytesWritten());

        ReadResult.Success partial = assertInstanceOf(
                ReadResult.Success.class,
                sandbox.read(Path.of("/workspace/notes.txt"), 0, 2)
        );
        assertEquals("one\ntwo\n", partial.contents());
        assertTrue(partial.truncated());

        EditResult.Success edited = assertInstanceOf(
                EditResult.Success.class,
                sandbox.edit(Path.of("notes.txt"), "changed", 1, 2)
        );
        assertEquals(1, edited.replacements());

        ReadResult.Success complete = assertInstanceOf(
                ReadResult.Success.class,
                sandbox.read(Path.of("notes.txt"), 0, 3)
        );
        assertEquals("one\nchanged\nthree", complete.contents());
    }

    @Test
    void refusesOverwriteTraversalAndSymlinkEscape() throws IOException {
        LinuxSandbox sandbox = sandbox();
        Files.writeString(workspace.resolve("existing.txt"), "value");

        WriteResult.Error duplicate = assertInstanceOf(
                WriteResult.Error.class,
                sandbox.write(Path.of("existing.txt"), "replacement")
        );
        assertEquals(WriteResult.ErrorType.FILE_ALREADY_EXISTS, duplicate.type());

        ReadResult.Error traversal = assertInstanceOf(
                ReadResult.Error.class,
                sandbox.read(Path.of("../outside.txt"), 0, 1)
        );
        assertEquals(ReadResult.ErrorType.INVALID_PATH, traversal.type());

        Path outside = Files.createTempFile("sandbox-outside", ".txt");
        try {
            Files.createSymbolicLink(workspace.resolve("link.txt"), outside);
            ReadResult.Error symlink = assertInstanceOf(
                    ReadResult.Error.class,
                    sandbox.read(Path.of("link.txt"), 0, 1)
            );
            assertEquals(ReadResult.ErrorType.PERMISSION_DENIED, symlink.type());
        } finally {
            Files.deleteIfExists(workspace.resolve("link.txt"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void executesCommandsAndReportsFailures() {
        LinuxSandbox sandbox = sandbox();

        CommandResult.Success success = assertInstanceOf(
                CommandResult.Success.class,
                sandbox.execute(
                        new SandboxRequest("/bin/sh", List.of("-c", "printf command-ok")),
                        ExecutionPolicy.DEFAULT
                )
        );
        assertEquals("command-ok", success.stdout());

        CommandResult.Error failure = assertInstanceOf(
                CommandResult.Error.class,
                sandbox.execute(
                        new SandboxRequest("/bin/sh", List.of("-c", "printf bad >&2; exit 7")),
                        ExecutionPolicy.DEFAULT
                )
        );
        assertEquals(CommandResult.ErrorType.PROCESS_FAILED, failure.type());
        assertEquals(7, failure.exitCode());
        assertEquals("bad", failure.stderr());
    }

    @Test
    void terminatesCommandsAtTheirDeadline() {
        CommandResult.Error timeout = assertInstanceOf(
                CommandResult.Error.class,
                sandbox().execute(
                        new SandboxRequest("/bin/sh", List.of("-c", "sleep 5")),
                        new ExecutionPolicy(Duration.ofMillis(100), true, true)
                )
        );

        assertEquals(CommandResult.ErrorType.TIMEOUT, timeout.type());
    }

    private LinuxSandbox sandbox() {
        return new LinuxSandbox(List.of(
                BindMount.of(workspace, Path.of("/workspace"), BindMount.Access.WRITE)
        ));
    }
}
