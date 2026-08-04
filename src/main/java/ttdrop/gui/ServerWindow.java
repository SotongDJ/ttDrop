package ttdrop.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Color;
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ttdrop.Config;
import ttdrop.server.TtDropServer;
import ttdrop.util.QrCode;

/**
 * Control window: file root, port choice, start/stop, an address picker
 * listing the machine's local IPs, and a QR code of the selected URL so
 * camera-equipped devices open the site by scanning.
 */
public final class ServerWindow extends JFrame {
    private TtDropServer server;
    private final Config config;
    private final JTextField portField;
    private final javax.swing.JCheckBox httpsBox = new javax.swing.JCheckBox("HTTPS");
    private final javax.swing.JCheckBox autostartBox = new javax.swing.JCheckBox("Start on launch");
    private final javax.swing.JCheckBox fileOpsBox = new javax.swing.JCheckBox("Allow browser file management");
    private final javax.swing.JCheckBox dirBrowseBox = new javax.swing.JCheckBox("Allow directory browsing");
    private final JButton toggleButton = new JButton("Start");
    private final JButton rootButton = new JButton("Change…");
    private final JLabel rootLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JComboBox<String> addressBox = new JComboBox<>();
    private final JLabel urlLabel = new JLabel(" ");
    private final QrPanel qrPanel = new QrPanel();

    public ServerWindow(TtDropServer server, int initialPort, boolean initialHttps, Config config) {
        super("ttDrop");
        this.server = server;
        this.config = config;
        this.portField = new JTextField(String.valueOf(initialPort), 6);
        this.httpsBox.setSelected(initialHttps);
        this.httpsBox.setToolTipText(
                "Serve over TLS with a self-signed certificate (devices must accept it once)");
        this.autostartBox.setSelected(config.getAutostart(false));
        this.autostartBox.setToolTipText("Start the server automatically when ttDrop opens");
        this.fileOpsBox.setSelected(server.isFileOpsEnabled());
        this.fileOpsBox.setToolTipText(
                "Let devices rename and delete files in the shared folder from their browser (off by default)");
        this.dirBrowseBox.setSelected(server.isDirBrowseEnabled());
        this.dirBrowseBox.setToolTipText(
                "Show browsable folder listing pages when a /files/ URL is opened directly (off by default)");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        rootLabel.setText("File root: " + server.getFileRoot());
        rootLabel.setFont(rootLabel.getFont().deriveFont(Font.PLAIN));
        JPanel rootRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rootRow.add(rootLabel);
        rootRow.add(rootButton);
        main.add(rootRow);
        main.add(Box.createVerticalStrut(8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Port:"));
        controls.add(portField);
        controls.add(httpsBox);
        controls.add(autostartBox);
        controls.add(toggleButton);
        controls.add(statusLabel);
        main.add(controls);
        JPanel permissions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        permissions.add(fileOpsBox);
        permissions.add(dirBrowseBox);
        main.add(permissions);
        main.add(Box.createVerticalStrut(8));

        JPanel addressRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addressRow.add(new JLabel("Address:"));
        addressRow.add(addressBox);
        main.add(addressRow);
        main.add(Box.createVerticalStrut(4));
        main.add(urlLabel);
        main.add(Box.createVerticalStrut(8));
        main.add(qrPanel);

        addressBox.setVisible(false);
        qrPanel.setVisible(false);
        toggleButton.addActionListener(e -> toggle());
        rootButton.addActionListener(e -> chooseRoot());
        addressBox.addActionListener(e -> updateUrl());
        // Both take effect immediately, even while the server is running.
        fileOpsBox.addActionListener(e -> {
            server.setFileOpsEnabled(fileOpsBox.isSelected());
            config.setFileOps(fileOpsBox.isSelected());
            config.save();
        });
        dirBrowseBox.addActionListener(e -> {
            server.setDirBrowseEnabled(dirBrowseBox.isSelected());
            config.setDirBrowse(dirBrowseBox.isSelected());
            config.save();
        });
        urlLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        urlLabel.setToolTipText("Open in your default browser");
        urlLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openInBrowser();
            }
        });

        add(main, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);

