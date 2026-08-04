package ttdrop.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Self-signed TLS for serving over HTTPS on the LAN.
 *
 * <p>A PKCS12 keystore is generated once with the JDK's {@code keytool}
 * (found via {@code java.home}, present in JDK and JRE distributions)
 * and stored under {@code ~/.config/ttdrop/}. The certificate carries
 * SANs for {@code localhost} and the machine's current LAN IPs so
 * browsers match the URL the QR codes hand out. Devices still have to
 * accept the self-signed certificate once — that is inherent to local
 * HTTPS without a CA.
 *
 * <p>The fixed keystore password protects nothing meaningful here (the
 * keystore sits in the user's own config dir and guards a self-signed
 * key for LAN transfers); it exists because PKCS12 requires one.
 */
public final class TlsSupport {
    private static final String STORE_PASS = "ttdrop";
    private static final String ALIAS = "ttdrop";

    private TlsSupport() {
    }

    /** Loads (or first generates) the keystore and builds an SSLContext. */
    public static SSLContext sslContext(Path configDir) throws IOException {
        Path keystore = configDir.resolve("keystore.p12");
        if (!Files.exists(keystore)) {
            generate(keystore);
        }
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystore)) {
                store.load(in, STORE_PASS.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(store, STORE_PASS.toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), null, null);
            return context;
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("could not load TLS keystore: " + e.getMessage(), e);
        }
    }

    private static void generate(Path keystore) throws IOException {
        Files.createDirectories(keystore.getParent());
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        if (!Files.exists(keytool)) {
            throw new IOException("keytool not found at " + keytool + "; cannot generate TLS certificate");
        }
        StringBuilder san = new StringBuilder("SAN=dns:localhost,ip:127.0.0.1");
        for (String ip : TtDropServer.lanAddresses()) {
            san.append(",ip:").append(ip);
        }
        List<String> cmd = new ArrayList<>(List.of(
                keytool.toString(), "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650",
                "-dname", "CN=ttDrop",
                "-ext", san.toString(),
                "-storetype", "PKCS12",
                "-keystore", keystore.toString(),
                "-storepass", STORE_PASS));
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) {
                throw new IOException("keytool failed: " + output.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while generating TLS certificate", e);
        }
    }
}
