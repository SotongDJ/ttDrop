package ttdrop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persistent settings, stored at {@code ~/.config/ttdrop/config.properties}
 * on every platform (resolved via {@code user.home}). The working directory
 * is exclusively the file area — configuration never lives there.
 */
public final class Config {
    private static final Path DIR = Path.of(System.getProperty("user.home"), ".config", "ttdrop");
    private static final Path FILE = DIR.resolve("config.properties");

    /** The per-user config directory (also holds the TLS keystore). */
    public static Path dir() {
        return DIR;
    }

    private final Properties props = new Properties();

    private Config() {
    }

    public static Config load() {
        Config config = new Config();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                config.props.load(in);
            } catch (IOException ignored) {
                // unreadable config falls back to defaults
            }
        }
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "ttDrop settings");
            }
        } catch (IOException ignored) {
            // settings are a convenience; failing to persist them is not fatal
        }
    }

    public int getPort(int fallback) {
        try {
            return Integer.parseInt(props.getProperty("port", ""));
        } catch (NumberFormatException nfe) {
            return fallback;
        }
    }

    public void setPort(int port) {
        props.setProperty("port", String.valueOf(port));
    }

    public boolean getHttps(boolean fallback) {
        String value = props.getProperty("https");
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public void setHttps(boolean https) {
        props.setProperty("https", String.valueOf(https));
    }
}
