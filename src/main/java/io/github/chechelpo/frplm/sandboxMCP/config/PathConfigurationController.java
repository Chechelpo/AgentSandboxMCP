package io.github.chechelpo.frplm.sandboxMCP.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/config/paths")
public class PathConfigurationController {

    private final PathManager paths;

    public PathConfigurationController(PathManager paths) {
        this.paths = paths;
    }

    @GetMapping
    public Map<PathManager.Values, String> getPaths() {
        EnumMap<PathManager.Values, String> result =
                new EnumMap<>(PathManager.Values.class);
        for (PathManager.Values value : PathManager.Values.values()) {
            result.put(value, paths.getPathFor(value).toString());
        }
        return result;
    }

    @GetMapping("/csrf")
    public CsrfDetails csrf(CsrfToken token) {
        return new CsrfDetails(
                token.getHeaderName(),
                token.getParameterName(),
                token.getToken()
        );
    }

    @PutMapping("/{name}")
    public PathValue setPath(
            @PathVariable PathManager.Values name,
            @RequestBody PathValue value
    ) {
        Objects.requireNonNull(value, "value");
        Path path = Path.of(value.path()).toAbsolutePath().normalize();
        paths.setPathFor(name, path);
        return new PathValue(path.toString());
    }

    public record PathValue(String path) {
        public PathValue {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path must not be blank");
            }
        }
    }

    public record CsrfDetails(
            String headerName,
            String parameterName,
            String token
    ) {}
}
