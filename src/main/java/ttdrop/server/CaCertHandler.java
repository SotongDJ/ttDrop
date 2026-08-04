package ttdrop.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code GET /ca.crt} — the per-user ttDrop CA certificate (DER).
 * Installing it once on a device makes every ttDrop HTTPS session
 * trusted, unlocking service workers and PWA install. Serving the CA
 * certificate is safe: it contains only the public half.
 */
public final class CaCertHandler implements HttpHandler {
    private final Path caCert;

    public CaCertHandler(Path caCert) {
        this.caCert = caCert;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try (ex) {
            if (!"GET".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            if (!Files.exists(caCert)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = Files.readAllBytes(caCert);
            ex.getResponseHeaders().set("Content-Type", "application/x-x509-ca-cert");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=ttdrop-ca.crt");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
