package ttdrop.server;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The device registry: pairing codes, device session tokens, and each
 * device's host-granted permissions. Persisted at
 * {@code ~/.config/ttdrop/devices.properties}; pairing codes are
 * in-memory only (one-time, 10-minute expiry).
 *
 * <p>Session model: a paired device holds a random token in an
 * HttpOnly cookie; the server stores only the token's SHA-256. When
 * pairing is not required (open mode) every request resolves to the
 * {@link #OPEN} device, which sees the whole root — the pre-v0.16
 * behavior.
 *
 * <p>Isolation default: a newly paired device is scoped to its own
 * folder named after it, so devices cannot see each other's files
 * until the host widens their path ("" = the whole shared folder).
 */
public final class Devices {
    /** One device's identity and host-granted permissions. */
    public record Device(String id, String name, String relPath,
            boolean read, boolean write, boolean browse) {
        /** The subtree this device may touch, resolved and normalized. */
        public Path resolveRoot(Path fileRoot) {
            Path scoped = fileRoot.resolve(relPath).normalize();
            return scoped.startsWith(fileRoot) ? scoped : fileRoot;
        }
    }

    /** The virtual device used when pairing is off: full access. */
    public static final Device OPEN = new Device("open", "open", "", true, true, true);

    private static final String COOKIE = "ttdrop";
    private static final long CODE_TTL_MS = 10 * 60 * 1000;
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final Path file;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Device> byId = new LinkedHashMap<>();
    private final Map<String, String> idByTokenHash = new LinkedHashMap<>();
    private final Map<String, Long> pendingCodes = new ConcurrentHashMap<>();
    private volatile Runnable onChange = () -> { };

    public Devices(Path configDir) {
        this.file = configDir.resolve("devices.properties");
        load();
    }

    /** GUI refresh hook; called after pair/remove/update on any thread. */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange != null ? onChange : () -> { };
    }

    public synchronized List<Device> list() {
        return new ArrayList<>(byId.values());
    }

    /**
     * Resolves the device for a request. Open mode → {@link #OPEN};
     * otherwise the device whose token cookie matches, or null.
     */
    public Device authorize(HttpExchange ex, boolean pairingRequired) {
        if (!pairingRequired) {
            return OPEN;
        }
        return deviceForToken(cookieToken(ex));
    }

    public synchronized Device deviceForToken(String token) {
        if (token == null) {
            return null;
        }
        String id = idByTokenHash.get(sha256(token));
        return id == null ? null : byId.get(id);
    }

    /** A new one-time pairing code, XXXX-XXXX, valid ten minutes. */
    public String newPairingCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                code.append('-');
            }
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        pendingCodes.put(code.toString(), System.currentTimeMillis() + CODE_TTL_MS);
        return code.toString();
    }

    /**
     * Consumes a pairing code and creates the device (scoped to its
     * own folder under the file root). Returns the raw session token,
     * or null when the code is invalid or expired.
     */
    public synchronized String pair(String code, String requestedName, Path fileRoot) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        Long expiry = pendingCodes.remove(normalized);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            return null;
        }
        String name = uniqueName(UploadHandler.sanitize(requestedName));
        String id = randomHex(8);
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = hex(tokenBytes);
        Device device = new Device(id, name, name, true, true, true);
        byId.put(id, device);
        idByTokenHash.put(sha256(token), id);
        try {
            Files.createDirectories(device.resolveRoot(fileRoot));
        } catch (IOException ignored) {
            // created lazily by the first upload if this fails
        }
        save();
        onChange.run();
        return token;
    }

    public synchronized Device get(String id) {
        return byId.get(id);
    }

    public synchronized void update(Device device) {
        if (byId.containsKey(device.id())) {
            byId.put(device.id(), device);
            save();
            onChange.run();
        }
    }

    public synchronized void remove(String id) {
        if (byId.remove(id) != null) {
            idByTokenHash.values().removeIf(v -> v.equals(id));
            save();
            onChange.run();
        }
    }

    /* ---------- helpers ---------- */

    static String cookieToken(HttpExchange ex) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(COOKIE) && kv[1].matches("[a-f0-9]{64}")) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    /** The Set-Cookie value carrying a session token. */
    static String cookie(String token, boolean secure) {
        return COOKIE + "=" + token + "; Path=/; Max-Age=31536000; HttpOnly; SameSite=Lax"
                + (secure ? "; Secure" : "");
    }

    private String uniqueName(String requested) {
        String base = requested == null ? "Device" : requested;
        String name = base;
        int n = 2;
        while (nameTaken(name)) {
            name = base + " " + n++;
        }
        return name;
    }

    private boolean nameTaken(String name) {
        return byId.values().stream().anyMatch(d -> d.name().equalsIgnoreCase(name));
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return hex(b);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    static String sha256(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        for (String key : p.stringPropertyNames()) {
            if (!key.endsWith(".name")) {
                continue;
            }
            String id = key.substring(2, key.length() - ".name".length());
            String hash = p.getProperty("d." + id + ".hash");
            if (hash == null) {
                continue;
            }
            Device device = new Device(id,
                    p.getProperty("d." + id + ".name"),
                    p.getProperty("d." + id + ".path", ""),
                    Boolean.parseBoolean(p.getProperty("d." + id + ".read", "true")),
                    Boolean.parseBoolean(p.getProperty("d." + id + ".write", "true")),
                    Boolean.parseBoolean(p.getProperty("d." + id + ".browse", "true")));
            byId.put(id, device);
            idByTokenHash.put(hash, id);
        }
    }

    private void save() {
        Properties p = new Properties();
        for (Device d : byId.values()) {
            String hash = idByTokenHash.entrySet().stream()
                    .filter(e -> e.getValue().equals(d.id()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (hash == null) {
                continue;
            }
            p.setProperty("d." + d.id() + ".name", d.name());
            p.setProperty("d." + d.id() + ".hash", hash);
            p.setProperty("d." + d.id() + ".path", d.relPath());
            p.setProperty("d." + d.id() + ".read", String.valueOf(d.read()));
            p.setProperty("d." + d.id() + ".write", String.valueOf(d.write()));
            p.setProperty("d." + d.id() + ".browse", String.valueOf(d.browse()));
        }
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "ttDrop paired devices");
            }
        } catch (IOException ignored) {
            // devices survive in memory for this run
        }
    }
}
