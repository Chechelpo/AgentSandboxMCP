package io.github.chechelpo.frplm.sandboxMCP.config;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/config/keys")
public class ApiKeyConfigurationController {

    private final ApiKeyManager apiKeys;

    public ApiKeyConfigurationController(ApiKeyManager apiKeys) {
        this.apiKeys = apiKeys;
    }

    @GetMapping
    public List<ApiKeyManager.ApiKeyDetails> list() {
        return apiKeys.list();
    }

    @PostMapping
    public ApiKeyManager.CreatedApiKey create(@RequestBody CreateApiKey request) {
        Objects.requireNonNull(request, "request");
        try {
            return apiKeys.create(request.name(), request.key());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        return apiKeys.revoke(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    public record CreateApiKey(String name, String key) {}
}
