package io.github.chechelpo.frplm.sandboxMCP.workspace;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.github.chechelpo.frplm.sandboxMCP.config.PathManager;
import io.github.chechelpo.frplm.sandboxMCP.sessions.ClientIdentity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

///
/// A stable workspace is a:
///
/// 1. Workspace on a separate directory than transient agent workspace
/// 2. A workspace with a name and description (defined by the agent that created it)
///
/// These workspaces can be modified by adding the workspace-name param to any of the tools.
@Component
public final class StableWorkspaceManager {

    private static final String REGISTRY_FILE = ".stable-workspaces.json";

    private final PathManager paths;
    private final WorkspaceFactory workspaces;
    private final ObjectMapper mapper;
    private final ConcurrentMap<UUID, Workspace> opened = new ConcurrentHashMap<>();

    public StableWorkspaceManager(
            PathManager paths,
            WorkspaceFactory workspaces,
            ObjectMapper mapper
    ) {
        this.paths = paths;
        this.workspaces = workspaces;
        this.mapper = mapper;
    }

    public synchronized StableWorkspaceDetails create(
            String name,
            String description,
            ClientIdentity creator
    ) {
        String normalizedName = requireText(name, "name", 100);
        String normalizedDescription = requireText(
                description,
                "description",
                1_000
        );
        Objects.requireNonNull(creator, "creator");

        List<StableWorkspaceDetails> existing = load();
        if (existing.stream().anyMatch(workspace ->
                workspace.name().equalsIgnoreCase(normalizedName))) {
            throw new IllegalArgumentException(
                    "A stable workspace named '" + normalizedName + "' already exists"
            );
        }

        StableWorkspaceDetails created = new StableWorkspaceDetails(
                UUID.randomUUID(),
                normalizedName,
                normalizedDescription,
                Instant.now(),
                creator.subject()
        );
        createDirectory(created.id());

        List<StableWorkspaceDetails> updated = new ArrayList<>(existing);
        updated.add(created);
        save(updated);
        return created;
    }

    public synchronized List<StableWorkspaceDetails> list() {
        return load().stream()
                .sorted(Comparator.comparing(StableWorkspaceDetails::createdAt))
                .toList();
    }

    public Workspace get(UUID id) {
        Objects.requireNonNull(id, "id");
        boolean exists = list().stream().anyMatch(workspace -> workspace.id().equals(id));
        if (!exists) {
            throw new IllegalArgumentException("Unknown stable workspace: " + id);
        }
        return opened.computeIfAbsent(id, workspaces::getForStableWorkspace);
    }

    public Workspace getByName(String name) {
        String normalizedName = requireText(name, "workspaceName", 100);
        StableWorkspaceDetails details = list().stream()
                .filter(workspace -> workspace.name().equalsIgnoreCase(normalizedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown stable workspace: " + normalizedName
                ));
        return opened.computeIfAbsent(
                details.id(),
                workspaces::getForStableWorkspace
        );
    }

    private List<StableWorkspaceDetails> load() {
        Path registry = registryFile();
        if (!Files.exists(registry)) {
            return List.of();
        }
        try {
            StableWorkspaceRegistry loaded = mapper.readValue(
                    registry.toFile(),
                    StableWorkspaceRegistry.class
            );
            return loaded.workspaces() == null
                    ? List.of()
                    : List.copyOf(loaded.workspaces());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Could not load stable workspace registry from " + registry,
                    e
            );
        }
    }

    private void save(List<StableWorkspaceDetails> values) {
        Path registry = registryFile();
        try {
            Files.createDirectories(registry.getParent());
            Path temporaryFile = Files.createTempFile(
                    registry.getParent(),
                    REGISTRY_FILE,
                    ".tmp"
            );
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(
                        temporaryFile.toFile(),
                        new StableWorkspaceRegistry(values)
                );
                try {
                    Files.move(
                            temporaryFile,
                            registry,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(
                            temporaryFile,
                            registry,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save stable workspace registry to " + registry,
                    e
            );
        }
    }

    private void createDirectory(UUID id) {
        try {
            Files.createDirectories(root().resolve(id.toString()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not create stable workspace", e);
        }
    }

    private Path registryFile() {
        return root().resolve(REGISTRY_FILE);
    }

    private Path root() {
        return paths.getPathFor(PathManager.Values.STABLE_WORKSPACES);
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " must not exceed " + maximumLength + " characters"
            );
        }
        return normalized;
    }

    public record StableWorkspaceDetails(
            UUID id,
            String name,
            @JsonAlias("purpose") String description,
            Instant createdAt,
            String createdBy
    ) {}

    private record StableWorkspaceRegistry(
            List<StableWorkspaceDetails> workspaces
    ) {}
}
