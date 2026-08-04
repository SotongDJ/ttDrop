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
 * TLS for serving over HTTPS on the LAN, built around a reusable
 * per-user Certificate Authority.
 *
 * <p>On the first HTTPS run a CA keypair is generated into
 * {@code ~/.config/ttdrop/ca.p12} with its certificate exported as
 * {@code ca.crt}. The user installs that one certificate on their
 * devices (served at {@code /ca.crt}); from then on every ttDrop
 * server certificate — present and future, regenerated or not — is
 * trusted, which also unlocks service workers and PWA install.
 *
 * <p>The server certificate ({@code keystore.p12}) is issued by the CA
 * with SANs for {@code localhost}, {@code 127.0.0.1}, and the LAN IPs
 * present at generation time, and a validity Apple accepts (≤825
 * days). Delete {@code keystore.p12} to re-issue (e.g. after an IP
 * change) — the CA, and therefore device trust, persists.
 *
 * <p>All generation happens through the JDK's {@code keytool}
 * (resolved via {@code java.home}). The fixed keystore password guards
 * self-signed LAN material in the user's own config dir; PKCS12 simply
 * requires one.
 */
public final class TlsSupport {
    private static final String STORE_PASS = "ttdrop";
    private static final String CA_ALIAS = "ttdrop-ca";
    private static final String SERVER_ALIAS = "ttdrop";

    private TlsSupport() {
    }

    /** The exported CA certificate (PEM), for the /ca.crt endpoint. */
    public static Path caCertificate(Path configDir) {
        return configDir.resolve("ca.crt");
    }

    /** Loads (generating CA and server certificate as needed) an SSLContext. */
    public static SSLContext sslContext(Path configDir) throws IOException {
        Path caStore = configDir.resolve("ca.p12");
        Path caCert = caCertificate(configDir);
        Path keystore = configDir.resolve("keystore.p12");
        if (!Files.exists(caStore) || !Files.exists(caCert)) {
            // No CA (first https run, or pre-CA layout): start fresh so
            // the server certificate is always CA-issued.
            Files.deleteIfExists(keystore);
            generateCa(caStore, caCert);
        }
        if (!Files.exists(keystore)) {
            generateServerCert(configDir, caStore, caCert, keystore);
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

    private static void generateCa(Path caStore, Path caCert) throws IOException {
        Files.createDirectories(caStore.getParent());
        String user = System.getProperty("user.name", "user");
        keytool("-genkeypair",
                "-alias", CA_ALIAS,
                "-keyalg", "RSA", "-keysize", "3072",
                "-validity", "3650",
                "-dname", "CN=ttDrop CA (" + user + ")",
                "-ext", "bc:c=ca:true",
                "-ext", "ku:c=keyCertSign,cRLSign",
                "-storetype", "PKCS12",
                "-keystore", caStore.toString(),
                "-storepass", STORE_PASS);
        keytool("-exportcert", "-rfc",
                "-alias", CA_ALIAS,
                "-keystore", caStore.toString(),
                "-storepass", STORE_PASS,
                "-file", caCert.toString());
    }

    private static void generateServerCert(Path configDir, Path caStore, Path caCert, Path keystore)
            throws IOException {
        StringBuilder san = new StringBuilder("SAN=dns:localhost,ip:127.0.0.1");
        for (String ip : TtDropServer.lanAddresses()) {
            san.append(",ip:").append(ip);
        }
        Path csr = configDir.resolve("server.csr");
        Path signed = configDir.resolve("server.crt");
        try {
            keytool("-genkeypair",
                    "-alias", SERVER_ALIAS,
                    "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "820",
                    "-dname", "CN=ttDrop",
                    "-storetype", "PKCS12",
                    "-keystore", keystore.toString(),
                    "-storepass", STORE_PASS);
            keytool("-certreq",
                    "-alias", SERVER_ALIAS,
                    "-keystore", keystore.toString(),
                    "-storepass", STORE_PASS,
                    "-file", csr.toString());
            keytool("-gencert",
                    "-alias", CA_ALIAS,
                    "-keystore", caStore.toString(),
                    "-storepass", STORE_PASS,
                    "-infile", csr.toString(),
                    "-outfile", signed.toString(),
                    "-validity", "820",
                    "-ext", san.toString(),
                    "-ext", "ku:c=digitalSignature,keyEncipherment",
                    "-ext", "eku=serverAuth");
            keytool("-importcert", "-noprompt",
                    "-alias", CA_ALIAS,
                    "-keystore", keystore.toString(),
                    "-storepass", STORE_PASS,
                    "-file", caCert.toString());
            keytool("-importcert", "-noprompt",
                    "-alias", SERVER_ALIAS,
                    "-keystore", keystore.toString(),
                    "-storepass", STORE_PASS,
                    "-file", signed.toString());
        } finally {
            Files.deleteIfExists(csr);
            Files.deleteIfExists(signed);
        }
    }

    private static void keytool(String... args) throws IOException {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        if (!Files.exists(keytool)) {
            throw new IOException("keytool not found at " + keytool + "; cannot generate TLS certificates");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(keytool.toString());
        cmd.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) {
                throw new IOException("keytool " + args[0] + " failed: " + output.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while generating TLS certificates", e);
        }
    }
}
