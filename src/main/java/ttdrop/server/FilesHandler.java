package ttdrop.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Serves the file area: the working directory the jar was started in.
 *
 * <p>{@code GET /files/<path>} downloads a file; a directory path returns
 * a JSON listing. Every resolved path must stay inside the file root —
 * traversal outside it is rejected.
 */
public final class FilesHandler implements HttpHandler {
    private final Path root;

    public FilesHandler(Path root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try (ex) {
            if (!"GET".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            String raw = ex.getRequestURI().getPath().substring("/files/".length());
            String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            Path target = root.resolve(decoded).normalize();
            if (!target.startsWith(root)) {
                ex.sendResponseHeaders(403, -1);
                return;
            }
            if (Files.isDirectory(target)) {
                sendListing(ex, target);
            } else if (Files.isRegularFile(target)) {
                sendFile(ex, target);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
        }
    }

    private void sendListing(HttpExchange ex, Path dir) throws IOException {
        StringBuilder json = new StringBuilder("{\"entries\":[");
        try (Stream<Path> entries = Files.list(dir)) {
            boolean first = true;
            for (Path p : (Iterable<Path>) entries.sorted()::iterator) {
                if (p.getFileName().toString().equals(UploadHandler.PART_DIR)) {
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                first = false;
                boolean isDir = Files.isDirectory(p);
                json.append("{\"name\":").append(quote(p.getFileName().toString()))
                        .append(",\"dir\":").append(isDir)
                        .append(",\"size\":").append(isDir ? 0 : Files.size(p))
                        .append(",\"mtime\":").append(Files.getLastModifiedTime(p).toMillis())
                        .append('}');
            }
        }
        json.append("]}");
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private void sendFile(HttpExchange ex, Path file) throws IOException {
        long size = Files.size(file);
        String name = file.getFileName().toString();
        ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        ex.sendResponseHeaders(200, size == 0 ? -1 : size);
        try (InputStream in = Files.newInputStream(file); OutputStream out = ex.getResponseBody()) {
            in.transferTo(out);
        }
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
