package gui;

import core.AppManager;
import core.ConfigManager;
import filesystem.FolderWatcherDaemon;
import network.IDResolver;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;

/**
 * ConnectionPanel
 * 
 * Provides an aesthetic Cyber Neon device pairing interface where users can
 * inspect their local Connecta ID, configure the shared folder, enter a remote
 * peer's Connecta ID, and establish a real-time TCP socket connection.
 */
public class ConnectionPanel extends JPanel {

    // Cyber Neon Theme Colors
    private final Color CYAN = new Color(0, 217, 255);
    private final Color VIOLET = new Color(128, 85, 255);
    private final Color BG = new Color(9, 13, 28);
    private final Color CARD = new Color(19, 29, 53);
    private final Color CARD_BG = new Color(12, 23, 43);
    private final Color WHITE = new Color(240, 245, 255);
    private final Color MUTED = new Color(155, 171, 199);
    private final Color GREEN = new Color(52, 211, 153);
    private final Color ERROR_RED = new Color(255, 80, 120);

    private JLabel myIdLabel;
    private JLabel sharedFolderLabel;
    private JLabel statusLabel;
    private JTextField partnerField;
    private JButton copyButton;
    private JButton connectButton;

    private String myID = "Generating...";

    public ConnectionPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);

        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);

        initAppManagerListener();
    }

    private void initAppManagerListener() {
        AppManager app = AppManager.getInstance();
        displayGeneratedID(app.getMyConnectaID());
        updateSharedFolderDisplay(app.getSharedFolderPath());

        app.addListener(new AppManager.AppEventListener() {
            @Override
            public void onServerReady(String myID, int port) {
                SwingUtilities.invokeLater(() -> displayGeneratedID(myID));
            }

            @Override
            public void onPeerConnected(String peerIP, int peerPort, String peerID, String peerName) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("● Connected to " + peerName + " (" + peerIP + ")");
                    statusLabel.setForeground(GREEN);
                    connectButton.setEnabled(true);
                    connectButton.setText("Connected  ✓");
                });
            }

            @Override
            public void onPeerDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("● Disconnected: " + reason);
                    statusLabel.setForeground(MUTED);
                    connectButton.setEnabled(true);
                    connectButton.setText("Connect Device  →");
                });
            }

            @Override
            public void onConnectionError(String error) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("● " + error);
                    statusLabel.setForeground(ERROR_RED);
                    connectButton.setEnabled(true);
                    connectButton.setText("Connect Device  →");
                });
            }

            @Override
            public void onTransferProgress(String fileName, int percent, String statusText, boolean isOutgoing) {}

            @Override
            public void onTransferComplete(String fileName, boolean isOutgoing) {}

            @Override
            public void onLog(String fileName, String message) {}

            @Override
            public void onStatsUpdated(int sent, int received, int total) {}

            @Override
            public void onRatesUpdated(String uploadRate, String downloadRate) {}

            @Override
            public void onFolderStatsUpdated(FolderWatcherDaemon.FolderStats stats) {}

            @Override
            public void onSharedFolderChanged(String newPath) {
                SwingUtilities.invokeLater(() -> updateSharedFolderDisplay(newPath));
            }

            @Override
            public void onSyncStatusChanged(String statusText) {}

            @Override
            public void onPairedDevicesChanged() {}
        });
    }

    // ================= HEADER =================

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(22, 35, 15, 35));

        JLabel logo = new JLabel("∞ CONNECTA");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 25));
        logo.setForeground(CYAN);

        JLabel tagline = new JLabel("  TWO DEVICES. ONE CONNECTION.");
        tagline.setFont(new Font("Segoe UI", Font.BOLD, 9));
        tagline.setForeground(MUTED);

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brand.setOpaque(false);
        brand.add(logo);
        brand.add(tagline);

        JButton dashboardButton = new JButton("Dashboard  →");
        dashboardButton.setFont(new Font("Segoe UI", Font.BOLD, 11));
        dashboardButton.setForeground(WHITE);
        dashboardButton.setBackground(new Color(35, 49, 80));
        dashboardButton.setFocusPainted(false);
        dashboardButton.setBorder(new EmptyBorder(10, 17, 10, 17));
        dashboardButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        dashboardButton.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window instanceof ConnectaMainWindow) {
                ((ConnectaMainWindow) window).showSyncDashboard();
            }
        });

        JLabel version = new JLabel("  DEVICE PAIRING  ");
        version.setFont(new Font("Segoe UI", Font.BOLD, 10));
        version.setForeground(CYAN);
        version.setBorder(new EmptyBorder(10, 10, 10, 10));
        version.setOpaque(true);
        version.setBackground(new Color(15, 47, 68));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(dashboardButton);
        right.add(version);

        header.add(brand, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    // ================= MAIN BODY =================

    private JPanel createBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(15, 35, 30, 35));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridy = 0;
        c.weighty = 1;
        c.weightx = 1;

        c.gridx = 0;
        c.insets = new Insets(0, 0, 0, 20);
        body.add(createIllustrationPanel(), c);

        c.gridx = 1;
        c.weightx = 0.95;
        c.insets = new Insets(0, 20, 0, 0);
        body.add(createPairingPanel(), c);

        return body;
    }

    // ================= LEFT ILLUSTRATION PANEL =================

    private JPanel createIllustrationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JLabel eyebrow = new JLabel("  THE FUTURE OF CONNECTIVITY");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 11));
        eyebrow.setForeground(CYAN);

        JLabel heading = new JLabel(
                "<html>Everything<br>connected. "
                        + "<font color='#00D9FF'>Effortlessly.</font></html>");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 37));
        heading.setForeground(WHITE);

        JLabel description = new JLabel(
                "<html><div style='width:340px;'>"
                        + "Experience lightning-fast LAN folder synchronization. "
                        + "Direct peer-to-peer TCP streaming with zero cloud dependence. "
                        + "Pair once, and devices auto-reconnect anytime on the local network."
                        + "</div></html>");
        description.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        description.setForeground(MUTED);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        text.add(eyebrow);
        text.add(Box.createVerticalStrut(17));
        text.add(heading);
        text.add(Box.createVerticalStrut(14));
        text.add(description);

        DeviceIllustration illustration = new DeviceIllustration();

        JLabel feature = new JLabel(
                "  ✦  P2P SYNC       ✦  ZERO CLOUD       ✦  AUTO-RECONNECT");
        feature.setFont(new Font("Segoe UI", Font.BOLD, 11));
        feature.setForeground(CYAN);

        panel.add(text, BorderLayout.NORTH);
        panel.add(illustration, BorderLayout.CENTER);
        panel.add(feature, BorderLayout.SOUTH);

        return panel;
    }

    // ================= RIGHT PAIRING CARD =================

    private JPanel createPairingPanel() {
        RoundedPanel card = new RoundedPanel(CARD, 28);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 217, 255, 65), 1),
                new EmptyBorder(24, 25, 24, 25)));

        JLabel badge = new JLabel("  ●  QUICK PAIRING");
        badge.setOpaque(true);
        badge.setBackground(new Color(19, 56, 76));
        badge.setForeground(CYAN);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setBorder(new EmptyBorder(7, 10, 7, 10));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = new JLabel("Let's connect!");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(WHITE);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Pair two devices over your local Wi-Fi or Ethernet network.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(badge);
        card.add(Box.createVerticalStrut(14));
        card.add(heading);
        card.add(Box.createVerticalStrut(5));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(20));

        // YOUR CONNECTA ID SECTION
        card.add(sectionLabel("YOUR CONNECTA ID & LOCAL LAN IP"));
        card.add(Box.createVerticalStrut(7));

        RoundedPanel idBox = new RoundedPanel(CARD_BG, 15);
        idBox.setLayout(new BorderLayout(8, 0));
        idBox.setBorder(new EmptyBorder(10, 14, 10, 10));
        idBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        idBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel idInfoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        idInfoPanel.setOpaque(false);

        myIdLabel = new JLabel(myID);
        myIdLabel.setFont(new Font("Monospaced", Font.BOLD, 15));
        myIdLabel.setForeground(CYAN);

        String localIp = IDResolver.getLocalIPv4Address();
        JLabel myLanIpLabel = new JLabel("Local LAN IP: " + localIp);
        myLanIpLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        myLanIpLabel.setForeground(MUTED);

        idInfoPanel.add(myIdLabel);
        idInfoPanel.add(myLanIpLabel);

        copyButton = new JButton("COPY ID");
        styleSecondaryButton(copyButton);
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyMyID());

        idBox.add(idInfoPanel, BorderLayout.CENTER);
        idBox.add(copyButton, BorderLayout.EAST);

        card.add(idBox);
        card.add(Box.createVerticalStrut(16));

        // SHARED FOLDER SECTION
        card.add(sectionLabel("SHARED SYNC FOLDER"));
        card.add(Box.createVerticalStrut(7));

        RoundedPanel folderBox = new RoundedPanel(CARD_BG, 15);
        folderBox.setLayout(new BorderLayout(8, 0));
        folderBox.setBorder(new EmptyBorder(10, 14, 10, 10));
        folderBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        folderBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        sharedFolderLabel = new JLabel("...");
        sharedFolderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sharedFolderLabel.setForeground(WHITE);

        JButton changeFolderBtn = new JButton("CHANGE");
        styleSecondaryButton(changeFolderBtn);
        changeFolderBtn.addActionListener(e -> chooseSharedFolder());

        folderBox.add(sharedFolderLabel, BorderLayout.CENTER);
        folderBox.add(changeFolderBtn, BorderLayout.EAST);

        card.add(folderBox);
        card.add(Box.createVerticalStrut(16));

        // PARTNER'S CONNECTA ID INPUT
        card.add(sectionLabel("PARTNER'S CONNECTA ID OR LAN IP"));
        card.add(Box.createVerticalStrut(7));

        partnerField = new JTextField();
        partnerField.setFont(new Font("Monospaced", Font.BOLD, 15));
        partnerField.setForeground(WHITE);
        partnerField.setCaretColor(CYAN);
        partnerField.setToolTipText("Enter 8-digit Connecta ID or LAN IP address (e.g. A3F8B12C or 192.168.1.5)");
        partnerField.setBackground(CARD_BG);
        partnerField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 91, 128), 1),
                new EmptyBorder(10, 12, 10, 12)));
        partnerField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        partnerField.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(partnerField);
        card.add(Box.createVerticalStrut(16));

        // CONNECT BUTTON
        connectButton = new JButton("Connect Device  →");
        connectButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        connectButton.setForeground(Color.WHITE);
        connectButton.setBackground(new Color(91, 75, 210));
        connectButton.setFocusPainted(false);
        connectButton.setBorderPainted(false);
        connectButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        connectButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        connectButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        connectButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (connectButton.isEnabled()) {
                    connectButton.setBackground(new Color(113, 87, 245));
                }
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                if (connectButton.isEnabled()) {
                    connectButton.setBackground(new Color(91, 75, 210));
                }
            }
        });

        connectButton.addActionListener(e -> onConnectButtonClicked());

        card.add(connectButton);
        card.add(Box.createVerticalStrut(16));

        // STATUS LABEL
        statusLabel = new JLabel("●  Waiting for connection");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(statusLabel);

        return card;
    }

    private JLabel sectionLabel(String value) {
        JLabel label = new JLabel(value);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void styleSecondaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 10));
        button.setForeground(CYAN);
        button.setBackground(new Color(22, 42, 66));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void copyMyID() {
        if (myID != null && !myID.isEmpty() && !myID.startsWith("Generat") && !myID.equals("ERROR")) {
            StringSelection selection = new StringSelection(myID);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

            statusLabel.setText("●  ID copied to clipboard!");
            statusLabel.setForeground(CYAN);
        }
    }

    private void chooseSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Connecta Shared Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File(AppManager.getInstance().getSharedFolderPath()));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            AppManager.getInstance().setSharedFolderPath(selected.getAbsolutePath());
        }
    }

    private void updateSharedFolderDisplay(String path) {
        if (sharedFolderLabel != null) {
            if (path.length() > 36) {
                sharedFolderLabel.setText("..." + path.substring(path.length() - 33));
            } else {
                sharedFolderLabel.setText(path);
            }
            sharedFolderLabel.setToolTipText(path);
        }
    }

    public void displayGeneratedID(String id) {
        this.myID = id;
        if (myIdLabel != null) {
            myIdLabel.setText(id);
        }
        if (copyButton != null) {
            copyButton.setEnabled(id != null && !id.isEmpty() && !id.equals("ERROR"));
        }
    }

    public String getInputID() {
        return partnerField.getText().trim();
    }

    public void onConnectButtonClicked() {
        String partnerID = getInputID();

        if (partnerID.isEmpty()) {
            statusLabel.setText("●  Please enter partner ID or LAN IP");
            statusLabel.setForeground(ERROR_RED);
            return;
        }

        statusLabel.setText("●  Connecting to " + partnerID + "...");
        statusLabel.setForeground(CYAN);
        connectButton.setEnabled(false);

        AppManager.getInstance().connectToPeer(partnerID);
    }

    // ================= CUSTOM DEVICE GRAPHICS =================

    private class DeviceIllustration extends JPanel {
        DeviceIllustration() {
            setOpaque(false);
            setPreferredSize(new Dimension(420, 340));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            // Background glows
            g2.setColor(new Color(0, 217, 255, 22));
            g2.fillOval(cx - 170, cy - 145, 340, 290);

            g2.setColor(new Color(128, 85, 255, 30));
            g2.fillOval(cx - 130, cy - 115, 260, 230);

            // Orbit rings
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(0, 217, 255, 65));
            g2.drawOval(cx - 130, cy - 120, 260, 250);

            g2.setColor(new Color(128, 85, 255, 75));
            g2.drawOval(cx - 150, cy - 140, 300, 290);

            // Beam line
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0, 217, 255, 130));
            g2.drawLine(cx - 48, cy + 5, cx + 62, cy + 5);

            // Beam nodes
            g2.setColor(CYAN);
            g2.fillOval(cx - 55, cy - 2, 14, 14);
            g2.fillOval(cx + 55, cy - 2, 14, 14);

            // Laptop shadow
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillRoundRect(cx - 145, cy + 88, 170, 17, 10, 10);

            // Laptop screen outer
            GradientPaint laptopGradient = new GradientPaint(cx - 135, cy - 80, CYAN, cx + 15, cy + 80, VIOLET);
            g2.setPaint(laptopGradient);
            g2.fillRoundRect(cx - 135, cy - 80, 150, 165, 15, 15);

            // Laptop screen inner
            g2.setColor(new Color(9, 17, 36));
            g2.fillRoundRect(cx - 127, cy - 72, 134, 149, 10, 10);

            g2.setColor(CYAN);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 35));
            g2.drawString("∞", cx - 83, cy + 5);

            g2.setColor(WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.drawString("CONNECTA", cx - 88, cy + 27);

            // Laptop base
            g2.setColor(new Color(115, 141, 177));
            int[] lx = { cx - 145, cx + 25, cx + 40, cx - 130 };
            int[] ly = { cy + 85, cy + 85, cy + 98, cy + 98 };
            g2.fillPolygon(lx, ly, 4);

            // Phone shadow
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(cx + 52, cy + 95, 100, 15, 12, 12);

            // Phone outer
            GradientPaint phoneGradient = new GradientPaint(cx + 50, cy - 120, VIOLET, cx + 155, cy + 100, CYAN);
            g2.setPaint(phoneGradient);
            g2.fillRoundRect(cx + 55, cy - 120, 95, 210, 22, 22);

            // Phone screen
            g2.setColor(new Color(10, 20, 40));
            g2.fillRoundRect(cx + 63, cy - 109, 79, 187, 15, 15);

            g2.setColor(CYAN);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 29));
            g2.drawString("∞", cx + 88, cy - 12);

            g2.setColor(WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 8));
            g2.drawString("CONNECTED", cx + 79, cy + 10);

            // Sparkles
            g2.setFont(new Font("Segoe UI", Font.BOLD, 29));
            g2.setColor(CYAN);
            g2.drawString("✦", cx + 115, cy - 135);

            g2.setColor(VIOLET);
            g2.drawString("✧", cx - 170, cy - 25);

            // Labels
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(CYAN);
            g2.drawString("HOST DEVICE", cx - 110, cy + 125);

            g2.setColor(new Color(180, 150, 255));
            g2.drawString("PEER DEVICE", cx + 60, cy + 125);

            g2.dispose();
        }
    }

    private static class RoundedPanel extends JPanel {
        private final Color panelColor;
        private final int radius;

        RoundedPanel(Color color, int radius) {
            this.panelColor = color;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(panelColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
