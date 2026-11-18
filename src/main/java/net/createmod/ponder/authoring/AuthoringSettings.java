package net.createmod.ponder.authoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.LongSupplier;

/** The local client preference is stored separately from the author's data. */
public final class AuthoringSettings {
    static final String FILE_NAME = "ponder_legacy-authoring.properties";
    private static volatile boolean enabled;
    private static volatile Boolean savedEnabled;
    private static Path file;

    private AuthoringSettings() { }

    public static synchronized void load(Path configDirectory) throws IOException {
        file = configDirectory.toAbsolutePath().resolve(FILE_NAME);
        enabled = false;
        savedEnabled = null;
        savedEnabled = enabled = readPreference(file);
    }

    public static boolean isEnabled() { return enabled; }

    public static boolean hasPendingChange() {
        Boolean saved = savedEnabled;
        return saved != null && enabled != saved;
    }

    /** Write the preference before reloading. A failed reload leaves the previous runtime state active. */
    public static synchronized boolean change(boolean requested, LongSupplier generation, Runnable reload) throws IOException {
        if (file == null) throw new IllegalStateException("Local authoring settings have not been loaded");
        if (savedEnabled == null || savedEnabled != requested) {
            writePreference(file, requested);
            savedEnabled = requested;
        }
        if (enabled == requested) return true;

        long previousGeneration = generation.getAsLong();
        boolean previous = enabled;
        enabled = requested;
        try {
            reload.run();
        } finally {
            if (generation.getAsLong() == previousGeneration) enabled = previous;
        }
        return enabled == requested;
    }

    static boolean readPreference(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (NoSuchFileException missing) {
            return false;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid local authoring preference", invalid);
        }
        String value = properties.getProperty("enabled", "false");
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IOException("Invalid local authoring preference: expected enabled=true or enabled=false");
    }

    static void writePreference(Path path, boolean requested) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(requested));
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "ponder-authoring-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Ponder local visual authoring");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
