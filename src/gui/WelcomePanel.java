
package gui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

public class WelcomePanel extends JPanel {

    private final Color cyan = new Color(0, 217, 255);
    private final Color violet = new Color(128, 85, 255);
    private final Color white = new Color(240, 245, 255);

    public WelcomePanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(9, 13, 28));

        JPanel content = new JPanel(new GridLayout(1, 2, 30, 0));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(65, 65, 65, 65));

        // LEFT SIDE
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("∞  CONNECTA");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 25));
        logo.setForeground(cyan);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = new JLabel(
                "<html>Welcome to<br><font color='#00D9FF'>Connecta.</font></html>"
        );
        heading.setFont(new Font("Segoe UI", Font.BOLD, 43));
        heading.setForeground(white);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel(
                "<html><div style='width:300px;'>"
                + "Connect your devices and experience "
                + "a smarter way to share and synchronize files."
                + "</div></html>"
        );
        description.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        description.setForeground(new Color(170, 185, 210));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton startButton = new JButton("GET STARTED   →");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        startButton.setForeground(Color.WHITE);
        startButton.setBackground(new Color(95, 75, 220));
        startButton.setFocusPainted(false);
        startButton.setBorderPainted(false);
        startButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        startButton.setPreferredSize(new Dimension(210, 50));
        startButton.setMaximumSize(new Dimension(210, 50));
        startButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        startButton.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);

            if (window instanceof ConnectaMainWindow) {
                ((ConnectaMainWindow) window).showConnectionScreen();
            }
        });

        JLabel tagline = new JLabel("TWO DEVICES. ONE CONNECTION.");
        tagline.setFont(new Font("Segoe UI", Font.BOLD, 11));
        tagline.setForeground(cyan);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(Box.createVerticalGlue());
        left.add(logo);
        left.add(Box.createVerticalStrut(45));
        left.add(heading);
        left.add(Box.createVerticalStrut(22));
        left.add(description);
        left.add(Box.createVerticalStrut(38));
        left.add(startButton);
        left.add(Box.createVerticalStrut(25));
        left.add(tagline);
        left.add(Box.createVerticalGlue());

        // RIGHT SIDE - DEVICE ILLUSTRATION
        JPanel illustration = new DeviceIllustration();
        illustration.setOpaque(false);

        content.add(left);
        content.add(illustration);

        add(content, BorderLayout.CENTER);
    }

    private class DeviceIllustration extends JPanel {

        DeviceIllustration() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int w = getWidth();
            int h = getHeight();

            // Glass background
            g2.setColor(new Color(20, 31, 60, 170));
            g2.fill(new RoundRectangle2D.Float(
                    10, 25, w - 20, h - 50, 35, 35
            ));

            g2.setColor(new Color(0, 217, 255, 100));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Float(
                    10, 25, w - 20, h - 50, 35, 35
            ));

            int centerX = w / 2;
            int centerY = h / 2;

            // Connection beam
            g2.setColor(new Color(128, 85, 255, 90));
            g2.setStroke(new BasicStroke(5f));
            g2.drawLine(centerX - 90, centerY,
                    centerX + 90, centerY);

            // Laptop screen
            g2.setColor(new Color(0, 217, 255));
            g2.fillRoundRect(
                    centerX - 125, centerY - 95,
                    145, 105, 12, 12
            );

            g2.setColor(new Color(12, 20, 42));
            g2.fillRoundRect(
                    centerX - 118, centerY - 88,
                    131, 91, 7, 7
            );

            g2.setColor(cyan);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 30));
            g2.drawString("∞", centerX - 73, centerY - 30);

            // Laptop base
            g2.setColor(new Color(140, 155, 190));
            g2.fillRoundRect(
                    centerX - 140, centerY + 12,
                    175, 10, 6, 6
            );

            // Smartphone
            g2.setColor(violet);
            g2.fillRoundRect(
                    centerX + 65, centerY - 105,
                    78, 145, 15, 15
            );

            g2.setColor(new Color(12, 20, 42));
            g2.fillRoundRect(
                    centerX + 71, centerY - 98,
                    66, 131, 10, 10
            );

            g2.setColor(cyan);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 23));
            g2.drawString("∞", centerX + 91, centerY - 22);

            // Connection symbol
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
            g2.drawString("✦", centerX + 22, centerY - 20);

            // Labels
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(cyan);
            g2.drawString("LAPTOP", centerX - 100, centerY + 55);

            g2.setColor(new Color(180, 150, 255));
            g2.drawString("MOBILE", centerX + 77, centerY + 65);

            g2.dispose();
        }
    }
}
