package gui;

import core.AppManager;
import core.ConfigManager;
import filesystem.FolderWatcherDaemon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * SyncDashboardPanel
 * 
 * Syncthing-Inspired Control Center for Connecta. Features:
 * - Two-column responsive architecture (Folders Section on Left, Telemetry & Remote Devices on Right).
 * - Real-time folder state calculation (Total files, sub-folders, formatted storage size).
 * - Live upload and download bandwidth meters (calculated during 8KB chunk streaming).
 * - Instantaneous online/offline peer presence tracking via TCP heartbeats.
 * - Dynamic theme toggle (Cyber Neon Dark Mode & Clean Modern Light Mode).
 * - Actions Menu (Settings, Show ID, Logs, Restart, Shut Down).
 */
public class SyncDashboardPanel extends JPanel {

    // ================= THEME COLOR PALETTES =================

    // Dark Mode (Cyber Neon Theme)
    private static final Color DARK_BG = new Color(9, 13, 28);           // #090D1C
    private static final Color DARK_CARD = new Color(17, 27, 53);        // #111B35
    private static final Color DARK_CARD2 = new Color(22, 35, 66);       // #162342
    private static final Color DARK_TEXT = new Color(235, 243, 255);     // #EBF3FF
    private static final Color DARK_MUTED = new Color(150, 168, 199);    // #96A8C7

    // Light Mode (Clean Modern Theme)
    private static final Color LIGHT_BG = new Color(239, 245, 252);      // #EFF5FC
    private static final Color LIGHT_CARD = new Color(255, 255, 255);    // #FFFFFF
    private static final Color LIGHT_CARD2 = new Color(226, 236, 248);   // #E2ECF8
    private static final Color LIGHT_TEXT = new Color(23, 35, 58);       // #17233A
    private static final Color LIGHT_MUTED = new Color(91, 108, 133);    // #5B6C85

    // Accent Colors
    private static final Color CYAN = new Color(0, 217, 255);            // #00D9FF
    private static final Color VIOLET = new Color(128, 85, 255);         // #8055FF
    private static final Color PINK = new Color(255, 60, 172);           // #FF3CAC
    private static final Color GREEN = new Color(52, 211, 153);          // #34D399 (Emerald Green)

    // Active Theme State
    private boolean darkMode = true;
    private boolean soundEnabled = true;

    private Color currentBg = DARK_BG;
    private Color currentCard = DARK_CARD;
    private Color currentCard2 = DARK_CARD2;
    private Color currentText = DARK_TEXT;
    private Color currentMuted = DARK_MUTED;

    // Telemetry & Stat Labels
    private JLabel statusPill;
    private JLabel downloadRateLabel;
    private JLabel uploadRateLabel;
    private JLabel localStateTotalLabel;
    private JLabel uptimeTimerLabel;
    private JLabel filesSentLabel;
    private JLabel filesReceivedLabel;
    private JLabel totalTransfersLabel;
    private JLabel identificationLabel;

    // Folder & Remote Device Container Panels
    private JPanel foldersContainerPanel;
    private JPanel remoteDevicesContainerPanel;
    private JButton pauseAllButton;

    // Live Transfer Bar & Logs
    private JProgressBar transferProgress;
    private JLabel progressPercentLabel;
    private JLabel progressStatusLabel;
    private JTextArea logArea;

    // Ticking Timers
    private Timer uptimeTicker;

    public SyncDashboardPanel() {
        this.darkMode = ConfigManager.getInstance().isDarkMode();
        this.soundEnabled = ConfigManager.getInstance().isSoundEnabled();
        updateThemeColors();

        setLayout(new BorderLayout());
        setBackground(currentBg);

        buildUI();
        startUptimeTicker();
        initBackendObserver();
    }

    private void updateThemeColors() {
        currentBg = darkMode ? DARK_BG : LIGHT_BG;
        currentCard = darkMode ? DARK_CARD : LIGHT_CARD;
        currentCard2 = darkMode ? DARK_CARD2 : LIGHT_CARD2;
        currentText = darkMode ? DARK_TEXT : LIGHT_TEXT;
        currentMuted = darkMode ? DARK_MUTED : LIGHT_MUTED;
    }

