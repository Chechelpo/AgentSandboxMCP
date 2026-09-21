package io.github.chechelpo.frplm.sandboxMCP.sessions;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIdentityTests {

    @Test
    void createsIdentityForApiKeyAuthentication() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "lumiverse",
                        "password",
                        List.of()
                );

        ClientIdentity identity = ClientIdentity.from(authentication);

        assertEquals("lumiverse", identity.subject());
        assertEquals("api-key", identity.clientId());
    }
}
