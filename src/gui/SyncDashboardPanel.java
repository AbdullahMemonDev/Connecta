package gui;

import core.AppManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * SyncDashboardPanel
 * 
 * Central Control Center for Connecta. Features real-time file synchronization monitoring,
 * live TCP streaming progress bars, active folder tracking, quick actions (Send Files,
 * Undo Sync via DSA LIFO Stack), and dynamic Light/Dark Cyber Neon theme switching.
 */
public class SyncDashboardPanel extends JPanel {

    // ================= THEME COLOR PALETTES =================

    // Dark Mode (Cyber Neon Theme)
    private static final Color DARK_BG = new Color(9, 13, 28);           // #090D1C
    private static final Color DARK_CARD = new Color(17, 27, 53);        // #111B35
    private static final Color DARK_CARD2 = new Color(22, 35, 66);       // #162342
    private static final Color DARK_TEXT = new Color(235, 243, 255);     // #EBF3FF
    private static final Color DARK_MUTED = new Color(150, 168, 199);    // #96A8C7
    private static final Color DARK_SELECTED = new Color(20, 58, 83);

    // Light Mode (Clean Modern Theme)
    private static final Color LIGHT_BG = new Color(239, 245, 252);      // #EFF5FC
    private static final Color LIGHT_CARD = new Color(255, 255, 255);    // #FFFFFF
    private static final Color LIGHT_CARD2 = new Color(226, 236, 248);   // #E2ECF8
    private static final Color LIGHT_TEXT = new Color(23, 35, 58);       // #17233A
    private static final Color LIGHT_MUTED = new Color(91, 108, 133);    // #5B6C85
    private static final Color LIGHT_SELECTED = new Color(218, 241, 250);

    // Accent Colors
    private static final Color CYAN = new Color(0, 217, 255);            // #00D9FF
    private static final Color VIOLET = new Color(128, 85, 255);         // #8055FF
    private static final Color PINK = new Color(255, 60, 172);           // #FF3CAC
    private static final Color GREEN = new Color(52, 211, 153);          // #34D399

    // Active Theme State
    private boolean darkMode = true;
    private boolean soundEnabled = true;

    private Color currentBg = DARK_BG;
    private Color currentCard = DARK_CARD;
    private Color currentCard2 = DARK_CARD2;
    private Color currentText = DARK_TEXT;
    private Color currentMuted = DARK_MUTED;
    private Color currentSelected = DARK_SELECTED;

    // Live UI Components
    private JProgressBar transferProgress;
    private JLabel progressLabel;
    private JLabel progressStatus;
    private JTextArea logArea;
    private JLabel devicePulse;
    private JLabel connectionStatusLabel;
    private JLabel partnerDeviceLabel;
    private JLabel sharedFolderDisplayLabel;

    // Stat Value Labels
    private JLabel filesSentLabel;
    private JLabel filesReceivedLabel;
    private JLabel totalTransfersLabel;

    // Animation & Status
    private Timer pulseTimer;
    private int pulse = 0;

    public SyncDashboardPanel() {
        setLayout(new BorderLayout());
        setBackground(currentBg);

        buildUI();
        startDeviceAnimation();
        initBackendObserver();
    }

