package io.github.chechelpo.frplm.sandboxMCP.sessions;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record ClientIdentity(
        String subject,
        String clientId
) {
    public ClientIdentity {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(clientId, "clientId");
        if (subject.isBlank() || clientId.isBlank()) {
            throw new IllegalArgumentException("subject and clientId must not be blank");
        }
    }

    @Contract("null -> fail")
    public static @NonNull ClientIdentity from(Authentication auth) {
        if (auth instanceof UsernamePasswordAuthenticationToken apiKey
                && apiKey.isAuthenticated()
                && apiKey.getName() != null
                && !apiKey.getName().isBlank()) {
            return new ClientIdentity(
                    apiKey.getName(),
                    "api-key"
            );
        }

        throw new IllegalArgumentException(
                "Unsupported authentication type"
        );
    }


    public String workspaceId() {
        return UUID.nameUUIDFromBytes(
                (clientId + ":" + subject)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
