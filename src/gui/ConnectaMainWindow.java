
package gui;

import javax.swing.*;
import java.awt.*;

public class ConnectaMainWindow extends JFrame {

    private CardLayout layoutManager;
    private JPanel currentPanel;

    public ConnectaMainWindow() {
        initUI();
    }

    private void initUI() {

        setTitle("Connecta - Two Devices. One Connection.");
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        layoutManager = new CardLayout();
        currentPanel = new JPanel(layoutManager);

        // Add all application screens
        currentPanel.add(new SplashPanel(), "SPLASH");
        currentPanel.add(new WelcomePanel(), "WELCOME");
        currentPanel.add(new ConnectionPanel(), "CONNECTION");
        currentPanel.add(new SyncDashboardPanel(), "DASHBOARD");

        add(currentPanel);

        // Start with Splash Screen
        showSplashScreen();

        // Automatically open Welcome Screen after 2.5 seconds
        Timer splashTimer = new Timer(2500, e -> {
            showWelcomeScreen();
        });

        splashTimer.setRepeats(false);
        splashTimer.start();
    }

    public void showSplashScreen() {
        layoutManager.show(currentPanel, "SPLASH");
    }

    public void showWelcomeScreen() {
        layoutManager.show(currentPanel, "WELCOME");
    }

    public void showConnectionScreen() {
        layoutManager.show(currentPanel, "CONNECTION");
    }

    public void showSyncDashboard() {
        layoutManager.show(currentPanel, "DASHBOARD");
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            ConnectaMainWindow window = new ConnectaMainWindow();
            window.setVisible(true);
        });
    }
}
