package jacross;

import java.awt.Color;
import java.awt.Toolkit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * One-shot OS probe: dark mode and accent colour, each optional with a
 * quiet fallback. Probing means attempting — a failed subprocess or
 * missing setting simply yields empty. Never called from painters; the
 * result feeds theme construction once at startup.
 */
public final class Platform {
    public enum OS { WINDOWS, MACOS, LINUX, OTHER }

    private final OS os;
    private final Optional<Boolean> osDark;
    private final Optional<Color> accent;

    private Platform(OS os, Optional<Boolean> osDark, Optional<Color> accent) {
        this.os = os;
        this.osDark = osDark;
        this.accent = accent;
    }

    public OS os() {
        return os;
    }

    public Optional<Boolean> osDark() {
        return osDark;
    }

    public Optional<Color> accentColor() {
        return accent;
    }

    public static Platform detect() {
        OS os = currentOs();
        return new Platform(os, probeDark(os), probeAccent(os));
    }

    private static OS currentOs() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win")) {
            return OS.WINDOWS;
        }
        if (name.contains("mac")) {
            return OS.MACOS;
        }
        if (name.contains("linux")) {
            return OS.LINUX;
        }
        return OS.OTHER;
    }

    private static Optional<Boolean> probeDark(OS os) {
        try {
            switch (os) {
                case WINDOWS -> {
                    Optional<String> out = exec("reg", "query",
                            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                            "/v", "AppsUseLightTheme");
                    if (out.isPresent()) {
                        return Optional.of(out.get().contains("0x0"));
                    }
                }
                case MACOS -> {
                    // The key exists only when dark mode is on.
                    return Optional.of(exec("defaults", "read", "-g", "AppleInterfaceStyle")
                            .map(s -> s.contains("Dark")).orElse(false));
                }
                case LINUX -> {
                    Optional<String> out = exec("gsettings", "get",
                            "org.gnome.desktop.interface", "color-scheme");
                    if (out.isPresent()) {
                        return Optional.of(out.get().contains("dark"));
                    }
                }
                default -> {
                }
            }
        } catch (Exception ignored) {
            // fall through to empty
        }
        return Optional.empty();
    }

    private static Optional<Color> probeAccent(OS os) {
        try {
            if (os == OS.WINDOWS) {
                Object value = Toolkit.getDefaultToolkit()
                        .getDesktopProperty("win.item.highlightColor");
                if (value instanceof Color c) {
                    return Optional.of(c);
                }
            } else if (os == OS.MACOS) {
                // "0.698039 0.843137 1.000000 Blue" — RGB floats.
                return exec("defaults", "read", "-g", "AppleHighlightColor").map(s -> {
                    String[] p = s.trim().split("\\s+");
                    return new Color(Float.parseFloat(p[0]), Float.parseFloat(p[1]),
                            Float.parseFloat(p[2]));
                });
            }
        } catch (Exception ignored) {
            // fall through to empty
        }
        return Optional.empty();
    }

    private static Optional<String> exec(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            return Optional.of(new String(process.getInputStream().readAllBytes()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
