package io.github.chechelpo.frplm.sandboxMCP.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public final class WebUiAccountManager implements UserDetailsService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebUiAccountManager.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 4;
    private static final int MAXIMUM_BCRYPT_BYTES = 72;

    private final StoredAccount account;

    public WebUiAccountManager(
            PasswordEncoder passwordEncoder,
            @Value("${sandbox-mcp.web.username:admin}") String configuredUsername,
            @Value("${sandbox-mcp.web.password}") String configuredPassword
    ) {
        Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        String username = requireUsername(configuredUsername);
        validatePassword(configuredPassword);
        this.account = new StoredAccount(
                username,
                passwordEncoder.encode(configuredPassword)
        );
        LOGGER.info("Web UI login configured for user '{}'", username);
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        if (!account.username().equals(username)) {
            throw new UsernameNotFoundException("Unknown Web UI user");
        }
        return User.withUsername(account.username())
                .password(account.passwordHash())
                .roles("ADMIN")
                .build();
    }

    public AccountDetails details() {
        return new AccountDetails(account.username());
    }

    private static String requireUsername(String username) {
        Objects.requireNonNull(username, "username");
        String normalized = username.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Web UI username must not be blank");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Web UI password must contain at least "
                            + MINIMUM_PASSWORD_LENGTH
                            + " characters"
            );
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BCRYPT_BYTES) {
            throw new IllegalArgumentException(
                    "Web UI password must not exceed 72 UTF-8 bytes"
            );
        }
    }

    public record AccountDetails(String username) {}

    private record StoredAccount(String username, String passwordHash) {
        private StoredAccount {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(passwordHash, "passwordHash");
        }
    }
}
