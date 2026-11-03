
package gui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class ConnectionPanel extends JPanel {

    private final Color CYAN = new Color(0, 217, 255);
    private final Color VIOLET = new Color(128, 85, 255);
    private final Color BG = new Color(9, 13, 28);
    private final Color CARD = new Color(19, 29, 53);
    private final Color WHITE = new Color(240, 245, 255);
    private final Color MUTED = new Color(155, 171, 199);

    private JLabel myIdLabel;
    private JLabel statusLabel;
    private JTextField partnerField;
    private JButton copyButton;

    private String myID = "Not generated";

    public ConnectionPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);

        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);
    }

    // HEADER
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

    // MAIN BODY
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
        c.weightx = 0.9;
        c.insets = new Insets(0, 20, 0, 0);
        body.add(createPairingPanel(), c);

        return body;
    }

    // LEFT SIDE
    private JPanel createIllustrationPanel() {

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JLabel eyebrow = new JLabel("  THE FUTURE OF CONNECTIVITY");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 11));
        eyebrow.setForeground(CYAN);

        JLabel heading = new JLabel(
                "<html>Everything<br>connected. "
                + "<font color='#00D9FF'>Effortlessly.</font></html>"
        );
        heading.setFont(new Font("Segoe UI", Font.BOLD, 37));
        heading.setForeground(WHITE);

        JLabel description = new JLabel(
                "<html><div style='width:340px;'>"
                + "Bring your devices together in one simple space. "
                + "Connect with ease and enjoy a smarter experience."
                + "</div></html>"
        );
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
                "  ✦  SIMPLE       ✦  SMART       ✦  SEAMLESS"
        );
        feature.setFont(new Font("Segoe UI", Font.BOLD, 11));
        feature.setForeground(CYAN);

        panel.add(text, BorderLayout.NORTH);
        panel.add(illustration, BorderLayout.CENTER);
        panel.add(feature, BorderLayout.SOUTH);

        return panel;
    }

    // RIGHT SIDE: PAIRING CARD
    private JPanel createPairingPanel() {

        RoundedPanel card = new RoundedPanel(CARD, 28);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(27, 27, 25, 27));

        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(0, 217, 255, 65), 1
                ),
                new EmptyBorder(26, 27, 25, 27)
        ));

        JLabel badge = new JLabel("  ●  QUICK PAIRING");
        badge.setOpaque(true);
        badge.setBackground(new Color(19, 56, 76));
        badge.setForeground(CYAN);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setBorder(new EmptyBorder(8, 10, 8, 10));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = new JLabel("Let's connect!");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 27));
        heading.setForeground(WHITE);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Your connection starts here.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(badge);
        card.add(Box.createVerticalStrut(17));
        card.add(heading);
        card.add(Box.createVerticalStrut(6));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(25));

        card.add(sectionLabel("YOUR CONNECTA ID"));
        card.add(Box.createVerticalStrut(9));

        RoundedPanel idBox = new RoundedPanel(
                new Color(12, 23, 43), 15
        );
        idBox.setLayout(new BorderLayout(8, 0));
        idBox.setBorder(new EmptyBorder(14, 13, 14, 10));
        idBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        idBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        myIdLabel = new JLabel(myID);
        myIdLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        myIdLabel.setForeground(CYAN);

        copyButton = new JButton("COPY");
        styleCopyButton(copyButton);
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyMyID());

        idBox.add(myIdLabel, BorderLayout.CENTER);
        idBox.add(copyButton, BorderLayout.EAST);

        card.add(idBox);
        card.add(Box.createVerticalStrut(23));

        card.add(sectionLabel("PARTNER'S CONNECTA ID"));
        card.add(Box.createVerticalStrut(9));

        partnerField = new JTextField();
        partnerField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        partnerField.setForeground(WHITE);
        partnerField.setCaretColor(CYAN);
        partnerField.setBackground(new Color(12, 23, 43));
        partnerField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(65, 91, 128), 1
                ),
                new EmptyBorder(13, 13, 13, 13)
        ));
        partnerField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 49));
        partnerField.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(partnerField);
        card.add(Box.createVerticalStrut(15));

        JButton connect = new JButton("Connect Device   →");
        connect.setFont(new Font("Segoe UI", Font.BOLD, 14));
        connect.setForeground(Color.WHITE);
        connect.setBackground(new Color(91, 75, 210));
        connect.setFocusPainted(false);
        connect.setBorderPainted(false);
        connect.setCursor(new Cursor(Cursor.HAND_CURSOR));
        connect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 49));
        connect.setAlignmentX(Component.LEFT_ALIGNMENT);

        connect.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                connect.setBackground(new Color(113, 87, 245));
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                connect.setBackground(new Color(91, 75, 210));
            }
        });

        connect.addActionListener(e -> onConnectButtonClicked());

        card.add(connect);
        card.add(Box.createVerticalStrut(20));

        JPanel qrInfo = new JPanel(new BorderLayout(12, 0));
        qrInfo.setOpaque(false);
        qrInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel qrSymbol = new JLabel("▦");
        qrSymbol.setFont(new Font("Segoe UI", Font.PLAIN, 35));
        qrSymbol.setForeground(CYAN);

        JPanel qrText = new JPanel();
        qrText.setOpaque(false);
        qrText.setLayout(new BoxLayout(qrText, BoxLayout.Y_AXIS));

        JLabel qrHeading = new JLabel("QR Pairing");
        qrHeading.setFont(new Font("Segoe UI", Font.BOLD, 13));
        qrHeading.setForeground(WHITE);

        JLabel qrDescription = new JLabel(
                "QR scanning integration coming later"
        );
        qrDescription.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        qrDescription.setForeground(MUTED);

        qrText.add(qrHeading);
        qrText.add(Box.createVerticalStrut(5));
        qrText.add(qrDescription);

        qrInfo.add(qrSymbol, BorderLayout.WEST);
        qrInfo.add(qrText, BorderLayout.CENTER);

        card.add(qrInfo);
        card.add(Box.createVerticalStrut(15));

        statusLabel = new JLabel("●  Waiting for connection");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(statusLabel);

        return card;
    }

    // LABEL STYLE
    private JLabel sectionLabel(String value) {

        JLabel label = new JLabel(value);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        return label;
    }

    private void styleCopyButton(JButton button) {

        button.setFont(new Font("Segoe UI", Font.BOLD, 10));
        button.setForeground(CYAN);
        button.setBackground(new Color(22, 42, 66));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(
                8, 10, 8, 10
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    // COPY ID
    private void copyMyID() {

        if (myID != null && !myID.equals("Not generated")) {

            StringSelection selection = new StringSelection(myID);

            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(selection, null);

            statusLabel.setText("●  ID copied successfully");
            statusLabel.setForeground(CYAN);
        }
    }

    // EXISTING GUI INTEGRATION METHODS
    public void displayGeneratedID(String id) {

        myID = id;
        myIdLabel.setText(id);
        copyButton.setEnabled(id != null && !id.isEmpty());
    }

    public String getInputID() {

        return partnerField.getText().trim();
    }

    public void onConnectButtonClicked() {

        String partnerID = getInputID();

        if (partnerID.isEmpty()) {
            statusLabel.setText("●  Please enter partner ID");
            statusLabel.setForeground(new Color(255, 80, 120));
            return;
        }

        statusLabel.setText("●  Connection request ready");
        statusLabel.setForeground(CYAN);

        JOptionPane.showMessageDialog(
                this,
                "Partner ID entered successfully.\n"
                + "Actual connection will be integrated by the backend team.",
                "Connecta",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // CUSTOM DEVICE ILLUSTRATION
    private class DeviceIllustration extends JPanel {

        DeviceIllustration() {
            setOpaque(false);
            setPreferredSize(new Dimension(420, 340));
        }

        @Override
        protected void paintComponent(Graphics g) {

            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            // Neon background glow
            g2.setColor(new Color(0, 217, 255, 22));
            g2.fillOval(cx - 170, cy - 145, 340, 290);

            g2.setColor(new Color(128, 85, 255, 30));
            g2.fillOval(cx - 130, cy - 115, 260, 230);

            // Decorative rings
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(0, 217, 255, 65));
            g2.drawOval(cx - 130, cy - 120, 260, 250);

            g2.setColor(new Color(128, 85, 255, 75));
            g2.drawOval(cx - 150, cy - 140, 300, 290);

            // Connection beam
            g2.setStroke(new BasicStroke(
                    4f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));

            g2.setColor(new Color(0, 217, 255, 130));
            g2.drawLine(cx - 48, cy + 5, cx + 62, cy + 5);

            // Beam nodes
            g2.setColor(CYAN);
            g2.fillOval(cx - 55, cy - 2, 14, 14);
            g2.fillOval(cx + 55, cy - 2, 14, 14);

            // Laptop shadow
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillRoundRect(cx - 145, cy + 88, 170, 17, 10, 10);

            // Laptop outer screen
            GradientPaint laptopGradient = new GradientPaint(
                    cx - 135, cy - 80, CYAN,
                    cx + 15, cy + 80, VIOLET
            );

            g2.setPaint(laptopGradient);
            g2.fillRoundRect(
                    cx - 135, cy - 80, 150, 165, 15, 15
            );

            // Laptop inner screen
            g2.setColor(new Color(9, 17, 36));
            g2.fillRoundRect(
                    cx - 127, cy - 72, 134, 149, 10, 10
            );

            g2.setColor(CYAN);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 35));
            g2.drawString("∞", cx - 83, cy + 5);

            g2.setColor(WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.drawString("CONNECTA", cx - 88, cy + 27);

            // Laptop base
            g2.setColor(new Color(115, 141, 177));
            int[] lx = {
                cx - 145, cx + 25, cx + 40, cx - 130
            };
            int[] ly = {
                cy + 85, cy + 85, cy + 98, cy + 98
            };
            g2.fillPolygon(lx, ly, 4);

            // Phone shadow
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(
                    cx + 52, cy + 95, 100, 15, 12, 12
            );

            // Phone body
            GradientPaint phoneGradient = new GradientPaint(
                    cx + 50, cy - 120, VIOLET,
                    cx + 155, cy + 100, CYAN
            );

            g2.setPaint(phoneGradient);
            g2.fillRoundRect(
                    cx + 55, cy - 120, 95, 210, 22, 22
            );

            // Phone screen
            g2.setColor(new Color(10, 20, 40));
            g2.fillRoundRect(
                    cx + 63, cy - 109, 79, 187, 15, 15
            );

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
            g2.drawString("LAPTOP", cx - 105, cy + 125);

            g2.setColor(new Color(180, 150, 255));
            g2.drawString("SMARTPHONE", cx + 58, cy + 125);

            g2.dispose();
        }
    }

    // ROUNDED PANEL
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

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(panelColor);
            g2.fillRoundRect(
                    0, 0, getWidth(), getHeight(), radius, radius
            );

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
