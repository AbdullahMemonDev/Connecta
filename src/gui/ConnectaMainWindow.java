package gui;

import core.AppManager;
import core.ConfigManager;
import filesystem.FolderWatcherDaemon;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * ConnectaMainWindow
 * 
 * Top-level application window hosting the CardLayout view switcher.
 * Manages screen transitions between Splash, Welcome, Connection, and Sync Dashboard screens.
 */
public class ConnectaMainWindow extends JFrame {

    private CardLayout layoutManager;
    private JPanel cardContainer;

    private SplashPanel splashPanel;
    private WelcomePanel welcomePanel;
    private ConnectionPanel connectionPanel;
    private SyncDashboardPanel dashboardPanel;

    public ConnectaMainWindow() {
        initUI();
    }

    private void initUI() {
        setTitle("Connecta - Two Devices. One Connection.");
        setSize(1200, 780);
        setMinimumSize(new Dimension(1000, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        layoutManager = new CardLayout();
        cardContainer = new JPanel(layoutManager);

        // Instantiate view panels
        splashPanel = new SplashPanel();
        welcomePanel = new WelcomePanel();
        connectionPanel = new ConnectionPanel();
        dashboardPanel = new SyncDashboardPanel();

        cardContainer.add(splashPanel, "SPLASH");
        cardContainer.add(welcomePanel, "WELCOME");
        cardContainer.add(connectionPanel, "CONNECTION");
        cardContainer.add(dashboardPanel, "DASHBOARD");

        add(cardContainer);

        // Start application engine in background
        AppManager.getInstance().start();

        // Listen for peer connection to automatically transition to dashboard
        AppManager.getInstance().addListener(new AppManager.AppEventListener() {
            @Override
            public void onServerReady(String myID, int port) {}

            @Override
            public void onPeerConnected(String peerIP, int peerPort, String peerID, String peerName) {
                SwingUtilities.invokeLater(() -> showSyncDashboard());
            }

            @Override
            public void onPeerDisconnected(String reason) {}

            @Override
            public void onConnectionError(String error) {}

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
            public void onSharedFolderChanged(String newPath) {}

            @Override
            public void onSyncStatusChanged(String statusText) {}

            @Override
            public void onPairedDevicesChanged() {}
        });

        // Graceful cleanup on window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                AppManager.getInstance().shutdown();
            }
        });

        // Start with Splash Screen
        showSplashScreen();

        // After splash screen (2.2 seconds):
        // If already paired before, jump straight to the Dashboard (auto-reconnecting in background)
        // If fresh install with 0 paired devices, show Welcome screen
        Timer splashTimer = new Timer(2200, e -> {
            boolean hasPaired = !ConfigManager.getInstance().getPairedDevices().isEmpty();
            if (hasPaired) {
                showSyncDashboard();
            } else {
                showWelcomeScreen();
            }
        });
        splashTimer.setRepeats(false);
        splashTimer.start();
    }

    public void showSplashScreen() {
        layoutManager.show(cardContainer, "SPLASH");
    }

    public void showWelcomeScreen() {
        layoutManager.show(cardContainer, "WELCOME");
    }

    public void showConnectionScreen() {
        layoutManager.show(cardContainer, "CONNECTION");
    }

    public void showSyncDashboard() {
        layoutManager.show(cardContainer, "DASHBOARD");
    }

    public ConnectionPanel getConnectionPanel() {
        return connectionPanel;
    }

    public SyncDashboardPanel getDashboardPanel() {
        return dashboardPanel;
    }

    public static void main(String[] args) {
        // Enforce system anti-aliasing hints for smooth font rendering
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            ConnectaMainWindow window = new ConnectaMainWindow();
            window.setVisible(true);
        });
    }
}
