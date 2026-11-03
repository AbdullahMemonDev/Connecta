
package gui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

public class SplashPanel extends JPanel {

    private final Color cyan = new Color(0, 217, 255);
    private final Color violet = new Color(128, 85, 255);

    public SplashPanel() {
        setLayout(new GridBagLayout());
        setBackground(new Color(9, 13, 28));
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

        // Background gradient
        GradientPaint background = new GradientPaint(
                0, 0, new Color(9, 13, 28),
                w, h, new Color(22, 20, 55)
        );

        g2.setPaint(background);
        g2.fillRect(0, 0, w, h);

        // Decorative neon circles
        g2.setColor(new Color(0, 217, 255, 25));
        g2.fillOval(w / 2 - 190, h / 2 - 190, 380, 380);

        g2.setColor(new Color(128, 85, 255, 30));
        g2.fillOval(w / 2 - 145, h / 2 - 145, 290, 290);

        // Logo
        String symbol = "∞";
        String name = "CONNECTA";
        String tagline = "TWO DEVICES. ONE CONNECTION.";

        Font symbolFont = new Font("Segoe UI", Font.BOLD, 95);
        Font nameFont = new Font("Segoe UI", Font.BOLD, 38);
        Font taglineFont = new Font("Segoe UI", Font.PLAIN, 13);

        FontMetrics symbolMetrics = g2.getFontMetrics(symbolFont);
        FontMetrics nameMetrics = g2.getFontMetrics(nameFont);
        FontMetrics taglineMetrics = g2.getFontMetrics(taglineFont);

        int symbolY = h / 2 - 35;
        int nameY = h / 2 + 45;
        int taglineY = h / 2 + 85;

        // Neon symbol
        g2.setFont(symbolFont);
        g2.setColor(new Color(0, 217, 255, 65));
        g2.drawString(
                symbol,
                (w - symbolMetrics.stringWidth(symbol)) / 2,
                symbolY + 3
        );

        g2.setColor(cyan);
        g2.drawString(
                symbol,
                (w - symbolMetrics.stringWidth(symbol)) / 2,
                symbolY
        );

        // App name
        g2.setFont(nameFont);
        g2.setColor(Color.WHITE);
        g2.drawString(
                name,
                (w - nameMetrics.stringWidth(name)) / 2,
                nameY
        );

        // Tagline
        g2.setFont(taglineFont);
        g2.setColor(cyan);
        g2.drawString(
                tagline,
                (w - taglineMetrics.stringWidth(tagline)) / 2,
                taglineY
        );

        // Bottom loading line
        int lineWidth = 110;
        int lineX = (w - lineWidth) / 2;

        g2.setColor(new Color(255, 255, 255, 35));
        g2.fill(new RoundRectangle2D.Float(
                lineX, h / 2 + 125, lineWidth, 4, 4, 4
        ));

        g2.setColor(violet);
        g2.fill(new RoundRectangle2D.Float(
                lineX, h / 2 + 125, 55, 4, 4, 4
        ));

        g2.dispose();
    }
}
