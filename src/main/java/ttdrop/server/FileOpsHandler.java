package ttdrop.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/**
 * File management under {@code /api/files/}: delete and rename inside
 * the file root, driven by the PWA's browser UI.
 *
 * <ul>
 *   <li>{@code POST /api/files/delete?path=} — deletes a file, or a
 *       directory recursively.</li>
 *   <li>{@code POST /api/files/rename?path=&to=} — renames within the
 *       same parent directory; {@code to} is a single sanitized name.
 *       409 when the new name already exists.</li>
 * </ul>
 *
 * <p>Both resolve strictly inside the file root (403 otherwise) and
 * refuse to touch the root itself or the {@code .ttdrop-part} staging
 * area.
 */
public final class FileOpsHandler implements HttpHandler {
    private final Path root;
    private final java.util.function.BooleanSupplier enabled;

    public FileOpsHandler(Path root, java.util.function.BooleanSupplier enabled) {
        this.root = root;
        this.enabled = enabled;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try (ex) {
            if (!enabled.getAsBoolean()) {
                UploadHandler.sendJson(ex, 403, "{\"error\":\"file management is disabled on this server\"}");
                return;
            }
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            String action = ex.getRequestURI().getPath().substring("/api/files/".length());
            Map<String, String> q = UploadHandler.query(ex);
            Path target = resolveSafe(q.get("path"));
            if (target == null) {
                UploadHandler.sendJson(ex, 400, "{\"error\":\"bad path\"}");
                return;
            }
            if (!Files.exists(target)) {
                UploadHandler.sendJson(ex, 404, "{\"error\":\"not found\"}");
                return;
            }
            switch (action) {
                case "delete" -> delete(ex, target);
                case "rename" -> rename(ex, target, q.get("to"));
                default -> ex.sendResponseHeaders(404, -1);
            }
        }
    }

    /** Resolves a client path inside the root, or null when unsafe. */
    private Path resolveSafe(String raw) {
        String clean = UploadHandler.sanitizePath(raw);
        if (clean == null) {
            return null;
        }
        Path target = root.resolve(clean).normalize();
        if (!target.startsWith(root) || target.equals(root)
                || target.startsWith(root.resolve(UploadHandler.PART_DIR))) {
            return null;
        }
        return target;
    }

    private void delete(HttpExchange ex, Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (var walk = Files.walk(target)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        } else {
            Files.delete(target);
        }
        ex.sendResponseHeaders(204, -1);
    }

    private void rename(HttpExchange ex, Path target, String to) throws IOException {
        String newName = UploadHandler.sanitize(to);
        if (newName == null) {
            UploadHandler.sendJson(ex, 400, "{\"error\":\"bad name\"}");
            return;
        }
        Path destination = target.getParent().resolve(newName).normalize();
        if (!destination.startsWith(root)) {
            UploadHandler.sendJson(ex, 400, "{\"error\":\"bad name\"}");
            return;
        }
        if (Files.exists(destination)) {
            UploadHandler.sendJson(ex, 409, "{\"error\":\"name already exists\"}");
            return;
        }
        Files.move(target, destination);
        UploadHandler.sendJson(ex, 200, "{\"name\":" + FilesHandler.quote(newName) + "}");
    }
}
