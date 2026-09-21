package io.github.chechelpo.frplm.sandboxMCP.sandbox;

import io.github.chechelpo.frplm.sandboxMCP.utils.actions.CommandResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.EditResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ExecutionPolicy;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.ReadResult;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.SandboxRequest;
import io.github.chechelpo.frplm.sandboxMCP.utils.actions.WriteResult;
import io.github.chechelpo.frplm.sandboxMCP.workspace.Subprocess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class LinuxSandbox implements Sandbox {

    private static final Path WORKSPACE = Path.of("/workspace");
    private static final String DEFAULT_PATH =
            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    private final List<BindMount> mounts;
    private final BindMount workspaceMount;

    LinuxSandbox(List<BindMount> configuredMounts) {
        Objects.requireNonNull(configuredMounts, "configuredMounts");

        Map<Path, BindMount> byGuest = new LinkedHashMap<>();
        configuredMounts.forEach(mount -> addMount(byGuest, mount));
        linuxDefaults().forEach(mount -> {
            if (Files.exists(mount.host())) {
                byGuest.putIfAbsent(mount.guest(), mount);
            }
        });

        this.mounts = List.copyOf(byGuest.values());
        this.workspaceMount = this.mounts.stream()
                .filter(mount -> mount.guest().equals(WORKSPACE))
                .findFirst()
                .orElse(null);
    }

    private static List<BindMount> linuxDefaults() {
        return List.of(
                BindMount.ofSimple(Path.of("/bin"), BindMount.Access.READ),
                BindMount.ofSimple(Path.of("/sbin"), BindMount.Access.READ),
                BindMount.ofSimple(Path.of("/usr"), BindMount.Access.READ),
                BindMount.ofSimple(Path.of("/lib"), BindMount.Access.READ),
                BindMount.ofSimple(Path.of("/lib64"), BindMount.Access.READ),
                BindMount.ofSimple(Path.of("/etc"), BindMount.Access.READ)
        );
    }

    private static void addMount(Map<Path, BindMount> mounts, BindMount mount) {
        Objects.requireNonNull(mount, "mount");
        BindMount previous = mounts.putIfAbsent(mount.guest(), mount);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate sandbox mount: " + mount.guest());
        }
    }

    @Override
    public CommandResult execute(SandboxRequest request, ExecutionPolicy policy) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        Instant startedAt = Instant.now();
        Process process = null;

        try {
            process = startProcess(request, policy.captureOutput());
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());

            boolean finished = process.waitFor(
                    policy.timeout().toNanos(),
                    TimeUnit.NANOSECONDS
            );

            if (!finished) {
                terminate(process, policy.killOnTimeout());
                return new CommandResult.Error(
                        CommandResult.ErrorType.TIMEOUT,
                        "Command timed out after " + policy.timeout(),
                        joinOutput(stderr),
                        process.isAlive() ? null : process.exitValue(),
                        Duration.between(startedAt, Instant.now())
                );
            }

            String standardOutput = joinOutput(stdout);
            String standardError = joinOutput(stderr);
            Duration executionTime = Duration.between(startedAt, Instant.now());
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return new CommandResult.Error(
                        CommandResult.ErrorType.PROCESS_FAILED,
                        "Command exited with status " + exitCode,
                        standardError,
                        exitCode,
                        executionTime
                );
            }

            return CommandResult.success(
                    exitCode,
                    standardOutput,
                    standardError,
                    executionTime
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return CommandResult.error(
                    CommandResult.ErrorType.SANDBOX_FAILURE,
                    "Command execution was interrupted"
            );
        } catch (IOException | IllegalArgumentException e) {
            return CommandResult.error(
                    CommandResult.ErrorType.SANDBOX_FAILURE,
                    "Could not start sandboxed command: " + e.getMessage()
            );
        }
    }

    @Override
    public Subprocess startSubprocess(SandboxRequest request, ExecutionPolicy policy) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        try {
            return new RunningSubprocess(startProcess(request, true));
        } catch (IOException e) {
            throw new IllegalStateException("Could not start sandboxed subprocess", e);
        }
    }

    @Override
    public ReadResult read(Path path, int fromLine, int toLine) {
        if (fromLine < 0 || toLine < fromLine) {
            return ReadResult.error(ReadResult.ErrorType.INVALID_PATH, "Invalid line range");
        }

        try {
            Path resolved = resolveExistingWorkspacePath(path);
            String contents = Files.readString(resolved);
            List<Integer> lines = lineStarts(contents);
            int lineCount = lineCount(contents, lines);

            if (fromLine > lineCount || toLine > lineCount) {
                return ReadResult.error(
                        ReadResult.ErrorType.INVALID_PATH,
                        "Line range exceeds file length of " + lineCount
                );
            }

            int start = offsetForLine(contents, lines, fromLine);
            int end = offsetForLine(contents, lines, toLine);
            return ReadResult.success(
                    contents.substring(start, end),
                    fromLine > 0 || toLine < lineCount
            );
        } catch (NoSuchFileException e) {
            return ReadResult.error(ReadResult.ErrorType.NOT_FOUND, "File not found: " + path);
        } catch (AccessDeniedException | SecurityException e) {
            return ReadResult.error(ReadResult.ErrorType.PERMISSION_DENIED, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ReadResult.error(ReadResult.ErrorType.INVALID_PATH, e.getMessage());
        } catch (IOException e) {
            return ReadResult.error(ReadResult.ErrorType.IO_ERROR, e.getMessage());
        }
    }

    @Override
    public EditResult edit(Path path, String content, int fromLine, int toLine) {
        Objects.requireNonNull(content, "content");
        if (fromLine < 0 || toLine < fromLine) {
            return EditResult.error(EditResult.ErrorType.INVALID_EDIT, "Invalid line range");
        }

        try {
            Path resolved = resolveExistingWorkspacePath(path);
            ensureWritableWorkspace();
            String original = Files.readString(resolved);
            List<Integer> lines = lineStarts(original);
            int lineCount = lineCount(original, lines);
            if (fromLine > lineCount || toLine > lineCount) {
                return EditResult.error(
                        EditResult.ErrorType.INVALID_EDIT,
                        "Line range exceeds file length of " + lineCount
                );
            }

            int start = offsetForLine(original, lines, fromLine);
            int end = offsetForLine(original, lines, toLine);
        String replacement = content;
            if (fromLine == toLine
                    && fromLine == lineCount
                    && !original.isEmpty()
                    && !original.endsWith("\n")
                    && !replacement.isEmpty()) {
                replacement = "\n" + replacement;
            }
            if (end < original.length()
                    && !replacement.isEmpty()
                    && !replacement.endsWith("\n")) {
                replacement += "\n";
            }
            String updated = original.substring(0, start) + replacement + original.substring(end);
            if (updated.equals(original)) {
                return EditResult.success(0, 0);
            }

            Files.writeString(
                    resolved,
                    updated,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            int replacements = Math.max(1, toLine - fromLine);
            return EditResult.success(replacements, updated.getBytes(StandardCharsets.UTF_8).length);
        } catch (NoSuchFileException e) {
            return EditResult.error(EditResult.ErrorType.FILE_NOT_FOUND, "File not found: " + path);
        } catch (AccessDeniedException | SecurityException e) {
            return EditResult.error(EditResult.ErrorType.PERMISSION_DENIED, e.getMessage());
        } catch (IllegalArgumentException e) {
            return EditResult.error(EditResult.ErrorType.INVALID_PATH, e.getMessage());
        } catch (IOException e) {
            return EditResult.error(EditResult.ErrorType.IO_ERROR, e.getMessage());
        }
    }

    @Override
    public WriteResult write(Path path, String content) {
        Objects.requireNonNull(content, "content");
        try {
            ensureWritableWorkspace();
            Path resolved = resolveNewWorkspacePath(path);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(
                    resolved,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            return WriteResult.success(bytes.length);
        } catch (FileAlreadyExistsException e) {
            return WriteResult.error(
                    WriteResult.ErrorType.FILE_ALREADY_EXISTS,
                    "File already exists: " + path
            );
        } catch (NoSuchFileException e) {
            return WriteResult.error(
                    WriteResult.ErrorType.PARENT_DIRECTORY_MISSING,
                    "Parent directory does not exist: " + path
            );
        } catch (AccessDeniedException | SecurityException e) {
            return WriteResult.error(WriteResult.ErrorType.PERMISSION_DENIED, e.getMessage());
        } catch (IllegalArgumentException e) {
            return WriteResult.error(WriteResult.ErrorType.INVALID_PATH, e.getMessage());
        } catch (IOException e) {
            return WriteResult.error(WriteResult.ErrorType.IO_ERROR, e.getMessage());
        }
    }

    private Process startProcess(SandboxRequest request, boolean captureOutput) throws IOException {
        validateWorkingDirectory(request.workingDirectory());
        List<String> command = new ArrayList<>();
        command.add("bwrap");
        command.addAll(List.of(
                "--die-with-parent",
                "--new-session",
                "--unshare-user-try",
                "--unshare-pid",
                "--unshare-ipc",
                "--unshare-uts",
                "--unshare-cgroup-try",
                "--proc", "/proc",
                "--tmpfs", "/tmp",
                "--dev", "/dev"
        ));

        for (BindMount mount : mounts) {
            command.add(mount.access() == BindMount.Access.WRITE ? "--bind" : "--ro-bind");
            command.add(mount.host().toString());
            command.add(mount.guest().toString());
        }

        command.add("--clearenv");
        addEnvironment(command, "HOME", WORKSPACE.toString());
        addEnvironment(command, "PATH", DEFAULT_PATH);
        request.environment().forEach((name, value) -> addEnvironment(command, name, value));
        command.add("--chdir");
        command.add(request.workingDirectory().toString());
        command.add("--");
        command.add(request.executable());
        command.addAll(request.args());

        ProcessBuilder builder = new ProcessBuilder(command);
        if (!captureOutput) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        return builder.start();
    }

    private static void addEnvironment(List<String> command, String name, String value) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid environment variable name: " + name);
        }
        command.add("--setenv");
        command.add(name);
        command.add(value);
    }

    private void validateWorkingDirectory(Path workingDirectory) {
        Path normalized = workingDirectory.normalize();
        boolean mounted = mounts.stream().anyMatch(mount -> normalized.startsWith(mount.guest()));
        if (!normalized.isAbsolute() || !mounted) {
            throw new IllegalArgumentException("Working directory is outside the sandbox mounts");
        }
    }

    private Path resolveExistingWorkspacePath(Path requested) throws IOException {
        Path candidate = resolveWorkspacePath(requested);
        Path realRoot = workspaceMount.host().toRealPath();
        Path realCandidate = candidate.toRealPath();
        if (!realCandidate.startsWith(realRoot)) {
            throw new SecurityException("Path escapes workspace through a symbolic link");
        }
        return realCandidate;
    }

    private Path resolveNewWorkspacePath(Path requested) throws IOException {
        Path candidate = resolveWorkspacePath(requested);
        Path parent = candidate.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("A file path is required");
        }
        Path realRoot = workspaceMount.host().toRealPath();
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw new SecurityException("Path escapes workspace through a symbolic link");
        }
        return realParent.resolve(candidate.getFileName());
    }

    private Path resolveWorkspacePath(Path requested) {
        Objects.requireNonNull(requested, "path");
        if (workspaceMount == null) {
            throw new IllegalStateException("Sandbox has no /workspace mount");
        }
        Path guestPath = requested.isAbsolute()
                ? requested.normalize()
                : WORKSPACE.resolve(requested).normalize();
        if (!guestPath.startsWith(WORKSPACE) || guestPath.equals(WORKSPACE)) {
            throw new IllegalArgumentException("Path must name a file inside /workspace");
        }
        Path relative = WORKSPACE.relativize(guestPath);
        Path candidate = workspaceMount.host().resolve(relative).normalize();
        if (!candidate.startsWith(workspaceMount.host())) {
            throw new SecurityException("Path escapes workspace");
        }
        return candidate;
    }

    private void ensureWritableWorkspace() throws AccessDeniedException {
        if (workspaceMount == null || workspaceMount.access() != BindMount.Access.WRITE) {
            throw new AccessDeniedException("/workspace is read-only");
        }
    }

    private static List<Integer> lineStarts(String contents) {
        List<Integer> starts = new ArrayList<>();
        if (!contents.isEmpty()) {
            starts.add(0);
            for (int i = 0; i < contents.length(); i++) {
                if (contents.charAt(i) == '\n' && i + 1 < contents.length()) {
                    starts.add(i + 1);
                }
            }
        }
        return starts;
    }

    private static int lineCount(String contents, List<Integer> starts) {
        return contents.isEmpty() ? 0 : starts.size();
    }

    private static int offsetForLine(String contents, List<Integer> starts, int line) {
        return line == starts.size() ? contents.length() : starts.get(line);
    }

    private static CompletableFuture<String> readAsync(InputStream input) {
        return CompletableFuture.supplyAsync(() -> {
            try (input) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static String joinOutput(CompletableFuture<String> output) {
        try {
            return output.join();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static void terminate(Process process, boolean force) throws InterruptedException {
        if (force) {
            process.destroyForcibly();
        } else {
            process.destroy();
        }
        process.waitFor(2, TimeUnit.SECONDS);
        if (process.isAlive()) {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    private static final class RunningSubprocess implements Subprocess {
        private final String id = UUID.randomUUID().toString();
        private final Process process;

        private RunningSubprocess(Process process) {
            this.process = process;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Optional<Integer> exitCode() {
            try {
                return Optional.of(process.exitValue());
            } catch (IllegalThreadStateException ignored) {
                return Optional.empty();
            }
        }

        @Override
        public boolean isAlive() {
            return process.isAlive();
        }

        @Override
        public InputStream stdout() {
            return process.getInputStream();
        }

        @Override
        public InputStream stderr() {
            return process.getErrorStream();
        }

        @Override
        public void terminate() {
            process.destroy();
        }

        @Override
        public void kill() {
            process.destroyForcibly();
        }
    }
}
