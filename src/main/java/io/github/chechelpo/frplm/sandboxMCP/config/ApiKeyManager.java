package io.github.chechelpo.frplm.sandboxMCP.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public final class ApiKeyManager {

    private static final String KEY_PREFIX = "asmcp_";
    private static final int MINIMUM_CUSTOM_KEY_LENGTH = 16;

    private final ObjectMapper mapper;
    private final Path configFile;
    private final SecureRandom random = new SecureRandom();
    private final List<StoredApiKey> keys;

    @Autowired
    public ApiKeyManager(
            ObjectMapper mapper,
            @Value("${sandbox-mcp.api-keys-file:}") String configuredFile
    ) {
        this.mapper = mapper;
        this.configFile = configuredFile == null || configuredFile.isBlank()
                ? Path.of(
                        System.getProperty("user.home"),
                        ".sandbox-mcp",
                        "api-keys.json"
                )
                : Path.of(configuredFile).toAbsolutePath().normalize();
        this.keys = load();
    }

    public synchronized List<ApiKeyDetails> list() {
        return keys.stream()
                .map(StoredApiKey::details)
                .sorted(Comparator.comparing(ApiKeyDetails::createdAt).reversed())
                .toList();
    }

    public synchronized CreatedApiKey create(String name, String requestedKey) {
        String normalizedName = requireName(name);
        String secret = requestedKey == null || requestedKey.isBlank()
                ? generateSecret()
                : requestedKey.trim();

        if (secret.length() < MINIMUM_CUSTOM_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "API keys must contain at least "
                            + MINIMUM_CUSTOM_KEY_LENGTH
                            + " characters"
            );
        }

        String hash = hash(secret);
        if (keys.stream().anyMatch(key -> constantTimeEquals(key.hash(), hash))) {
            throw new IllegalArgumentException("That API key is already configured");
        }

        StoredApiKey stored = new StoredApiKey(
                UUID.randomUUID(),
                normalizedName,
                visiblePrefix(secret),
                hash,
                Instant.now(),
                secret
        );
        keys.add(stored);
        save();
        return new CreatedApiKey(stored.details(), secret);
    }

    public synchronized boolean revoke(UUID id) {
        Objects.requireNonNull(id, "id");
        boolean removed = keys.removeIf(key -> key.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized Optional<ApiKeyPrincipal> authenticate(String secret) {
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }

        String candidateHash = hash(secret);
        return keys.stream()
                .filter(key -> constantTimeEquals(key.hash(), candidateHash))
                .findFirst()
                .map(key -> new ApiKeyPrincipal(key.id(), key.name()));
    }

    public Path configFile() {
        return configFile;
    }

    private List<StoredApiKey> load() {
        if (!Files.exists(configFile)) {
            saveKeys(List.of());
            return new ArrayList<>();
        }

        try {
            ApiKeyFile file = mapper.readValue(configFile.toFile(), ApiKeyFile.class);
            return new ArrayList<>(file.keys() == null ? List.of() : file.keys());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Could not load API keys from " + configFile,
                    e
            );
        }
    }

    private void save() {
        saveKeys(keys);
    }

    private void saveKeys(List<StoredApiKey> values) {
        try {
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temporaryFile = Files.createTempFile(
                    parent,
                    configFile.getFileName().toString(),
                    ".tmp"
            );
            try {
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(temporaryFile.toFile(), new ApiKeyFile(values));
                try {
                    Files.move(
                            temporaryFile,
                            configFile,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(
                            temporaryFile,
                            configFile,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save API keys to " + configFile,
                    e
            );
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("User name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("User name must not exceed 100 characters");
        }
        return normalized;
    }

    private static String visiblePrefix(String secret) {
        return secret.substring(0, Math.min(secret.length(), 12));
    }

    private static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record ApiKeyDetails(
            UUID id,
            String name,
            String prefix,
            Instant createdAt,
            String secret
    ) {}

    public record CreatedApiKey(ApiKeyDetails key, String secret) {}

    public record ApiKeyPrincipal(UUID id, String label)
            implements java.security.Principal {
        @Override
        public String getName() {
            return id.toString();
        }
    }

    private record StoredApiKey(
            UUID id,
            String name,
            String prefix,
            String hash,
            Instant createdAt,
            String secret
    ) {
        private ApiKeyDetails details() {
            return new ApiKeyDetails(id, name, prefix, createdAt, secret);
        }
    }

    private record ApiKeyFile(List<StoredApiKey> keys) {}
}
