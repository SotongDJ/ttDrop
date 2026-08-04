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
    private final javax.swing.JCheckBox pairingBox = new javax.swing.JCheckBox("Require device pairing");
    private final JButton pairButton = new JButton("Pair device…");
    private final JPanel devicesPanel = new JPanel();
    private final JButton toggleButton = new JButton("Start");
    private final JButton rootButton = new JButton("Change…");
    private final JLabel rootLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JComboBox<String> addressBox = new JComboBox<>();
    private final JLabel urlLabel = new JLabel(" ");
    private final QrPanel qrPanel = new QrPanel();

    public ServerWindow(TtDropServer server, int initialPort, boolean initialHttps, Config config) {
        super("ttDrop v" + ttdrop.Main.VERSION);
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
        this.pairingBox.setSelected(server.isPairingRequired());
        this.pairingBox.setToolTipText(
                "Devices must pair with a one-time code before seeing anything (on by default)");
        this.pairButton.setToolTipText("Show a one-time pairing code as QR and text");
        this.pairButton.setEnabled(false);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        applyAppIcon();
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
        JPanel pairingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pairingRow.add(pairingBox);
        pairingRow.add(pairButton);
        main.add(pairingRow);
        devicesPanel.setLayout(new BoxLayout(devicesPanel, BoxLayout.Y_AXIS));
        devicesPanel.setBorder(BorderFactory.createTitledBorder("Devices"));
        main.add(devicesPanel);
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
        pairingBox.addActionListener(e -> {
            server.setPairingRequired(pairingBox.isSelected());
            config.setPairing(pairingBox.isSelected());
            config.save();
            pairButton.setEnabled(server.isRunning() && pairingBox.isSelected());
        });
        pairButton.addActionListener(e -> showPairDialog());
        server.devices().setOnChange(() ->
                javax.swing.SwingUtilities.invokeLater(this::rebuildDevices));
        rebuildDevices();
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

    /** Window/taskbar icon: the dark-background variant (resources). */
    private void applyAppIcon() {
        try (java.io.InputStream in = ServerWindow.class.getResourceAsStream("/ttdrop/icon.png")) {
            if (in == null) {
                return;
            }
            java.awt.image.BufferedImage icon = javax.imageio.ImageIO.read(in);
            setIconImage(icon);
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(icon);
                }
            }
        } catch (Exception ignored) {
            // the default Java icon is a cosmetic fallback only
        }
    }

    /** One-time pairing code as QR + copyable text (server must run). */
    private void showPairDialog() {
        Object selected = addressBox.getSelectedItem();
        String host = selected == null ? "localhost" : selected.toString();
        String code = server.devices().newPairingCode();
        String url = server.scheme() + "://" + host + ":" + server.getPort() + "/?pair="
                + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8);

        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "Pair a device", false);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        panel.add(new JLabel("Scan with the device's camera, or open the site and enter the code:"));
        panel.add(Box.createVerticalStrut(8));
        QrPanel qr = new QrPanel();
        qr.show(url);
        panel.add(qr);
        panel.add(Box.createVerticalStrut(8));
        JTextField codeField = new JTextField(code, 10);
        codeField.setEditable(false);
        JButton copyButton = new JButton("Copy");
        copyButton.addActionListener(e -> java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(code), null));
        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        codeRow.add(new JLabel("Code:"));
        codeRow.add(codeField);
        codeRow.add(copyButton);
        panel.add(codeRow);
        JLabel hint = new JLabel("Valid 10 minutes; pairs one device.");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN));
        panel.add(hint);
        dialog.add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** Rebuilds the per-device permission rows from the registry. */
    private void rebuildDevices() {
        devicesPanel.removeAll();
        java.util.List<ttdrop.server.Devices.Device> all = server.devices().list();
        if (all.isEmpty()) {
            JLabel none = new JLabel("No paired devices yet.");
            none.setFont(none.getFont().deriveFont(Font.PLAIN));
            devicesPanel.add(none);
        }
        for (ttdrop.server.Devices.Device device : all) {
            devicesPanel.add(deviceRow(device));
        }
        devicesPanel.revalidate();
        devicesPanel.repaint();
        pack();
    }

    private JPanel deviceRow(ttdrop.server.Devices.Device device) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.add(new JLabel(device.name()));
        JLabel pathLabel = new JLabel(
                device.relPath().isEmpty() ? "→ (everything)" : "→ " + device.relPath() + "/");
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN));
        pathLabel.setToolTipText("The only folder this device can see");
        row.add(pathLabel);
        JButton folderButton = new JButton("Folder…");
        folderButton.setToolTipText("Choose which folder this device may access"
                + " (the shared folder itself grants everything)");
        folderButton.addActionListener(e -> chooseDeviceFolder(device));
        row.add(folderButton);
        JButton subButton = new JButton("Subfolders…");
        subButton.setToolTipText(
                "Choose which subfolders this device may read and write (all allowed by default)");
        subButton.addActionListener(e -> showSubfoldersDialog(device.id()));
        row.add(subButton);
        row.add(permissionBox("Read", "Allow downloads and listings", device.read(),
                v -> updateDevice(device.id(), d -> d.withRead(v))));
        row.add(permissionBox("Write", "Allow uploads (and rename/delete when enabled)",
                device.write(), v -> updateDevice(device.id(), d -> d.withWrite(v))));
        row.add(permissionBox("Browse", "Allow /files/ listing pages for this device",
                device.browse(), v -> updateDevice(device.id(), d -> d.withBrowse(v))));
        JButton renameButton = new JButton("Rename…");
        renameButton.setToolTipText(
                "Rename this device (a-z, 0-9, _); its folder is renamed with it");
        renameButton.addActionListener(e -> renameDevice(device));
        row.add(renameButton);
        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this,
                    "Unpair \"" + device.name() + "\"? The device will need a new code.",
                    "ttDrop", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                server.devices().remove(device.id());
            }
        });
        row.add(removeButton);
        return row;
    }

    /** Rename a device (and its folder, when it is scoped to its own). */
    private void renameDevice(ttdrop.server.Devices.Device device) {
        String input = (String) JOptionPane.showInputDialog(this,
                "New name for \"" + device.name() + "\" (lower-case a-z, 0-9, _):",
                "ttDrop", JOptionPane.PLAIN_MESSAGE, null, null, device.name());
        if (input == null || input.equals(device.name())) {
            return;
        }
        String error = server.devices().rename(device.id(), input.trim(), server.getFileRoot());
        if (error != null) {
            String message = switch (error) {
                case "name" -> "Names may only use lower-case a-z, 0-9 and _ (1-32 chars).";
                case "taken" -> "That name is already used by another device.";
                case "dir" -> "The device's folder could not be renamed (does the target exist?).";
                default -> "Rename failed.";
            };
            JOptionPane.showMessageDialog(this, message, "ttDrop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private javax.swing.JCheckBox permissionBox(String label, String tip, boolean value,
            java.util.function.Consumer<Boolean> onChange) {
        javax.swing.JCheckBox box = new javax.swing.JCheckBox(label, value);
        box.setToolTipText(tip);
        box.addActionListener(e -> onChange.accept(box.isSelected()));
        return box;
    }

    /** Restrict a device to a folder inside the shared root. */
    private void chooseDeviceFolder(ttdrop.server.Devices.Device device) {
        java.nio.file.Path fileRoot = server.getFileRoot();
        java.nio.file.Path chosen = FolderPicker.pick(this,
                "Folder \"" + device.name() + "\" may access (shared folder = everything)",
                device.resolveRoot(fileRoot), fileRoot);
        if (chosen == null) {
            return;
        }
        String rel = fileRoot.relativize(chosen).toString().replace('\\', '/');
        updateDevice(device.id(), d -> d.withRelPath(rel));
    }

    /** Applies a change to the latest registry state of a device. */
    private void updateDevice(String id,
            java.util.function.UnaryOperator<ttdrop.server.Devices.Device> change) {
        ttdrop.server.Devices.Device current = server.devices().get(id);
        if (current != null) {
            server.devices().update(change.apply(current));
        }
    }

    /**
     * Checklist of the device's top-level subfolders with Read/Write
     * boxes; everything is allowed unless unticked (deny list, so
     * folders created later are allowed automatically).
     */
    private void showSubfoldersDialog(String deviceId) {
        ttdrop.server.Devices.Device device = server.devices().get(deviceId);
        if (device == null) {
            return;
        }
        java.nio.file.Path deviceRoot = device.resolveRoot(server.getFileRoot());
        java.util.List<String> subs = new java.util.ArrayList<>();
        try (var children = java.nio.file.Files.list(deviceRoot)) {
            for (java.nio.file.Path child : (Iterable<java.nio.file.Path>) children.sorted()::iterator) {
                String name = child.getFileName().toString();
                if (java.nio.file.Files.isDirectory(child) && !name.startsWith(".")) {
                    subs.add(name);
                }
            }
        } catch (java.io.IOException ignored) {
            // unreadable device folder: show it empty
        }
        javax.swing.JDialog dialog = new javax.swing.JDialog(this,
                "Subfolders \"" + device.name() + "\" may use", false);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        if (subs.isEmpty()) {
            panel.add(new JLabel("No subfolders yet — everything inside is readable and writable."));
        } else {
            panel.add(new JLabel("Untick to block; new subfolders are always allowed."));
            panel.add(Box.createVerticalStrut(6));
        }
        for (String sub : subs) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            JLabel nameLabel = new JLabel(sub + "/");
            row.add(nameLabel);
            row.add(permissionBox("Read", "Allow reading " + sub,
                    !device.denyRead().contains(sub),
                    v -> updateDevice(deviceId, d -> d.withDeny(
                            toggled(d.denyRead(), sub, !v), d.denyWrite()))));
            row.add(permissionBox("Write", "Allow writing into " + sub,
                    !device.denyWrite().contains(sub),
                    v -> updateDevice(deviceId, d -> d.withDeny(
                            d.denyRead(), toggled(d.denyWrite(), sub, !v)))));
            panel.add(row);
        }
        dialog.add(new javax.swing.JScrollPane(panel));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static java.util.Set<String> toggled(java.util.Set<String> set, String name,
            boolean deny) {
        java.util.Set<String> out = new java.util.TreeSet<>(set);
        if (deny) {
            out.add(name);
        } else {
            out.remove(name);
        }
        return out;
    }

    /** Pick a new file root (only while stopped); persisted in config. */
    private void chooseRoot() {
        java.nio.file.Path newRoot = FolderPicker.pick(this,
                "Choose the folder ttDrop shares", server.getFileRoot(), null);
        if (newRoot == null) {
            return;
        }
        server = new TtDropServer(newRoot);
        server.setFileOpsEnabled(fileOpsBox.isSelected());
        server.setDirBrowseEnabled(dirBrowseBox.isSelected());
        server.setPairingRequired(pairingBox.isSelected());
        server.devices().setOnChange(() ->
                javax.swing.SwingUtilities.invokeLater(this::rebuildDevices));
        rebuildDevices();
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
            pairButton.setEnabled(false);
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
        pairButton.setEnabled(pairingBox.isSelected());

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
