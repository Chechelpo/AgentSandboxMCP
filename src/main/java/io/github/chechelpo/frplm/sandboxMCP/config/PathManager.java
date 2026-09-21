package io.github.chechelpo.frplm.sandboxMCP.config;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Component
public final class PathManager {

    public enum Values {
        AGENT_WORKSPACES,
        STABLE_WORKSPACES,
        WORKSPACE_CONFIG_FILE
    }

    private final ObjectMapper mapper;
    private final Path configFile;
    private final EnumMap<Values, Path> configuration;


    public PathManager(ObjectMapper mapper) {
        this(mapper, "");
    }

    @Autowired
    public PathManager(
            ObjectMapper mapper,
            @Value("${sandbox-mcp.config-file:}") String configuredFile
    ) {
        this.mapper = mapper;
        this.configFile = configuredFile == null || configuredFile.isBlank()
                ? Path.of(
                        System.getProperty("user.home"),
                        ".sandbox-mcp",
                        "paths.json"
                )
                : Path.of(configuredFile).toAbsolutePath().normalize();
        this.configuration = load();
    }


    public @NonNull Path getPathFor(Values value) {
        Objects.requireNonNull(value, "value");

        Path path = configuration.get(value);

        if (path == null) {
            throw new IllegalStateException(
                    "Missing configuration for " + value
            );
        }

        return path;
    }


    public synchronized void setPathFor(
            Values value,
            Path path
    ) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(path, "path");

        EnumMap<Values, Path> updated = new EnumMap<>(configuration);
        updated.put(value, path.toAbsolutePath().normalize());
        save(updated);
        configuration.clear();
        configuration.putAll(updated);
    }


    private @NonNull EnumMap<Values, Path> load() {
        if (!Files.exists(configFile)) {
            EnumMap<Values, Path> defaults = createDefaults();
            save(defaults);
            return defaults;
        }

        Map<Values, Path> loaded =
                mapper.readValue(
                        configFile.toFile(),
                        mapper.getTypeFactory()
                                .constructMapType(
                                        EnumMap.class,
                                        Values.class,
                                        Path.class
                                )
                );

        EnumMap<Values, Path> result = createDefaults();
        result.putAll(loaded);
        return result;

    }


    private @NonNull EnumMap<Values, Path> createDefaults() {
        Path root = Path.of(
                System.getProperty("user.home"),
                ".sandbox-mcp"
        );

        EnumMap<Values, Path> defaults =
                new EnumMap<>(Values.class);

        defaults.put(
                Values.AGENT_WORKSPACES,
                root.resolve("workspaces")
        );

        defaults.put(
                Values.WORKSPACE_CONFIG_FILE,
                root.resolve("workspace.json")
        );

        defaults.put(
                Values.STABLE_WORKSPACES,
                root.resolve("stable-workspaces")
        );

        return defaults;
    }


    private void save(
            EnumMap<Values, Path> values
    ) {
        try {
            Files.createDirectories(
                    configFile.getParent()
            );

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(
                            configFile.toFile(),
                            values
                    );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save path configuration",
                    e
            );
        }
    }
}