    private void buildUI() {
        removeAll();
        setBackground(currentBg);

        add(createTopHeaderBar(), BorderLayout.NORTH);
        add(createMainTwoColumnBody(), BorderLayout.CENTER);
        add(createBottomDock(), BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    // ================= BACKEND OBSERVER =================

    private void initBackendObserver() {
        AppManager.getInstance().addListener(new AppManager.AppEventListener() {
            @Override
            public void onServerReady(String myID, int port) {
                SwingUtilities.invokeLater(() -> refreshTelemetry());
            }

            @Override
            public void onPeerConnected(String peerIP, int peerPort, String peerID, String peerName) {
                SwingUtilities.invokeLater(() -> {
                    updateStatusPill(true, "UP TO DATE");
                    refreshRemoteDevicesList();
                    refreshTelemetry();
                    playChime();
                });
            }

            @Override
            public void onPeerDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    updateStatusPill(false, "DISCONNECTED");
                    refreshRemoteDevicesList();
                    refreshTelemetry();
                });
            }

            @Override
            public void onConnectionError(String error) {
                SwingUtilities.invokeLater(() -> {
                    updateStatusPill(false, "OFFLINE");
                    refreshRemoteDevicesList();
                });
            }

            @Override
            public void onTransferProgress(String fileName, int percent, String statusText, boolean isOutgoing) {
                SwingUtilities.invokeLater(() -> {
                    updateStatusPill(true, "SYNCING (" + percent + "%)");
                    if (transferProgress != null) transferProgress.setValue(percent);
                    if (progressPercentLabel != null) progressPercentLabel.setText(percent + "%");
                    if (progressStatusLabel != null) progressStatusLabel.setText(statusText);
                });
            }

            @Override
            public void onTransferComplete(String fileName, boolean isOutgoing) {
                SwingUtilities.invokeLater(() -> {
                    updateStatusPill(true, "UP TO DATE");
                    if (transferProgress != null) transferProgress.setValue(100);
                    if (progressPercentLabel != null) progressPercentLabel.setText("100%");
                    if (progressStatusLabel != null) progressStatusLabel.setText("Completed: " + fileName);
                    refreshFolderList();
                    refreshTelemetry();
                    playChime();
                });
            }

            @Override
            public void onLog(String fileName, String message) {
                SwingUtilities.invokeLater(() -> appendLog(fileName, message));
            }

            @Override
            public void onStatsUpdated(int sent, int received, int total) {
                SwingUtilities.invokeLater(() -> {
                    if (filesSentLabel != null) filesSentLabel.setText(String.valueOf(sent));
                    if (filesReceivedLabel != null) filesReceivedLabel.setText(String.valueOf(received));
                    if (totalTransfersLabel != null) totalTransfersLabel.setText(String.valueOf(total));
                });
            }

            @Override
            public void onRatesUpdated(String uploadRate, String downloadRate) {
                SwingUtilities.invokeLater(() -> {
                    if (uploadRateLabel != null) uploadRateLabel.setText(uploadRate);
                    if (downloadRateLabel != null) downloadRateLabel.setText(downloadRate);
                });
            }

            @Override
            public void onFolderStatsUpdated(FolderWatcherDaemon.FolderStats stats) {
                SwingUtilities.invokeLater(() -> {
                    if (localStateTotalLabel != null) {
                        localStateTotalLabel.setText(stats.getSummary());
                    }
                    refreshFolderList();
                });
            }

            @Override
            public void onSharedFolderChanged(String newPath) {
                SwingUtilities.invokeLater(() -> {
                    refreshFolderList();
                    refreshTelemetry();
                });
            }

            @Override
            public void onSyncStatusChanged(String statusText) {
                SwingUtilities.invokeLater(() -> {
                    boolean online = AppManager.getInstance().isConnected();
                    updateStatusPill(online, statusText.toUpperCase());
                });
            }

            @Override
            public void onPairedDevicesChanged() {
                SwingUtilities.invokeLater(() -> refreshRemoteDevicesList());
            }
        });
    }

    // ================= TOP HEADER BAR =================

    private JPanel createTopHeaderBar() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 24, 12, 24));

        // Brand & Logo
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);

        JLabel logo = new JLabel("∞ CONNECTA");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        logo.setForeground(CYAN);

        JLabel tagline = new JLabel("PEER-TO-PEER LAN SYNCHRONIZATION");
        tagline.setFont(new Font("Segoe UI", Font.BOLD, 10));
        tagline.setForeground(currentMuted);

        brand.add(logo);
        brand.add(tagline);

        // Right Controls: Status Pill + Theme + Sound + Actions Menu
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        boolean isConn = AppManager.getInstance().isConnected();
        statusPill = new JLabel(isConn ? "  ● UP TO DATE  " : "  ● OFFLINE  ");
        statusPill.setFont(new Font("Segoe UI", Font.BOLD, 10));
        statusPill.setOpaque(true);
        statusPill.setBackground(isConn ? new Color(15, 47, 40) : new Color(47, 18, 30));
        statusPill.setForeground(isConn ? GREEN : PINK);
        statusPill.setBorder(new EmptyBorder(6, 10, 6, 10));

        JButton themeButton = neonHeaderButton(darkMode ? "☼ Light Mode" : "☾ Dark Mode");
        themeButton.addActionListener(e -> {
            darkMode = !darkMode;
            ConfigManager.getInstance().setDarkMode(darkMode);
            updateThemeColors();
            buildUI();
            playChime();
        });

        JButton soundButton = neonHeaderButton(soundEnabled ? "♫ Sound ON" : "♫ Sound OFF");
        soundButton.addActionListener(e -> {
            soundEnabled = !soundEnabled;
            ConfigManager.getInstance().setSoundEnabled(soundEnabled);
            soundButton.setText(soundEnabled ? "♫ Sound ON" : "♫ Sound OFF");
            playChime();
        });

        JButton actionsButton = neonHeaderButton("Actions  ▾");
        actionsButton.setBackground(new Color(91, 75, 210));
        actionsButton.setForeground(Color.WHITE);
        actionsButton.addActionListener(e -> showActionsDropdown(actionsButton));

        right.add(statusPill);
        right.add(themeButton);
        right.add(soundButton);
        right.add(actionsButton);

        header.add(brand, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    private void updateStatusPill(boolean isOnline, String text) {
        if (statusPill == null) return;
        statusPill.setText("  ● " + text + "  ");
        if (isOnline) {
            statusPill.setBackground(new Color(15, 47, 40));
            statusPill.setForeground(GREEN);
        } else {
            statusPill.setBackground(new Color(47, 18, 30));
            statusPill.setForeground(PINK);
        }
    }

    private void showActionsDropdown(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(currentCard);
        menu.setBorder(BorderFactory.createLineBorder(CYAN, 1));

        JMenuItem settingsItem = createMenuItem("⚙  Settings");
        settingsItem.addActionListener(e -> showSettingsDialog());

        JMenuItem showIdItem = createMenuItem("▤  Show Connecta ID");
        showIdItem.addActionListener(e -> showIdDialog());

        JMenuItem logsItem = createMenuItem("◷  Full Transfer Logs");
        logsItem.addActionListener(e -> showLogsDialog());

        JMenuItem restartItem = createMenuItem("↺  Restart Sync Engine");
        restartItem.addActionListener(e -> {
            AppManager.getInstance().rescanAll();
            JOptionPane.showMessageDialog(this, "Connecta Sync Engine refreshed.", "Restarted", JOptionPane.INFORMATION_MESSAGE);
        });

        JMenuItem shutdownItem = createMenuItem("✕  Shut Down");
        shutdownItem.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(this, "Are you sure you want to shut down Connecta?", "Shut Down", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                AppManager.getInstance().shutdown();
                System.exit(0);
            }
        });

        menu.add(settingsItem);
        menu.add(showIdItem);
        menu.add(logsItem);
        menu.addSeparator();
        menu.add(restartItem);
        menu.add(shutdownItem);

        menu.show(invoker, 0, invoker.getHeight());
    }

    private JMenuItem createMenuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(new Font("Segoe UI", Font.BOLD, 12));
        item.setForeground(currentText);
        item.setBackground(currentCard);
        item.setBorder(new EmptyBorder(8, 14, 8, 14));
        item.setOpaque(true);
        return item;
    }

    // ================= TWO-COLUMN MAIN BODY =================

    private JPanel createMainTwoColumnBody() {
        JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(6, 24, 12, 24));

        body.add(createFoldersLeftColumn());
        body.add(createTelemetryRightColumn());

        return body;
    }

    // ================= LEFT COLUMN: FOLDERS SECTION =================

    private JPanel createFoldersLeftColumn() {
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        // Header Row
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel title = new JLabel("Folders");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(currentText);

        headerRow.add(title, BorderLayout.WEST);
        left.add(headerRow);
        left.add(Box.createVerticalStrut(10));

        // Folders List Container
        foldersContainerPanel = new JPanel();
        foldersContainerPanel.setOpaque(false);
        foldersContainerPanel.setLayout(new BoxLayout(foldersContainerPanel, BoxLayout.Y_AXIS));

        refreshFolderList();

        JScrollPane scroll = new JScrollPane(foldersContainerPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(14);

        left.add(scroll);
        left.add(Box.createVerticalStrut(12));

        // Folder Controls Button Row
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controls.setOpaque(false);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        pauseAllButton = neonButton(AppManager.getInstance().isPaused() ? "▶ Resume All" : "⏸ Pause All", false);
        pauseAllButton.addActionListener(e -> {
            AppManager.getInstance().togglePauseAll();
            pauseAllButton.setText(AppManager.getInstance().isPaused() ? "▶ Resume All" : "⏸ Pause All");
            playChime();
        });

        JButton rescanAllBtn = neonButton("🔄 Rescan All", false);
        rescanAllBtn.addActionListener(e -> {
            AppManager.getInstance().rescanAll();
            playChime();
        });

        JButton addFolderBtn = neonButton("➕ Add Folder", true);
        addFolderBtn.addActionListener(e -> showAddFolderDialog());

        controls.add(pauseAllButton);
        controls.add(rescanAllBtn);
        controls.add(addFolderBtn);

        left.add(controls);

        return left;
    }

    private void refreshFolderList() {
        if (foldersContainerPanel == null) return;
        foldersContainerPanel.removeAll();

        List<ConfigManager.FolderConfig> folders = ConfigManager.getInstance().getSharedFolders();
        for (ConfigManager.FolderConfig fc : folders) {
            foldersContainerPanel.add(createFolderCard(fc));
            foldersContainerPanel.add(Box.createVerticalStrut(10));
        }

        foldersContainerPanel.revalidate();
        foldersContainerPanel.repaint();
    }

    private JPanel createFolderCard(ConfigManager.FolderConfig fc) {
        NeonCard card = new NeonCard(currentCard, 16, CYAN);
        card.setLayout(new BorderLayout(12, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        // Top line: Label + Status
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel nameLabel = new JLabel("📁  " + fc.getLabel());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        nameLabel.setForeground(currentText);

        boolean paused = fc.isPaused();
        JLabel statusLabel = new JLabel(paused ? "● Paused" : "● Up to Date");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        statusLabel.setForeground(paused ? PINK : GREEN);

        top.add(nameLabel, BorderLayout.WEST);
        top.add(statusLabel, BorderLayout.EAST);

        // Center line: Path & Summary
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel pathLabel = new JLabel(truncateText(fc.getPath(), 48));
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        pathLabel.setForeground(currentMuted);
        pathLabel.setToolTipText(fc.getPath());

        FolderWatcherDaemon.FolderStats stats = AppManager.getInstance().getCurrentFolderStats();
        JLabel statsLabel = new JLabel("Local State: " + stats.getSummary());
        statsLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        statsLabel.setForeground(CYAN);

        center.add(pathLabel);
        center.add(Box.createVerticalStrut(6));
        center.add(statsLabel);

        // Bottom Actions: Open Folder, Rescan
        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomActions.setOpaque(false);

        JButton openBtn = miniButton("Open Folder");
        openBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(fc.getPath()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not open folder: " + ex.getMessage());
            }
        });

        JButton rescanBtn = miniButton("Rescan");
        rescanBtn.addActionListener(e -> {
            AppManager.getInstance().rescanAll();
            playChime();
        });

        bottomActions.add(openBtn);
        bottomActions.add(rescanBtn);

        card.add(top, BorderLayout.NORTH);
        card.add(center, BorderLayout.CENTER);
        card.add(bottomActions, BorderLayout.SOUTH);

        return card;
    }

    // ================= RIGHT COLUMN: TELEMETRY & REMOTE DEVICES =================

    private JPanel createTelemetryRightColumn() {
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        // Card 1: This Device Telemetry
        right.add(createThisDeviceCard());
        right.add(Box.createVerticalStrut(14));

        // Card 2: Remote Devices List
        right.add(createRemoteDevicesSection());

        return right;
    }

    private JPanel createThisDeviceCard() {
        NeonCard card = new NeonCard(currentCard, 18, VIOLET);
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(new EmptyBorder(14, 18, 14, 18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        // Title
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        String host = ConfigManager.getInstance().getLocalDeviceName();
        JLabel title = new JLabel("This Device (" + host + ")");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(currentText);

        JLabel version = new JLabel("Connecta v2.0 (DSA Edition)");
        version.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        version.setForeground(currentMuted);

        top.add(title, BorderLayout.WEST);
        top.add(version, BorderLayout.EAST);

        // Telemetry Grid
        JPanel grid = new JPanel(new GridLayout(5, 2, 8, 4));
        grid.setOpaque(false);

        grid.add(telemetryLabel("Download Rate"));
        downloadRateLabel = telemetryValue("0 B/s", CYAN);
        grid.add(downloadRateLabel);

        grid.add(telemetryLabel("Upload Rate"));
        uploadRateLabel = telemetryValue("0 B/s", VIOLET);
        grid.add(uploadRateLabel);

        grid.add(telemetryLabel("Local State (Total)"));
        localStateTotalLabel = telemetryValue(AppManager.getInstance().getCurrentFolderStats().getSummary(), currentText);
        grid.add(localStateTotalLabel);

        grid.add(telemetryLabel("Uptime"));
        uptimeTimerLabel = telemetryValue(AppManager.getInstance().getFormattedUptime(), GREEN);
        grid.add(uptimeTimerLabel);

        grid.add(telemetryLabel("Identification"));
        JPanel idPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        idPanel.setOpaque(false);
        identificationLabel = new JLabel(AppManager.getInstance().getMyConnectaID());
        identificationLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        identificationLabel.setForeground(CYAN);
        JButton copyBtn = miniButton("Copy");
        copyBtn.addActionListener(e -> copyConnectaID());
        idPanel.add(identificationLabel);
        idPanel.add(copyBtn);
        grid.add(idPanel);

        card.add(top, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);

        return card;
    }

    private JPanel createRemoteDevicesSection() {
        NeonCard card = new NeonCard(currentCard, 18, PINK);
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(new EmptyBorder(14, 18, 14, 18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel title = new JLabel("Remote Devices");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(currentText);

        JButton pairBtn = miniButton("➕ Pair New Device");
        pairBtn.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window instanceof ConnectaMainWindow) {
                ((ConnectaMainWindow) window).showConnectionScreen();
            }
        });

        top.add(title, BorderLayout.WEST);
        top.add(pairBtn, BorderLayout.EAST);

        remoteDevicesContainerPanel = new JPanel();
        remoteDevicesContainerPanel.setOpaque(false);
        remoteDevicesContainerPanel.setLayout(new BoxLayout(remoteDevicesContainerPanel, BoxLayout.Y_AXIS));

        refreshRemoteDevicesList();

        JScrollPane scroll = new JScrollPane(remoteDevicesContainerPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        card.add(top, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    private void refreshRemoteDevicesList() {
        if (remoteDevicesContainerPanel == null) return;
        remoteDevicesContainerPanel.removeAll();

        List<ConfigManager.PairedDevice> devices = ConfigManager.getInstance().getPairedDevices();
        boolean hasConnected = false;

        for (ConfigManager.PairedDevice dev : devices) {
            boolean isOnline = dev.isOnline() || (AppManager.getInstance().isConnected() && 
                    dev.getDeviceId().equalsIgnoreCase(AppManager.getInstance().getConnectedPeerID()));
            if (isOnline) hasConnected = true;

            remoteDevicesContainerPanel.add(createRemoteDeviceRow(dev, isOnline));
            remoteDevicesContainerPanel.add(Box.createVerticalStrut(6));
        }

        if (devices.isEmpty()) {
            JLabel emptyLabel = new JLabel("No paired remote devices yet. Click 'Pair New Device' to connect.");
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            emptyLabel.setForeground(currentMuted);
            remoteDevicesContainerPanel.add(emptyLabel);
        }

        remoteDevicesContainerPanel.revalidate();
        remoteDevicesContainerPanel.repaint();
    }

    private JPanel createRemoteDeviceRow(ConfigManager.PairedDevice dev, boolean isOnline) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(currentCard2);
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(dev.getDeviceName() + " (" + dev.getDeviceId() + ")");
        name.setFont(new Font("Segoe UI", Font.BOLD, 12));
        name.setForeground(currentText);

        String ipInfo = dev.getLastKnownIp().isEmpty() ? "LAN IP Unknown" : dev.getLastKnownIp() + ":" + dev.getPort();
        JLabel addr = new JLabel(ipInfo);
        addr.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        addr.setForeground(currentMuted);

        left.add(name);
        left.add(addr);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JLabel statusBadge = new JLabel(isOnline ? "● Connected / Online" : "● Disconnected / Offline");
        statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        statusBadge.setForeground(isOnline ? GREEN : PINK);

        JButton removeBtn = miniDangerButton("✕ Remove");
        removeBtn.setToolTipText("Remove and unpair device from saved configuration");
        removeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to remove and unpair device:\n'" + dev.getDeviceName() + "' (" + dev.getDeviceId() + ")?",
                "Remove Remote Device",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                AppManager.getInstance().removePairedDevice(dev.getDeviceId());
                playChime();
            }
        });

        right.add(statusBadge);
        right.add(removeBtn);

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);

        return row;
    }

    // ================= BOTTOM DOCK: TRANSFER PROGRESS & LOGS =================

    private JPanel createBottomDock() {
        JPanel dock = new JPanel(new BorderLayout(14, 0));
        dock.setOpaque(false);
        dock.setBorder(new EmptyBorder(8, 24, 16, 24));
        dock.setPreferredSize(new Dimension(0, 160));

        // Left half: Transfer Progress & Buttons
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(500, 0));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("Live Transfer Streaming (8KB Chunks)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(currentText);

        progressPercentLabel = new JLabel("0%");
        progressPercentLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        progressPercentLabel.setForeground(CYAN);

        top.add(title, BorderLayout.WEST);
        top.add(progressPercentLabel, BorderLayout.EAST);

        transferProgress = new JProgressBar(0, 100);
        transferProgress.setValue(0);
        transferProgress.setBorderPainted(false);
        transferProgress.setStringPainted(false);
        transferProgress.setBackground(currentCard2);
        transferProgress.setForeground(CYAN);
        transferProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));

        progressStatusLabel = new JLabel("All files synchronized");
        progressStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressStatusLabel.setForeground(currentMuted);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);

        JButton sendBtn = neonButton("↑ Choose & Send File", true);
        sendBtn.addActionListener(e -> showSendFilesDialog());

        JButton undoBtn = neonButton("↺ Undo Last Sync (LIFO Stack)", false);
        undoBtn.addActionListener(e -> executeUndoRecentTransfer());

        btnRow.add(sendBtn);
        btnRow.add(undoBtn);

        left.add(top);
        left.add(Box.createVerticalStrut(8));
        left.add(transferProgress);
        left.add(Box.createVerticalStrut(6));
        left.add(progressStatusLabel);
        left.add(Box.createVerticalStrut(10));
        left.add(btnRow);

        // Right half: Log Console
        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.setOpaque(false);

        JLabel logTitle = new JLabel("Real-Time Synchronization Activity");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logTitle.setForeground(currentText);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logArea.setForeground(currentMuted);
        logArea.setBackground(currentCard2);
        logArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        logArea.setText("● Connecta v2.0 initialized. Awaiting peer connection...\n");

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(null);
        logScroll.getViewport().setBackground(currentCard2);

        right.add(logTitle, BorderLayout.NORTH);
        right.add(logScroll, BorderLayout.CENTER);

        dock.add(left, BorderLayout.WEST);
        dock.add(right, BorderLayout.CENTER);

        return dock;
    }

    // ================= ACTIONS & DIALOGS =================

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
            appendLog(selected.getName(), "Manually queued for transfer");
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

    private void showAddFolderDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Directory to Add for Synchronization");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            AppManager.getInstance().setSharedFolderPath(selected.getAbsolutePath());
            refreshFolderList();
            playChime();
        }
    }

    private void showSettingsDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        JTextField nameField = new JTextField(ConfigManager.getInstance().getLocalDeviceName());
        JTextField pathField = new JTextField(AppManager.getInstance().getSharedFolderPath());

        panel.add(new JLabel("Device Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Primary Shared Folder:"));
        panel.add(pathField);

        int res = JOptionPane.showConfirmDialog(this, panel, "Connecta Settings", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            ConfigManager.getInstance().setLocalDeviceName(nameField.getText());
            AppManager.getInstance().setSharedFolderPath(pathField.getText());
            refreshTelemetry();
            refreshFolderList();
        }
    }

    private void showIdDialog() {
        String id = AppManager.getInstance().getMyConnectaID();
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JLabel label = new JLabel("<html><center>Your Connecta ID is:<br><font color='#00D9FF' size='+2'><b>" + id + "</b></font><br>Share this ID with your partner device on LAN.</center></html>", SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);

        JButton copy = new JButton("Copy to Clipboard");
        copy.addActionListener(e -> {
            copyConnectaID();
            JOptionPane.showMessageDialog(this, "Copied ID to clipboard!");
        });
        panel.add(copy, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, panel, "Connecta ID", JOptionPane.PLAIN_MESSAGE);
    }

    private void showLogsDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Connecta Activity Logs", true);
        dlg.setSize(600, 400);
        dlg.setLocationRelativeTo(this);

        JTextArea area = new JTextArea(logArea.getText());
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        dlg.add(new JScrollPane(area));
        dlg.setVisible(true);
    }

    private void copyConnectaID() {
        String id = AppManager.getInstance().getMyConnectaID();
        if (id != null && !id.isEmpty()) {
            StringSelection selection = new StringSelection(id);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    private void refreshTelemetry() {
        if (localStateTotalLabel != null) {
            localStateTotalLabel.setText(AppManager.getInstance().getCurrentFolderStats().getSummary());
        }
        if (uploadRateLabel != null) {
            uploadRateLabel.setText(AppManager.getInstance().getCurrentUploadRate());
        }
        if (downloadRateLabel != null) {
            downloadRateLabel.setText(AppManager.getInstance().getCurrentDownloadRate());
        }
        if (identificationLabel != null) {
            identificationLabel.setText(AppManager.getInstance().getMyConnectaID());
        }
    }

    private void appendLog(String fileName, String message) {
        if (logArea == null) return;
        String time = new SimpleDateFormat("hh:mm:ss a").format(new Date());
        logArea.append("• " + time + " | " + fileName + " — " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void startUptimeTicker() {
        uptimeTicker = new Timer(1000, e -> {
            if (uptimeTimerLabel != null) {
                uptimeTimerLabel.setText(AppManager.getInstance().getFormattedUptime());
            }
        });
        uptimeTicker.start();
    }

    private void playChime() {
        if (soundEnabled) {
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {}
        }
    }

    // ================= UI HELPERS =================

    private JButton neonButton(String text, boolean filled) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(filled ? new Color(8, 18, 35) : CYAN);
        button.setBackground(filled ? CYAN : currentCard2);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(7, 14, 7, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton neonHeaderButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setForeground(currentText);
        button.setBackground(currentCard2);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(6, 12, 6, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton miniButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 10));
        button.setForeground(CYAN);
        button.setBackground(currentCard2);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(4, 8, 4, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton miniDangerButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 10));
        button.setForeground(PINK);
        button.setBackground(currentCard2);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(4, 8, 4, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JLabel telemetryLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(currentMuted);
        return label;
    }

    private JLabel telemetryValue(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(color);
        return label;
    }

    private String truncateText(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return "..." + text.substring(text.length() - (maxLen - 3));
    }

    // ================= NEON CARD WIDGET =================

    private static class NeonCard extends JPanel {
        private final Color fill;
        private final int radius;
        private final Color glow;

        NeonCard(Color fill, int radius, Color glow) {
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

            g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 75));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