        if (autostartBox.isSelected()) {
            javax.swing.SwingUtilities.invokeLater(this::toggle);
        }
    }

    /** Pick a new file root (only while stopped); persisted in config. */
    private void chooseRoot() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(server.getFileRoot().toFile());
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose the folder ttDrop shares");
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path newRoot = chooser.getSelectedFile().toPath();
        server = new TtDropServer(newRoot);
        server.setFileOpsEnabled(fileOpsBox.isSelected());
        server.setDirBrowseEnabled(dirBrowseBox.isSelected());
        config.setRoot(newRoot);
        config.save();
        rootLabel.setText("File root: " + server.getFileRoot());
        pack();
    }

    private void toggle() {
        if (server.isRunning()) {
            server.stop();
            statusLabel.setText("Stopped");
            toggleButton.setText("Start");
            portField.setEnabled(true);
            httpsBox.setEnabled(true);
            rootButton.setEnabled(true);
            addressBox.setVisible(false);
            qrPanel.setVisible(false);
            urlLabel.setText(" ");
            pack();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Invalid port", "ttDrop", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            startOn(port);
        } catch (BindException be) {
            // Another program owns this port: offer an OS-assigned free one.
            offerFreePort(port);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(this, "Could not start: " + ioe.getMessage(),
                    "ttDrop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void offerFreePort(int takenPort) {
        try {
            int freePort = TtDropServer.findFreePort();
            int choice = JOptionPane.showConfirmDialog(this,
                    "Port " + takenPort + " is already used by another program.\n"
                            + "Use free port " + freePort + " instead?\n"
                            + "(Or press No and enter a different port yourself.)",
                    "ttDrop — port in use", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                portField.setText(String.valueOf(freePort));
                startOn(freePort);
            }
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(this, "Could not start: " + ioe.getMessage(),
                    "ttDrop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startOn(int port) throws IOException {
        server.start(port, httpsBox.isSelected());
        config.setPort(port);
        config.setHttps(httpsBox.isSelected());
        config.setAutostart(autostartBox.isSelected());
        config.save();
        statusLabel.setText("Running");
        toggleButton.setText("Stop");
        portField.setEnabled(false);
        httpsBox.setEnabled(false);
        rootButton.setEnabled(false);

        List<String> addresses = new ArrayList<>(TtDropServer.lanAddresses());
        addresses.add("localhost");
        addressBox.removeAllItems();
        for (String address : addresses) {
            addressBox.addItem(address);
        }
        addressBox.setSelectedIndex(0);
        addressBox.setVisible(true);
        qrPanel.setVisible(true);
        updateUrl();
    }

    private void updateUrl() {
        Object selected = addressBox.getSelectedItem();
        if (selected == null || !server.isRunning()) {
            return;
        }
        String url = server.scheme() + "://" + selected + ":" + server.getPort() + "/";
        // Rendered as a link: clicking it opens the default browser.
        urlLabel.setText("<html><a href=\"" + url + "\">" + url + "</a></html>");
        qrPanel.show(url);
        pack();
    }

    /** Opens the currently shown URL in the user's default browser. */
    private void openInBrowser() {
        Object selected = addressBox.getSelectedItem();
        if (selected == null || !server.isRunning()) {
            return;
        }
        String url = server.scheme() + "://" + selected + ":" + server.getPort() + "/";
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return;
            }
            // Linux desktops without java.awt.Desktop browse support.
            String os = System.getProperty("os.name", "").toLowerCase();
            String[] cmd = os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", url}
                    : os.contains("mac") ? new String[] {"open", url}
                    : new String[] {"xdg-open", url};
            new ProcessBuilder(cmd).start();
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(this, "Could not open browser: " + ioe.getMessage(),
                    "ttDrop", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Paints a QR code, 4x4 px per module plus the standard quiet zone. */
    private static final class QrPanel extends JComponent {
        private static final int SCALE = 4;
        private static final int QUIET = 4;
        private boolean[][] matrix;

        void show(String text) {
            matrix = QrCode.encode(text);
            int px = (matrix.length + 2 * QUIET) * SCALE;
            Dimension d = new Dimension(px, px);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (matrix == null) {
                return;
            }
            int px = (matrix.length + 2 * QUIET) * SCALE;
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, px, px);
            g.setColor(Color.BLACK);
            for (int y = 0; y < matrix.length; y++) {
                for (int x = 0; x < matrix.length; x++) {
                    if (matrix[y][x]) {
                        g.fillRect((x + QUIET) * SCALE, (y + QUIET) * SCALE, SCALE, SCALE);
                    }
                }
            }
        }
    }
}
