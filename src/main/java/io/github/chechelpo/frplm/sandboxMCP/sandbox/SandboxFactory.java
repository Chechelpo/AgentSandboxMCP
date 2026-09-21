package io.github.chechelpo.frplm.sandboxMCP.sandbox;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class SandboxFactory {

    enum OperatingSystem {
        LINUX,
        MACOS,
        WINDOWS
    }

    private OperatingSystem system;

    @PostConstruct
    void initialize() {
        this.system = detectOperatingSystem();
    }

    public Sandbox create(List<BindMount> binds) {
        Objects.requireNonNull(binds, "binds");

        List<BindMount> safeBinds = List.copyOf(binds);

        OperatingSystem currentSystem = system == null ? detectOperatingSystem() : system;

        return switch (currentSystem) {
            case LINUX -> new LinuxSandbox(safeBinds);
            case MACOS, WINDOWS ->
                    throw new UnsupportedOperationException(
                            "Sandbox not implemented for " + currentSystem
                    );
        };
    }

    private static OperatingSystem detectOperatingSystem() {
        String osName = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        if (osName.contains("linux")) {
            if (!hasBubblewrap()) {
                throw new IllegalStateException(
                        "Linux sandbox requires bubblewrap (bwrap) installed"
                );
            }

            return OperatingSystem.LINUX;
        }

        if (osName.contains("mac") || osName.contains("darwin")) {
            return OperatingSystem.MACOS;
        }

        if (osName.contains("win")) {
            return OperatingSystem.WINDOWS;
        }

        throw new IllegalStateException(
                "Unsupported operating system: " + osName
        );
    }

    private static boolean hasBubblewrap() {
        try {
            Process process = new ProcessBuilder(
                    "bwrap",
                    "--version"
            )
                    .redirectErrorStream(true)
                    .start();

            return process.waitFor() == 0;

        } catch (IOException e) {
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
