package ttdrop.server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * The ttDrop HTTP server: PWA host, file host, and receiver in one.
 *
 * <p>Serves the PWA from resources embedded in the jar (never from disk)
 * and the file area from the working directory the jar was started in.
 */
public final class TtDropServer {
    private final Path fileRoot;
    private HttpServer http;

    public TtDropServer(Path fileRoot) {
        this.fileRoot = fileRoot.toAbsolutePath().normalize();
    }

    public synchronized void start(int port) throws IOException {
        if (http != null) {
            throw new IllegalStateException("server already running");
        }
        http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/", new WebRootHandler());
        http.createContext("/files/", new FilesHandler(fileRoot));
        http.createContext("/api/upload/", new UploadHandler(fileRoot));
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
    }

    public synchronized void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    public synchronized boolean isRunning() {
        return http != null;
    }

    public synchronized int getPort() {
        return http == null ? -1 : http.getAddress().getPort();
    }

    public Path getFileRoot() {
        return fileRoot;
    }

    /** Addresses other devices on the LAN can use to reach this server. */
    public static List<String> lanAddresses() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr.isSiteLocalAddress()) {
                        out.add(addr.getHostAddress());
                    }
                }
            }
        } catch (IOException ignored) {
            // best effort: no LAN address list, localhost still works
        }
        return out;
    }
}
