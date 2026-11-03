package gui;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class SyncDashboardPanel extends JPanel {

    // CYBER NEON COLORS
    private static final Color BG = new Color(9, 13, 28);
    private static final Color CARD = new Color(17, 27, 53);
    private static final Color CARD2 = new Color(22, 35, 66);
    private static final Color CYAN = new Color(0, 217, 255);
    private static final Color VIOLET = new Color(128, 85, 255);
    private static final Color PINK = new Color(255, 60, 172);
    private static final Color GREEN = new Color(52, 211, 153);
    private static final Color WHITE = new Color(235, 243, 255);
    private static final Color MUTED = new Color(150, 168, 199);

    private Color background = BG;
    private Color cardColor = CARD;
    private Color cardColor2 = CARD2;
    private Color textColor = WHITE;
    private Color mutedColor = MUTED;
    private Color selectedColor = new Color(20, 58, 83);

    private JProgressBar transferProgress;
    private JTextArea logArea;
    private JLabel progressLabel;
    private JLabel progressStatus;
    private JLabel devicePulse;
    private JLabel selectedFileLabel;
    private JLabel connectionStatus;

    private Timer pulseTimer;
    private Timer demoTimer;

    private int demoProgress = 0;
    private int pulse = 0;

    private boolean darkMode = true;
    private boolean soundEnabled = true;

    private File selectedFile;

    public SyncDashboardPanel() {
        setLayout(new BorderLayout());
        setBackground(background);

        add(createSidebar(), BorderLayout.WEST);
        add(createMainArea(), BorderLayout.CENTER);

        startDeviceAnimation();
    }

    // ================= SIDEBAR =================

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(215, 0));
        sidebar.setBackground(cardColor);
        sidebar.setBorder(new EmptyBorder(25, 17, 20, 17));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("∞ CONNECTA");
        logo.setFont(new Font("SansSerif", Font.BOLD, 23));
        logo.setForeground(CYAN);

        JLabel tagline = smallLabel("YOUR DIGITAL SPACE");

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(40));

        sidebar.add(sidebarHeading("WORKSPACE"));
        sidebar.add(Box.createVerticalStrut(15));

        sidebar.add(navItem("⌂", "Overview"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navItem("⇄", "File Transfers"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navItem("▣", "My Devices"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navItem("◷", "Transfer History"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navItem("▦", "QR Pairing"));

        sidebar.add(Box.createVerticalGlue());

        JButton backButton = neonButton("←  Back to Connect", false);
        backButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        backButton.addActionListener(e -> {
            playSound();

            Window window = SwingUtilities.getWindowAncestor(this);

            if (window instanceof ConnectaMainWindow) {
                ((ConnectaMainWindow) window).showConnectionScreen();
            }
        });

        sidebar.add(backButton);
        sidebar.add(Box.createVerticalStrut(15));
        sidebar.add(smallLabel("CONNECTA  •  VERSION 1.0"));

        return sidebar;
    }

    private JLabel sidebarHeading(String text) {
        JLabel label = smallLabel(text);
        label.setForeground(CYAN);
        return label;
    }

    private JPanel navItem(String icon, String name) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        panel.setOpaque(true);
        panel.setBackground(cardColor);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        iconLabel.setForeground(CYAN);

        JLabel textLabel = new JLabel(name);
        textLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        textLabel.setForeground(mutedColor);

        panel.add(iconLabel);
        panel.add(textLabel);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                playSound();

                if (name.equals("QR Pairing")) {
                    showQrDialog();
                } else {
                    JOptionPane.showMessageDialog(
                            SyncDashboardPanel.this,
                            name + " section selected.",
                            "Connecta",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            }
        });

        return panel;
    }

    // ================= MAIN AREA =================

    private JPanel createMainArea() {
        JPanel main = new JPanel(new BorderLayout(0, 18));
        main.setOpaque(false);
        main.setBorder(new EmptyBorder(24, 27, 22, 27));

        main.add(createTopBar(), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createWelcomeBanner());
        content.add(Box.createVerticalStrut(17));

        content.add(createStatsRow());
        content.add(Box.createVerticalStrut(20));

        content.add(sectionTitle("Quick Actions"));
        content.add(Box.createVerticalStrut(10));

        content.add(createActionCards());
        content.add(Box.createVerticalStrut(19));

        content.add(createDeviceCard());
        content.add(Box.createVerticalStrut(19));

        content.add(createTransferAndQrRow());
        content.add(Box.createVerticalStrut(19));

        content.add(createHistoryAndPreviewRow());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
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

        JLabel title = new JLabel("Control Center");
        title.setFont(new Font("SansSerif", Font.BOLD, 27));
        title.setForeground(textColor);

        JLabel subtitle = new JLabel(
                "Your devices. Your files. One seamless space."
        );
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 11));
        subtitle.setForeground(mutedColor);

        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(subtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        right.setOpaque(false);

        JLabel ready = new JLabel("● READY");
        ready.setFont(new Font("SansSerif", Font.BOLD, 10));
        ready.setForeground(GREEN);

        JButton themeButton = neonButton("☼ Light Mode", false);

        themeButton.addActionListener(e -> {
            darkMode = !darkMode;
            applyTheme();

            themeButton.setText(
                    darkMode ? "☼ Light Mode" : "☾ Dark Mode"
            );

            playSound();
        });

        JButton soundButton = neonButton("♫ Sound ON", false);

        soundButton.addActionListener(e -> {
            soundEnabled = !soundEnabled;

            soundButton.setText(
                    soundEnabled ? "♫ Sound ON" : "♫ Sound OFF"
            );

            playSound();
        });

        right.add(ready);
        right.add(themeButton);
        right.add(soundButton);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    // ================= WELCOME BANNER =================

    private JPanel createWelcomeBanner() {
        NeonPanel banner = new NeonPanel(cardColor2, 22, VIOLET);

        banner.setLayout(new BorderLayout());
        banner.setBorder(new EmptyBorder(20, 23, 20, 23));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel eyebrow = smallLabel("YOUR CONNECTED WORKSPACE");
        eyebrow.setForeground(CYAN);

        JLabel heading = new JLabel("Hello, welcome back!");
        heading.setFont(new Font("SansSerif", Font.BOLD, 23));
        heading.setForeground(textColor);

        JLabel description = new JLabel(
                "Everything is in sync. What would you like to do today?"
        );
        description.setFont(new Font("SansSerif", Font.PLAIN, 11));
        description.setForeground(mutedColor);

        text.add(eyebrow);
        text.add(Box.createVerticalStrut(6));
        text.add(heading);
        text.add(Box.createVerticalStrut(5));
        text.add(description);

        JLabel infinity = new JLabel("∞");
        infinity.setFont(new Font("SansSerif", Font.BOLD, 69));
        infinity.setForeground(CYAN);

        banner.add(text, BorderLayout.WEST);
        banner.add(infinity, BorderLayout.EAST);

        return banner;
    }

    // ================= STATISTICS =================

    private JPanel createStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));

        row.add(statCard("↑", "FILES SENT", "12", CYAN));
        row.add(statCard("↓", "FILES RECEIVED", "8", VIOLET));
        row.add(statCard("✦", "TOTAL TRANSFERS", "20", PINK));

        return row;
    }

    private JPanel statCard(
            String icon,
            String title,
            String value,
            Color accent
    ) {
        NeonPanel panel = new NeonPanel(cardColor, 17, accent);
        panel.setLayout(new BorderLayout(9, 0));
        panel.setBorder(new EmptyBorder(13, 15, 13, 15));

        JLabel symbol = new JLabel(icon);
        symbol.setFont(new Font("SansSerif", Font.BOLD, 25));
        symbol.setForeground(accent);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel heading = smallLabel(title);

        JLabel number = new JLabel(value);
        number.setFont(new Font("SansSerif", Font.BOLD, 23));
        number.setForeground(textColor);

        labels.add(heading);
        labels.add(Box.createVerticalStrut(4));
        labels.add(number);

        panel.add(symbol, BorderLayout.WEST);
        panel.add(labels, BorderLayout.CENTER);

        return panel;
    }

    // ================= QUICK ACTIONS =================

    private JPanel createActionCards() {
        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 103));

        row.add(actionCard(
                "↑", "Send Files",
                "Choose and prepare a file",
                CYAN, this::showSendFilesDialog
        ));

        row.add(actionCard(
                "↓", "Receive Files",
                "Incoming transfer area",
                VIOLET,
                () -> placeholder(
                        "Receive Files",
                        "Receive functionality will be connected by the backend team."
                )
        ));

        row.add(actionCard(
                "▦", "Scan QR",
                "Pair a device quickly",
                PINK, this::showQrDialog
        ));

        return row;
    }

    private JPanel actionCard(
            String icon,
            String title,
            String description,
            Color accent,
            Runnable action
    ) {
        NeonPanel panel = new NeonPanel(cardColor, 17, accent);
        panel.setLayout(new BorderLayout(12, 0));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel symbol = new JLabel(icon);
        symbol.setFont(new Font("SansSerif", Font.BOLD, 29));
        symbol.setForeground(accent);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(title);
        name.setFont(new Font("SansSerif", Font.BOLD, 14));
        name.setForeground(textColor);

        JLabel detail = new JLabel(description);
        detail.setFont(new Font("SansSerif", Font.PLAIN, 10));
        detail.setForeground(mutedColor);

        JLabel open = new JLabel("OPEN →");
        open.setFont(new Font("SansSerif", Font.BOLD, 9));
        open.setForeground(accent);

        labels.add(name);
        labels.add(Box.createVerticalStrut(4));
        labels.add(detail);
        labels.add(Box.createVerticalStrut(5));
        labels.add(open);

        panel.add(symbol, BorderLayout.WEST);
        panel.add(labels, BorderLayout.CENTER);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                playSound();
                action.run();
            }
        });

        return panel;
    }

    // ================= DEVICE CONNECTION =================

    private JPanel createDeviceCard() {
        NeonPanel card = new NeonPanel(cardColor, 19, CYAN);
        card.setLayout(new BorderLayout(15, 0));
        card.setBorder(new EmptyBorder(16, 19, 16, 19));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        heading.add(sectionTitle("Device Connection"));
        heading.add(Box.createVerticalStrut(4));
        heading.add(smallLabel("Visual preview of your paired workspace"));

        JPanel visual = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 25, 5)
        );
        visual.setOpaque(false);

        visual.add(deviceTile("▰", "THIS LAPTOP", CYAN));

        devicePulse = new JLabel("◉ ⇄ ◉");
        devicePulse.setFont(new Font("SansSerif", Font.BOLD, 22));
        devicePulse.setForeground(CYAN);

        visual.add(devicePulse);
        visual.add(deviceTile("▯", "PARTNER DEVICE", VIOLET));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(heading, BorderLayout.NORTH);
        center.add(visual, BorderLayout.CENTER);

        connectionStatus = smallLabel(
                "● DEMO DISPLAY • Actual device status comes from backend"
        );
        connectionStatus.setForeground(GREEN);

        card.add(center, BorderLayout.CENTER);
        card.add(connectionStatus, BorderLayout.SOUTH);

        return card;
    }

    private JPanel deviceTile(String icon, String name, Color accent) {
        JPanel tile = new JPanel();
        tile.setOpaque(false);
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));

        JLabel glyph = new JLabel(icon);
        glyph.setFont(new Font("SansSerif", Font.BOLD, 34));
        glyph.setForeground(accent);
        glyph.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel label = smallLabel(name);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        tile.add(glyph);
        tile.add(label);

        return tile;
    }

    // ================= TRANSFER AND QR =================

    private JPanel createTransferAndQrRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 188));

        row.add(createProgressCard());
        row.add(createQrCard());

        return row;
    }

    private JPanel createProgressCard() {
        NeonPanel card = new NeonPanel(cardColor, 18, VIOLET);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        top.add(sectionTitle("Live Transfer"), BorderLayout.WEST);

        progressLabel = new JLabel("0%");
        progressLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        progressLabel.setForeground(CYAN);

        top.add(progressLabel, BorderLayout.EAST);

        transferProgress = new JProgressBar(0, 100);
        transferProgress.setValue(0);
        transferProgress.setBorderPainted(false);
        transferProgress.setStringPainted(false);
        transferProgress.setBackground(cardColor2);
        transferProgress.setForeground(CYAN);
        transferProgress.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 12)
        );

        progressStatus = smallLabel("No active transfers");

        JButton demoButton = neonButton("▶ Preview Animation", true);
        demoButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        demoButton.addActionListener(e -> startDemoTransfer());

        card.add(top);
        card.add(Box.createVerticalStrut(16));
        card.add(transferProgress);
        card.add(Box.createVerticalStrut(9));
        card.add(progressStatus);
        card.add(Box.createVerticalGlue());
        card.add(demoButton);

        return card;
    }

    private JPanel createQrCard() {
        NeonPanel card = new NeonPanel(cardColor, 18, PINK);
        card.setLayout(new BorderLayout(13, 0));
        card.setBorder(new EmptyBorder(16, 17, 16, 17));

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel description = new JLabel(
                "<html><div style='width:135px'>"
                + "Scan a pairing code to connect another device."
                + "</div></html>"
        );
        description.setFont(new Font("SansSerif", Font.PLAIN, 10));
        description.setForeground(mutedColor);

        JButton open = neonButton("Open QR Interface", false);
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.addActionListener(e -> showQrDialog());

        labels.add(sectionTitle("Quick Pairing"));
        labels.add(Box.createVerticalStrut(8));
        labels.add(description);
        labels.add(Box.createVerticalStrut(12));
        labels.add(open);

        card.add(labels, BorderLayout.CENTER);
        card.add(new QRPlaceholder(), BorderLayout.EAST);

        return card;
    }

    // ================= HISTORY AND FILE PREVIEW =================

    private JPanel createHistoryAndPreviewRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 225));

        row.add(createHistoryCard());
        row.add(createPreviewCard());

        return row;
    }

    private JPanel createHistoryCard() {
        NeonPanel card = new NeonPanel(cardColor, 18, CYAN);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(15, 17, 15, 17));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        logArea.setForeground(mutedColor);
        logArea.setBackground(cardColor2);
        logArea.setBorder(new EmptyBorder(10, 11, 10, 11));

        logArea.setText(
                "No real transfers recorded yet.\n"
                + "Activity will appear here."
        );

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(cardColor2);

        card.add(sectionTitle("Transfer History"), BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    private JPanel createPreviewCard() {
        NeonPanel card = new NeonPanel(cardColor, 18, VIOLET);
        card.setLayout(new BorderLayout(10, 8));
        card.setBorder(new EmptyBorder(15, 17, 15, 17));

        selectedFileLabel = new JLabel("▤  No file selected");
        selectedFileLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        selectedFileLabel.setForeground(mutedColor);

        JLabel info = smallLabel("Select a file to preview its details.");

        JButton choose = neonButton("Choose File", true);
        choose.setAlignmentX(Component.LEFT_ALIGNMENT);
        choose.addActionListener(e -> choosePreviewFile());

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(selectedFileLabel);
        body.add(Box.createVerticalStrut(9));
        body.add(info);
        body.add(Box.createVerticalGlue());
        body.add(choose);

        card.add(sectionTitle("File Preview"), BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);

        return card;
    }

    private void choosePreviewFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file for preview");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();

            double kb = selectedFile.length() / 1024.0;

            selectedFileLabel.setText(
                    selectedFile.getName() + " ("
                    + String.format("%.1f KB", kb) + ")"
            );

            selectedFileLabel.setToolTipText(
                    selectedFile.getAbsolutePath()
            );

            addLog(selectedFile.getName(), "Selected for preview");
            playSound();
        }
    }

    // ================= SEND FILES =================

    private void showSendFilesDialog() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(350, 190));

        JLabel heading = new JLabel("Prepare a file transfer");
        heading.setFont(new Font("SansSerif", Font.BOLD, 18));

        JLabel fileLabel = new JLabel(
                selectedFile == null
                        ? "No file selected"
                        : selectedFile.getName()
        );

        JButton choose = new JButton("Choose File");

        JTextField recipient = new JTextField();
        recipient.setMaximumSize(new Dimension(350, 42));
        recipient.setBorder(
                BorderFactory.createTitledBorder("Recipient Device ID")
        );

        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();

            if (chooser.showOpenDialog(panel)
                    == JFileChooser.APPROVE_OPTION) {

                selectedFile = chooser.getSelectedFile();
                fileLabel.setText(selectedFile.getName());
            }
        });

        panel.add(heading);
        panel.add(Box.createVerticalStrut(15));
        panel.add(choose);
        panel.add(Box.createVerticalStrut(8));
        panel.add(fileLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(recipient);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Connecta | Send Files",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {

            if (selectedFile == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "Please select a file first."
                );
                return;
            }

            if (recipient.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "Please enter the recipient Device ID."
                );
                return;
            }

            addLog(
                    selectedFile.getName(),
                    "Prepared • awaiting backend transfer"
            );

            placeholder(
                    "Ready for transfer",
                    "File and recipient selected. Actual transfer will be handled by the backend."
            );
        }
    }

    // ================= QR DIALOG =================

    private void showQrDialog() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setPreferredSize(new Dimension(300, 220));

        panel.add(new QRPlaceholder(), BorderLayout.CENTER);

        JLabel note = new JLabel(
                "<html><center>QR pairing preview<br>"
                + "Camera scanning and code generation<br>"
                + "will be connected by the backend team.</center></html>",
                SwingConstants.CENTER
        );

        panel.add(note, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(
                this,
                panel,
                "Connecta | QR Pairing",
                JOptionPane.PLAIN_MESSAGE
        );
    }

    private void placeholder(String title, String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // ================= DEMO ANIMATION =================

    private void startDemoTransfer() {
        if (demoTimer != null && demoTimer.isRunning()) {
            return;
        }

        demoProgress = 0;
        updateProgressBar(0);

        progressStatus.setText(
                "Demo animation • no file is being transferred"
        );

        demoTimer = new Timer(65, e -> {
            demoProgress = Math.min(100, demoProgress + 1);

            updateProgressBar(demoProgress);

            if (demoProgress >= 100) {
                ((Timer) e.getSource()).stop();

                progressStatus.setText(
                        "Demo complete • backend transfer not started"
                );

                playSound();
            }
        });

        demoTimer.start();
    }

    private void startDeviceAnimation() {
        pulseTimer = new Timer(450, e -> {
            pulse = (pulse + 1) % 2;

            if (devicePulse != null) {
                devicePulse.setForeground(
                        pulse == 0 ? CYAN : VIOLET
                );
            }
        });

        pulseTimer.start();
    }

    // ================= THEME =================

    private void applyTheme() {
        background = darkMode
                ? BG
                : new Color(239, 245, 252);

        cardColor = darkMode
                ? CARD
                : Color.WHITE;

        cardColor2 = darkMode
                ? CARD2
                : new Color(226, 236, 248);

        textColor = darkMode
                ? WHITE
                : new Color(23, 35, 58);

        mutedColor = darkMode
                ? MUTED
                : new Color(91, 108, 133);

        selectedColor = darkMode
                ? new Color(20, 58, 83)
                : new Color(218, 241, 250);

        setBackground(background);

        if (pulseTimer != null) {
            pulseTimer.stop();
        }

        removeAll();

        setLayout(new BorderLayout());
        add(createSidebar(), BorderLayout.WEST);
        add(createMainArea(), BorderLayout.CENTER);

        revalidate();
        repaint();

        startDeviceAnimation();
    }

    // ================= HELPER METHODS =================

    private void playSound() {
        if (soundEnabled) {
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {
            }
        }
    }

    private JButton neonButton(String text, boolean filled) {
        JButton button = new JButton(text);

        button.setFont(new Font("SansSerif", Font.BOLD, 10));
        button.setForeground(
                filled ? new Color(8, 18, 35) : CYAN
        );
        button.setBackground(
                filled ? CYAN : cardColor2
        );
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 12, 9, 12));
        button.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        );

        return button;
    }

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 9));
        label.setForeground(mutedColor);
        return label;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 15));
        label.setForeground(textColor);
        return label;
    }

    // ================= BACKEND INTEGRATION METHODS =================

    public void updateProgressBar(int percent) {
        int safe = Math.max(0, Math.min(100, percent));

        if (transferProgress != null) {
            transferProgress.setValue(safe);
        }

        if (progressLabel != null) {
            progressLabel.setText(safe + "%");
        }

        if (progressStatus != null) {
            if (safe == 100) {
                progressStatus.setText("Transfer completed successfully!");
            } else if (safe > 0) {
                progressStatus.setText("Your transfer is in progress...");
            } else {
                progressStatus.setText("No active transfers");
            }
        }
    }

    public void addLog(String fileName, String status) {
        if (logArea == null) {
            return;
        }

        if (logArea.getText().startsWith(
                "No real transfers recorded yet."
        )) {
            logArea.setText("");
        }

        String time = new SimpleDateFormat("hh:mm a")
                .format(new Date());

        logArea.append(
                "• " + time + " | "
                + fileName + " — "
                + status + "\n"
        );
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

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(fill);

            g2.fillRoundRect(
                    0, 0,
                    getWidth() - 1,
                    getHeight() - 1,
                    radius, radius
            );

            g2.setColor(new Color(
                    glow.getRed(),
                    glow.getGreen(),
                    glow.getBlue(),
                    95
            ));

            g2.setStroke(new BasicStroke(1.2f));

            g2.drawRoundRect(
                    0, 0,
                    getWidth() - 1,
                    getHeight() - 1,
                    radius, radius
            );

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

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int cell = 7;
            int startX = 12;
            int startY = 10;

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(
                    0, 0,
                    getWidth(), getHeight(),
                    12, 12
            );

            g2.setColor(new Color(12, 22, 43));

            // QR visual placeholder pattern
            boolean[][] pattern = {
                {true, true, true, true, true, false, true, false, true, true, true},
                {true, false, false, false, true, false, false, true, false, false, true},
                {true, false, true, false, true, true, true, false, true, false, true},
                {true, false, false, false, true, false, false, true, false, false, true},
                {true, true, true, true, true, false, true, false, true, true, true},
                {false, false, true, false, false, true, false, true, false, false, false},
                {true, false, true, true, false, false, true, true, false, true, false},
                {false, true, false, false, true, true, false, false, true, false, true},
                {true, true, true, true, true, false, true, false, true, true, true},
                {true, false, false, false, true, true, false, true, false, false, true},
                {true, false, true, false, true, false, true, false, true, false, true}
            };

            for (int r = 0; r < pattern.length; r++) {
                for (int c = 0; c < pattern[r].length; c++) {
                    if (pattern[r][c]) {
                        g2.fillRoundRect(
                                startX + c * cell,
                                startY + r * cell,
                                cell - 1,
                                cell - 1,
                                2, 2
                        );
                    }
                }
            }

            g2.dispose();
        }
    }
}