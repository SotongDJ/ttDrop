package ttdrop;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;

import ttdrop.gui.ServerWindow;
import ttdrop.server.TtDropServer;

/**
 * Entry point for the ttDrop server application.
 *
 * <p>The working directory the jar is started from becomes the file root
 * served under {@code /files/}. With a display available a Swing control
 * window opens; with {@code --headless} (or no display) the server starts
 * immediately and blocks until interrupted.
 *
 * <p>Arguments: {@code --port <n>} (default 4646), {@code --headless}.
 */
public final class Main {
    public static final int DEFAULT_PORT = 4646;

    private Main() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Config config = Config.load();
        int port = config.getPort(DEFAULT_PORT);
        boolean https = config.getHttps(true);
        boolean headless = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--headless" -> headless = true;
                case "--https" -> https = true;
                case "--http" -> https = false;
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.err.println("Usage: java -jar ttdrop.jar [--port <n>] [--headless] [--https|--http]");
                    System.exit(2);
                }
            }
        }

        Path fileRoot = Path.of(System.getProperty("user.dir"));
        TtDropServer server = new TtDropServer(fileRoot);

        if (headless || GraphicsEnvironment.isHeadless()) {
            try {
                server.start(port, https);
            } catch (java.net.BindException be) {
                int freePort = TtDropServer.findFreePort();
                System.err.println("Port " + port + " is already in use; using free port " + freePort);
                server.start(freePort, https);
            }
            System.out.println("ttDrop serving " + fileRoot
                    + " at " + server.scheme() + "://localhost:" + server.getPort() + "/"
                    + " on port " + server.getPort());
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            Thread.currentThread().join();
        } else {
            final int guiPort = port;
            final boolean guiHttps = https;
            javax.swing.SwingUtilities.invokeLater(() ->
                    new ServerWindow(server, guiPort, guiHttps, config).setVisible(true));
        }
    }
}
