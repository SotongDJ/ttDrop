package ttdrop.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
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
    /**
     * Extensions safe to display inline, mapped to their MIME types.
     * A whitelist on purpose: rendering arbitrary uploaded content
     * (HTML above all) inline on this origin would be stored XSS
     * against the PWA. Everything not listed here downloads as an
     * attachment, and inline responses additionally carry
     * {@code Content-Security-Policy: sandbox} so even scriptable
     * formats (SVG) cannot run code. Text-like formats are served as
     * text/plain deliberately.
     */
    private static final java.util.Map<String, String> VIEWABLE = java.util.Map.ofEntries(
            java.util.Map.entry("png", "image/png"),
            java.util.Map.entry("jpg", "image/jpeg"),
            java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("webp", "image/webp"),
            java.util.Map.entry("avif", "image/avif"),
            java.util.Map.entry("bmp", "image/bmp"),
            java.util.Map.entry("ico", "image/x-icon"),
            java.util.Map.entry("svg", "image/svg+xml"),
            java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("txt", "text/plain; charset=utf-8"),
            java.util.Map.entry("md", "text/plain; charset=utf-8"),
            java.util.Map.entry("log", "text/plain; charset=utf-8"),
            java.util.Map.entry("csv", "text/plain; charset=utf-8"),
            java.util.Map.entry("json", "text/plain; charset=utf-8"),
            java.util.Map.entry("xml", "text/plain; charset=utf-8"));

    private final Path root;
    private final java.util.function.BooleanSupplier fileOps;

    public FilesHandler(Path root, java.util.function.BooleanSupplier fileOps) {
        this.root = root;
        this.fileOps = fileOps;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try (ex) {
            boolean head = "HEAD".equals(ex.getRequestMethod());
            if (!"GET".equals(ex.getRequestMethod()) && !head) {
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
                sendFile(ex, target, head);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
        }
    }

    private void sendListing(HttpExchange ex, Path dir) throws IOException {
        // fileOps tells the PWA whether to render rename/delete buttons.
        StringBuilder json = new StringBuilder("{\"fileOps\":" + fileOps.getAsBoolean() + ",\"entries\":[");
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

    /**
     * Sends a file, honoring single-range {@code Range: bytes=a-b} requests
     * with 206 responses so the PWA can download chunks in parallel and
     * resume. The ETag ({@code "size-mtime"}) lets a resuming client detect
     * that the file changed since its partial download was staged.
     */
    private void sendFile(HttpExchange ex, Path file, boolean head) throws IOException {
        long size = Files.size(file);
        String name = file.getFileName().toString();
        String etag = "\"" + size + "-" + Files.getLastModifiedTime(file).toMillis() + "\"";
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        ex.getResponseHeaders().set("ETag", etag);
        int dot = name.lastIndexOf('.');
        String viewableType = dot < 0 ? null : VIEWABLE.get(name.substring(dot + 1).toLowerCase());
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        if (viewableType != null) {
            ex.getResponseHeaders().set("Content-Type", viewableType);
            ex.getResponseHeaders().set("Content-Disposition", "inline; filename*=UTF-8''" + encoded);
            ex.getResponseHeaders().set("Content-Security-Policy", "sandbox");
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        } else {
            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        }

        long from = 0;
        long to = size - 1;
        boolean partial = false;
        String range = ex.getRequestHeaders().getFirst("Range");
        if (range != null && range.startsWith("bytes=") && !range.contains(",")) {
            String spec = range.substring("bytes=".length()).trim();
            int dash = spec.indexOf('-');
            try {
                if (dash > 0) {
                    from = Long.parseLong(spec.substring(0, dash));
                    to = dash < spec.length() - 1 ? Long.parseLong(spec.substring(dash + 1)) : size - 1;
                } else if (dash == 0) {
                    long suffix = Long.parseLong(spec.substring(1));
                    from = Math.max(0, size - suffix);
                }
                partial = true;
            } catch (NumberFormatException nfe) {
                partial = false;
            }
            if (partial && (from > to || from >= size)) {
                ex.getResponseHeaders().set("Content-Range", "bytes */" + size);
                ex.sendResponseHeaders(416, -1);
                return;
            }
            to = Math.min(to, size - 1);
        }

        long length = to - from + 1;
        if (partial) {
            ex.getResponseHeaders().set("Content-Range", "bytes " + from + "-" + to + "/" + size);
        }
        int code = partial ? 206 : 200;
        if (head) {
            ex.getResponseHeaders().set("Content-Length", String.valueOf(length));
            ex.sendResponseHeaders(code, -1);
            return;
        }
        ex.sendResponseHeaders(code, length == 0 ? -1 : length);
        try (var channel = Files.newByteChannel(file); OutputStream out = ex.getResponseBody()) {
            channel.position(from);
            byte[] buf = new byte[64 * 1024];
            long remaining = length;
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf);
            while (remaining > 0) {
                bb.clear().limit((int) Math.min(buf.length, remaining));
                int read = channel.read(bb);
                if (read < 0) {
                    break;
                }
                out.write(buf, 0, read);
                remaining -= read;
            }
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
