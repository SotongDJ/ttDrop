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
    private final TtDropServer server;
    private final Config config;
    private final JTextField portField;
    private final JButton toggleButton = new JButton("Start");
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JComboBox<String> addressBox = new JComboBox<>();
    private final JLabel urlLabel = new JLabel(" ");
    private final QrPanel qrPanel = new QrPanel();

    public ServerWindow(TtDropServer server, int initialPort, Config config) {
        super("ttDrop");
        this.server = server;
        this.config = config;
        this.portField = new JTextField(String.valueOf(initialPort), 6);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel rootLabel = new JLabel("File root: " + server.getFileRoot());
        rootLabel.setFont(rootLabel.getFont().deriveFont(Font.PLAIN));
        main.add(rootLabel);
        main.add(Box.createVerticalStrut(8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Port:"));
        controls.add(portField);
        controls.add(toggleButton);
        controls.add(statusLabel);
        main.add(controls);
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
        addressBox.addActionListener(e -> updateUrl());

        add(main, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }

    private void toggle() {
        if (server.isRunning()) {
            server.stop();
            statusLabel.setText("Stopped");
            toggleButton.setText("Start");
            portField.setEnabled(true);
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
        server.start(port);
        config.setPort(port);
        config.save();
        statusLabel.setText("Running");
        toggleButton.setText("Stop");
        portField.setEnabled(false);

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
        String url = "http://" + selected + ":" + server.getPort() + "/";
        urlLabel.setText(url);
        qrPanel.show(url);
        pack();
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
