package ttdrop.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ttdrop.server.TtDropServer;

/**
 * Minimal control window: shows the file root, lets the user pick a port,
 * start/stop the server, and see the URLs devices can connect to.
 */
public final class ServerWindow extends JFrame {
    private final TtDropServer server;
    private final JTextField portField;
    private final JButton toggleButton = new JButton("Start");
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JLabel urlLabel = new JLabel(" ");

    public ServerWindow(TtDropServer server, int initialPort) {
        super("ttDrop");
        this.server = server;
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
        main.add(urlLabel);

        toggleButton.addActionListener(e -> toggle());

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
            urlLabel.setText(" ");
            return;
        }
        try {
            int port = Integer.parseInt(portField.getText().trim());
            server.start(port);
            statusLabel.setText("Running");
            toggleButton.setText("Stop");
            portField.setEnabled(false);
            StringBuilder urls = new StringBuilder("<html>Open: http://localhost:" + server.getPort() + "/");
            for (String addr : TtDropServer.lanAddresses()) {
                urls.append("<br>LAN: http://").append(addr).append(':').append(server.getPort()).append('/');
            }
            urlLabel.setText(urls.append("</html>").toString());
            pack();
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Invalid port", "ttDrop", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(this, "Could not start: " + ioe.getMessage(),
                    "ttDrop", JOptionPane.ERROR_MESSAGE);
        }
    }
}