    private void buildUI() {
        removeAll();
        add(createSidebar(), BorderLayout.WEST);
        add(createMainArea(), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // ================= BACKEND INTEGRATION =================

    private void initBackendObserver() {
        AppManager.getInstance().addListener(new AppManager.AppEventListener() {
            @Override
            public void onServerReady(String myID, int port) {}

            @Override
            public void onPeerConnected(String peerIP) {
                SwingUtilities.invokeLater(() -> {
                    if (connectionStatusLabel != null) {
                        connectionStatusLabel.setText("● CONNECTED TO " + peerIP);
                        connectionStatusLabel.setForeground(GREEN);
                    }
                    if (partnerDeviceLabel != null) {
                        partnerDeviceLabel.setText(peerIP);
                    }
                    playChime();
                });
            }

            @Override
            public void onPeerDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    if (connectionStatusLabel != null) {
                        connectionStatusLabel.setText("● DISCONNECTED • Waiting for peer");
                        connectionStatusLabel.setForeground(PINK);
                    }
                    if (partnerDeviceLabel != null) {
                        partnerDeviceLabel.setText("OFFLINE");
                    }
                });
            }

            @Override
            public void onConnectionError(String error) {}

            @Override
            public void onTransferProgress(String fileName, int percent, String statusText, boolean isOutgoing) {
                SwingUtilities.invokeLater(() -> updateProgressBar(percent, statusText));
            }

            @Override
            public void onTransferComplete(String fileName, boolean isOutgoing) {
                SwingUtilities.invokeLater(() -> {
                    updateProgressBar(100, (isOutgoing ? "Sent: " : "Received: ") + fileName);
                    playChime();
                });
            }

            @Override
            public void onLog(String fileName, String message) {
                SwingUtilities.invokeLater(() -> addLog(fileName, message));
            }

            @Override
            public void onStatsUpdated(int sent, int received, int total) {
                SwingUtilities.invokeLater(() -> updateMetrics(sent, received, total));
            }

            @Override
            public void onSharedFolderChanged(String newPath) {
                SwingUtilities.invokeLater(() -> {
                    if (sharedFolderDisplayLabel != null) {
                        sharedFolderDisplayLabel.setText(truncatePath(newPath, 40));
                        sharedFolderDisplayLabel.setToolTipText(newPath);
                    }
                });
            }
        });
    }

    // ================= SIDEBAR =================

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(225, 0));
        sidebar.setBackground(currentCard);
        sidebar.setBorder(new EmptyBorder(25, 18, 20, 18));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("∞ CONNECTA");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 23));
        logo.setForeground(CYAN);

        JLabel tagline = smallLabel("LAN SYNCHRONIZATION");

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(35));

        sidebar.add(sidebarHeading("WORKSPACE"));
        sidebar.add(Box.createVerticalStrut(12));

        sidebar.add(navItem("⌂", "Overview", () -> {}));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navItem("⇄", "File Transfers", this::showSendFilesDialog));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navItem("▣", "Shared Folder", this::openSharedFolderInExplorer));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navItem("↺", "Undo Recent Sync", this::executeUndoRecentTransfer));
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(navItem("▦", "QR Pairing", this::showQrDialog));

        sidebar.add(Box.createVerticalGlue());

        JButton backButton = neonButton("← Back to Pairing", false);
        backButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        backButton.addActionListener(e -> {
            playChime();
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window instanceof ConnectaMainWindow) {
                ((ConnectaMainWindow) window).showConnectionScreen();
            }
        });

        sidebar.add(backButton);
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(smallLabel("CONNECTA  •  DSA PROJECT"));

        return sidebar;
    }

    private JLabel sidebarHeading(String text) {
        JLabel label = smallLabel(text);
        label.setForeground(CYAN);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        return label;
    }

    private JPanel navItem(String icon, String name, Runnable action) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 9));
        panel.setOpaque(true);
        panel.setBackground(currentCard);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        iconLabel.setForeground(CYAN);

        JLabel textLabel = new JLabel(name);
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textLabel.setForeground(currentMuted);

        panel.add(iconLabel);
        panel.add(textLabel);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                panel.setBackground(currentCard2);
                textLabel.setForeground(currentText);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                panel.setBackground(currentCard);
                textLabel.setForeground(currentMuted);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                playChime();
                if (action != null) action.run();
            }
        });

        return panel;
    }

    // ================= MAIN AREA =================

    private JPanel createMainArea() {
        JPanel main = new JPanel(new BorderLayout(0, 16));
        main.setOpaque(false);
        main.setBorder(new EmptyBorder(22, 25, 20, 25));

        main.add(createTopBar(), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createWelcomeBanner());
        content.add(Box.createVerticalStrut(16));

        content.add(createStatsRow());
        content.add(Box.createVerticalStrut(18));

        content.add(sectionTitle("Quick Synchronization Actions"));
        content.add(Box.createVerticalStrut(10));

        content.add(createActionCards());
        content.add(Box.createVerticalStrut(18));

        content.add(createDeviceCard());
        content.add(Box.createVerticalStrut(18));

        content.add(createTransferAndSharedFolderRow());
        content.add(Box.createVerticalStrut(18));

        content.add(createHistoryAndUndoRow());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        main.add(scroll, BorderLayout.CENTER);

        return main;
    }

    // ================= TOP BAR =================

    private JPanel createTopBar() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Synchronization Dashboard");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(currentText);

        JLabel subtitle = new JLabel("High-Performance LAN Folder Mirroring with MD5 Checksum Verification");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(currentMuted);

        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(subtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        right.setOpaque(false);

        // Theme Toggle Button
        JButton themeButton = neonButton(darkMode ? "☼ Light Mode" : "☾ Dark Mode", false);
        themeButton.addActionListener(e -> {
            darkMode = !darkMode;
            applyTheme();
            playChime();
        });

        // Sound Toggle Button
        JButton soundButton = neonButton(soundEnabled ? "♫ Sound ON" : "♫ Sound OFF", false);
        soundButton.addActionListener(e -> {
            soundEnabled = !soundEnabled;
            soundButton.setText(soundEnabled ? "♫ Sound ON" : "♫ Sound OFF");
            playChime();
        });

        right.add(themeButton);
        right.add(soundButton);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    // ================= WELCOME BANNER =================

    private JPanel createWelcomeBanner() {
        NeonPanel banner = new NeonPanel(currentCard2, 20, VIOLET);
        banner.setLayout(new BorderLayout());
        banner.setBorder(new EmptyBorder(18, 22, 18, 22));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel eyebrow = smallLabel("ACTIVE LAN SYNCHRONIZATION ENGINE");
        eyebrow.setForeground(CYAN);

        JLabel heading = new JLabel("Connecta Live Dashboard");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
        heading.setForeground(currentText);

        JLabel description = new JLabel(
                "Real-time N-ary tree file monitoring, O(1) hash table loop prevention, and 8KB sequential array streaming.");
        description.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        description.setForeground(currentMuted);

        text.add(eyebrow);
        text.add(Box.createVerticalStrut(5));
        text.add(heading);
        text.add(Box.createVerticalStrut(4));
        text.add(description);

        JLabel infinity = new JLabel("∞");
        infinity.setFont(new Font("Segoe UI", Font.BOLD, 65));
        infinity.setForeground(CYAN);

        banner.add(text, BorderLayout.WEST);
        banner.add(infinity, BorderLayout.EAST);

        return banner;
    }

    // ================= STATS ROW =================

    private JPanel createStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 14, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));

        AppManager app = AppManager.getInstance();

        filesSentLabel = new JLabel(String.valueOf(app.getFilesSentCount()));
        filesReceivedLabel = new JLabel(String.valueOf(app.getFilesReceivedCount()));
        totalTransfersLabel = new JLabel(String.valueOf(app.getTotalTransfersCount()));

        row.add(statCard("↑", "FILES SENT", filesSentLabel, CYAN));
        row.add(statCard("↓", "FILES RECEIVED", filesReceivedLabel, VIOLET));
        row.add(statCard("✦", "TOTAL TRANSFERS", totalTransfersLabel, PINK));

        return row;
    }

    private JPanel statCard(String icon, String title, JLabel valueLabel, Color accent) {
        NeonPanel panel = new NeonPanel(currentCard, 16, accent);
        panel.setLayout(new BorderLayout(10, 0));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel symbol = new JLabel(icon);
        symbol.setFont(new Font("Segoe UI", Font.BOLD, 26));
        symbol.setForeground(accent);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel heading = smallLabel(title);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        valueLabel.setForeground(currentText);

        labels.add(heading);
        labels.add(Box.createVerticalStrut(3));
        labels.add(valueLabel);

        panel.add(symbol, BorderLayout.WEST);
        panel.add(labels, BorderLayout.CENTER);

        return panel;
    }

    // ================= QUICK ACTIONS =================

    private JPanel createActionCards() {
        JPanel row = new JPanel(new GridLayout(1, 3, 14, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 102));

        row.add(actionCard("↑", "Send Files", "Select any file to stream to peer", CYAN, this::showSendFilesDialog));
        row.add(actionCard("↺", "Undo Recent Sync", "LIFO Stack rollback of last sync", PINK, this::executeUndoRecentTransfer));
        row.add(actionCard("▦", "QR Pairing", "Display peer connection code", VIOLET, this::showQrDialog));

        return row;
    }

    private JPanel actionCard(String icon, String title, String description, Color accent, Runnable action) {
        NeonPanel panel = new NeonPanel(currentCard, 16, accent);
        panel.setLayout(new BorderLayout(12, 0));
        panel.setBorder(new EmptyBorder(14, 16, 14, 16));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel symbol = new JLabel(icon);
        symbol.setFont(new Font("Segoe UI", Font.BOLD, 28));
        symbol.setForeground(accent);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(title);
        name.setFont(new Font("Segoe UI", Font.BOLD, 14));
        name.setForeground(currentText);

        JLabel detail = new JLabel(description);
        detail.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        detail.setForeground(currentMuted);

        JLabel open = new JLabel("EXECUTE →");
        open.setFont(new Font("Segoe UI", Font.BOLD, 9));
        open.setForeground(accent);

        labels.add(name);
        labels.add(Box.createVerticalStrut(3));
        labels.add(detail);
        labels.add(Box.createVerticalStrut(4));
        labels.add(open);

        panel.add(symbol, BorderLayout.WEST);
        panel.add(labels, BorderLayout.CENTER);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                playChime();
                if (action != null) action.run();
            }
        });

        return panel;
    }

    // ================= DEVICE CONNECTION CARD =================

    private JPanel createDeviceCard() {
        NeonPanel card = new NeonPanel(currentCard, 18, CYAN);
        card.setLayout(new BorderLayout(15, 0));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 145));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        heading.add(sectionTitle("Active Device Link"));
        heading.add(Box.createVerticalStrut(3));
        heading.add(smallLabel("Direct SocketStreamer TCP link over Local Area Network"));

        JPanel visual = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 4));
        visual.setOpaque(false);

        AppManager app = AppManager.getInstance();
        visual.add(deviceTile("▰", "MY ID: " + app.getMyConnectaID(), CYAN));

        devicePulse = new JLabel("◉ ⇄ ◉");
        devicePulse.setFont(new Font("Segoe UI", Font.BOLD, 22));
        devicePulse.setForeground(CYAN);
        visual.add(devicePulse);

        String peer = app.getConnectedPeerAddress() != null ? app.getConnectedPeerAddress() : "OFFLINE";
        partnerDeviceLabel = new JLabel(peer);
        visual.add(deviceTile("▯", "PEER: " + peer, VIOLET));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(heading, BorderLayout.NORTH);
        center.add(visual, BorderLayout.CENTER);

        boolean conn = app.isConnected();
        connectionStatusLabel = smallLabel(conn 
                ? "● CONNECTED TO " + app.getConnectedPeerAddress() 
                : "● WAITING FOR PARTNER CONNECTION");
        connectionStatusLabel.setForeground(conn ? GREEN : PINK);

        card.add(center, BorderLayout.CENTER);
        card.add(connectionStatusLabel, BorderLayout.SOUTH);

        return card;
    }

    private JPanel deviceTile(String icon, String name, Color accent) {
        JPanel tile = new JPanel();
        tile.setOpaque(false);
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));

        JLabel glyph = new JLabel(icon);
        glyph.setFont(new Font("Segoe UI", Font.BOLD, 32));
        glyph.setForeground(accent);
        glyph.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel label = smallLabel(name);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        tile.add(glyph);
        tile.add(label);

        return tile;
    }

    // ================= PROGRESS & SHARED FOLDER ROW =================

    private JPanel createTransferAndSharedFolderRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 185));

        row.add(createProgressCard());
        row.add(createSharedFolderCard());

        return row;
    }

    private JPanel createProgressCard() {
        NeonPanel card = new NeonPanel(currentCard, 18, VIOLET);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        top.add(sectionTitle("Real-Time Packet Streaming"), BorderLayout.WEST);

        progressLabel = new JLabel("0%");
        progressLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        progressLabel.setForeground(CYAN);
        top.add(progressLabel, BorderLayout.EAST);

        transferProgress = new JProgressBar(0, 100);
        transferProgress.setValue(0);
        transferProgress.setBorderPainted(false);
        transferProgress.setStringPainted(false);
        transferProgress.setBackground(currentCard2);
        transferProgress.setForeground(CYAN);
        transferProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));

        progressStatus = smallLabel("No active transfers (Listening for file changes)");

        JButton sendBtn = neonButton("Choose & Send File", true);
        sendBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendBtn.addActionListener(e -> showSendFilesDialog());

        card.add(top);
        card.add(Box.createVerticalStrut(14));
        card.add(transferProgress);
        card.add(Box.createVerticalStrut(8));
        card.add(progressStatus);
        card.add(Box.createVerticalGlue());
        card.add(sendBtn);

        return card;
    }

    private JPanel createSharedFolderCard() {
        NeonPanel card = new NeonPanel(currentCard, 18, CYAN);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(sectionTitle("Shared Synchronization Directory"), BorderLayout.WEST);

        String path = AppManager.getInstance().getSharedFolderPath();
        sharedFolderDisplayLabel = new JLabel(truncatePath(path, 38));
        sharedFolderDisplayLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sharedFolderDisplayLabel.setForeground(currentText);
        sharedFolderDisplayLabel.setToolTipText(path);

        JLabel info = smallLabel("Any file placed inside this directory is automatically synchronized.");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);

        JButton openBtn = neonButton("Open in Explorer", false);
        openBtn.addActionListener(e -> openSharedFolderInExplorer());

        JButton changeBtn = neonButton("Change Folder", true);
        changeBtn.addActionListener(e -> chooseNewSharedFolder());

        buttons.add(openBtn);
        buttons.add(changeBtn);

        card.add(top);
        card.add(Box.createVerticalStrut(12));
        card.add(sharedFolderDisplayLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(info);
        card.add(Box.createVerticalGlue());
        card.add(buttons);

        return card;
    }

    // ================= HISTORY AND LIFO STACK UNDO ROW =================

    private JPanel createHistoryAndUndoRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        row.add(createHistoryCard());
        row.add(createStackUndoCard());

        return row;
    }

    private JPanel createHistoryCard() {
        NeonPanel card = new NeonPanel(currentCard, 18, CYAN);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setForeground(currentMuted);
        logArea.setBackground(currentCard2);
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        logArea.setText("● Connecta Engine Started • Awaiting synchronization events...\n");

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(currentCard2);

        card.add(sectionTitle("Real-Time Synchronization Log"), BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    private JPanel createStackUndoCard() {
        NeonPanel card = new NeonPanel(currentCard, 18, PINK);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = sectionTitle("DSA Concept: LIFO Stack History");
        JLabel desc = new JLabel("<html><div style='width:240px;'>"
                + "Transferred files are stored in a Last-In-First-Out (LIFO) Stack. "
                + "Clicking 'Undo Last Sync' pops the top file in O(1) time and reverts it on both devices."
                + "</div></html>");
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        desc.setForeground(currentMuted);

        JButton undoBtn = neonButton("↺ Undo Last Sync (Stack Pop)", true);
        undoBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        undoBtn.addActionListener(e -> executeUndoRecentTransfer());

        card.add(title);
        card.add(Box.createVerticalStrut(10));
        card.add(desc);
        card.add(Box.createVerticalGlue());
        card.add(undoBtn);

        return card;
    }

    // ================= ACTION HANDLERS =================

    private void showSendFilesDialog() {
        if (!AppManager.getInstance().isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "No active peer connected.\nPlease pair with a device first via the Connection screen.",
                    "Device Offline", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select File to Synchronize");
        chooser.setCurrentDirectory(new File(AppManager.getInstance().getSharedFolderPath()));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            AppManager.getInstance().syncFileToPeer(selected, selected.getName());
            addLog(selected.getName(), "Manually initiated transfer");
            playChime();
        }
    }

    private void executeUndoRecentTransfer() {
        String undone = AppManager.getInstance().undoRecentTransfer();
        if (undone == null) {
            JOptionPane.showMessageDialog(this,
                    "Transfer History Stack is empty. No recent files to undo.",
                    "Stack Empty", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Successfully rolled back file:\n" + undone + "\n(Removed locally and partner notified via LIFO Stack Pop)",
                    "Undo Complete", JOptionPane.INFORMATION_MESSAGE);
            playChime();
        }
    }

    private void openSharedFolderInExplorer() {
        try {
            Desktop.getDesktop().open(new File(AppManager.getInstance().getSharedFolderPath()));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open folder: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseNewSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select New Shared Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            AppManager.getInstance().setSharedFolderPath(selected.getAbsolutePath());
            playChime();
        }
    }

    private void showQrDialog() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setPreferredSize(new Dimension(300, 240));

        panel.add(new QRPlaceholder(), BorderLayout.CENTER);

        String id = AppManager.getInstance().getMyConnectaID();
        JLabel note = new JLabel("<html><center>Connecta ID: <font color='#00D9FF'><b>" + id + "</b></font><br>"
                + "Scan or enter this ID on the partner device<br>to initiate seamless LAN sync.</center></html>",
                SwingConstants.CENTER);
        note.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        panel.add(note, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, panel, "Connecta | QR Pairing Code", JOptionPane.PLAIN_MESSAGE);
    }

    // ================= PROGRESS & LOGGING =================

    public void updateProgressBar(int percent, String status) {
        int safe = Math.max(0, Math.min(100, percent));
        if (transferProgress != null) transferProgress.setValue(safe);
        if (progressLabel != null) progressLabel.setText(safe + "%");
        if (progressStatus != null) progressStatus.setText(status);
    }

    public void updateMetrics(int sent, int rec, int total) {
        if (filesSentLabel != null) filesSentLabel.setText(String.valueOf(sent));
        if (filesReceivedLabel != null) filesReceivedLabel.setText(String.valueOf(rec));
        if (totalTransfersLabel != null) totalTransfersLabel.setText(String.valueOf(total));
    }

    public void addLog(String fileName, String status) {
        if (logArea == null) return;
        String time = new SimpleDateFormat("hh:mm:ss a").format(new Date());
        logArea.append("• " + time + " | " + fileName + " — " + status + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ================= THEME TOGGLE =================

    private void applyTheme() {
        currentBg = darkMode ? DARK_BG : LIGHT_BG;
        currentCard = darkMode ? DARK_CARD : LIGHT_CARD;
        currentCard2 = darkMode ? DARK_CARD2 : LIGHT_CARD2;
        currentText = darkMode ? DARK_TEXT : LIGHT_TEXT;
        currentMuted = darkMode ? DARK_MUTED : LIGHT_MUTED;
        currentSelected = darkMode ? DARK_SELECTED : LIGHT_SELECTED;

        setBackground(currentBg);
        buildUI();
    }

    private void playChime() {
        if (soundEnabled) {
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {}
        }
    }

    private void startDeviceAnimation() {
        if (pulseTimer != null) pulseTimer.stop();
        pulseTimer = new Timer(500, e -> {
            pulse = (pulse + 1) % 2;
            if (devicePulse != null) {
                devicePulse.setForeground(pulse == 0 ? CYAN : VIOLET);
            }
        });
        pulseTimer.start();
    }

    // ================= HELPER METHODS & WIDGETS =================

    private JButton neonButton(String text, boolean filled) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setForeground(filled ? new Color(8, 18, 35) : CYAN);
        button.setBackground(filled ? CYAN : currentCard2);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 14, 8, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        label.setForeground(currentMuted);
        return label;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(currentText);
        return label;
    }

    private String truncatePath(String path, int maxLen) {
        if (path == null) return "";
        if (path.length() <= maxLen) return path;
        return "..." + path.substring(path.length() - (maxLen - 3));
    }

    // ================= NEON PANEL =================

    private static class NeonPanel extends JPanel {
        private final Color fill;
        private final int radius;
        private final Color glow;

        NeonPanel(Color fill, int radius, Color glow) {
            this.fill = fill;
            this.radius = radius;
            this.glow = glow;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 90));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ================= QR PLACEHOLDER =================

    private static class QRPlaceholder extends JPanel {
        QRPlaceholder() {
            setOpaque(false);
            setPreferredSize(new Dimension(104, 104));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cell = 7;
            int startX = 12;
            int startY = 10;

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(new Color(12, 22, 43));

            boolean[][] pattern = {
                    { true, true, true, true, true, false, true, false, true, true, true },
                    { true, false, false, false, true, false, false, true, false, false, true },
                    { true, false, true, false, true, true, true, false, true, false, true },
                    { true, false, false, false, true, false, false, true, false, false, true },
                    { true, true, true, true, true, false, true, false, true, true, true },
                    { false, false, true, false, false, true, false, true, false, false, false },
                    { true, false, true, true, false, false, true, true, false, true, false },
                    { false, true, false, false, true, true, false, false, true, false, true },
                    { true, true, true, true, true, false, true, false, true, true, true },
                    { true, false, false, false, true, true, false, true, false, false, true },
                    { true, false, true, false, true, false, true, false, true, false, true }
            };

            for (int r = 0; r < pattern.length; r++) {
                for (int c = 0; c < pattern[r].length; c++) {
                    if (pattern[r][c]) {
                        g2.fillRoundRect(startX + c * cell, startY + r * cell, cell - 1, cell - 1, 2, 2);
                    }
                }
            }
            g2.dispose();
        }
    }
}
