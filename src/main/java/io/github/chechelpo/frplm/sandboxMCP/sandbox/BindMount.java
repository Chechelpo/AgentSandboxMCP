package io.github.chechelpo.frplm.sandboxMCP.sandbox;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record BindMount(
        Path host,
        Path guest,
        Access access
) {
    public enum Access {
        READ,
        WRITE
    }

    @Contract("_, _ -> new")
    public static @NonNull BindMount ofSimple(Path bind, Access access){
        return new BindMount(bind, bind, access);
    }

    @Contract("_, _, _ -> new")
    public static @NonNull BindMount of(Path host, Path guest, Access access) {
        return new BindMount(host, guest, access);
    }


    @Contract(" -> new")
    public static @NonNull @Unmodifiable List<BindMount> macOsDefaults() {
        return List.of(
                ofSimple(Path.of("/bin"), Access.READ),
                ofSimple(Path.of("/sbin"), Access.READ),
                ofSimple(Path.of("/usr"), Access.READ),
                ofSimple(Path.of("/System"), Access.READ),
                ofSimple(Path.of("/Library"), Access.READ),
                ofSimple(Path.of("/private/etc"), Access.READ)
        );
    }

    @Contract(" -> new")
    public static @NonNull @Unmodifiable List<BindMount> windowsDefaults() {
        return List.of(
                ofSimple(Path.of("C:\\Windows"), Access.READ),
                ofSimple(Path.of("C:\\Program Files"), Access.READ),
                ofSimple(Path.of("C:\\Program Files (x86)"), Access.READ)
        );
    }


    public BindMount {
        Objects.requireNonNull(host);
        Objects.requireNonNull(guest);
        Objects.requireNonNull(access);
        host = host.toAbsolutePath().normalize();
        guest = guest.normalize();
        if (!guest.isAbsolute()) {
            throw new IllegalArgumentException("Guest mount path must be absolute");
        }
    }
}
