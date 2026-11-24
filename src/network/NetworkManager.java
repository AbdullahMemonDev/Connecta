package network;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * NetworkManager
 * 
 * Central network controller responsible for hosting the incoming TCP server daemon
 * and initiating outbound socket connections to peer devices.
 * 
 * Binds explicitly to wildcard 0.0.0.0 with graceful port-conflict fallbacks,
 * enforces connection timeouts to protect the Swing EDT from freezing, and delivers
 * specific, actionable error messages for diagnostic feedback.
 */
public class NetworkManager {

    public static final int DEFAULT_PORT = 8080;
    public static final int MAX_PORT_ATTEMPTS = 5;
    public static final int CONNECT_TIMEOUT_MS = 4000;

    private ServerSocket serverSocket;
    private Socket activeSocket;
    private SocketStreamer streamer;
    private volatile boolean isConnected = false;
    private int listeningPort = DEFAULT_PORT;

    private NetworkListener listener;

    /**
     * Interface for dispatching network lifecycle events to the application orchestrator.
     */
    public interface NetworkListener {
        void onServerStarted(int port, String myConnectaID);
        void onPeerConnected(String peerIP, int peerPort, SocketStreamer streamer);
        void onPeerDisconnected(String reason);
        void onConnectionFailed(String reason);
    }

    public void setNetworkListener(NetworkListener listener) {
        this.listener = listener;
    }

    /**
     * Starts listening for incoming peer connections on a dedicated daemon thread.
     * Binds explicitly to 0.0.0.0 (wildcard) and checks alternate ports (8080-8084)
     * if the default port is already bound.
     */
    public void startServer() {
        Thread serverThread = new Thread(() -> {
            boolean bound = false;
            for (int offset = 0; offset < MAX_PORT_ATTEMPTS; offset++) {
                int testPort = DEFAULT_PORT + offset;
                try {
                    // Bind explicitly to 0.0.0.0 across all network interfaces
                    serverSocket = new ServerSocket(testPort, 50, InetAddress.getByName("0.0.0.0"));
                    listeningPort = testPort;
                    bound = true;
                    break;
                } catch (IOException bindEx) {
                    // Port already bound; try next port
                }
            }

            if (!bound) {
                if (listener != null) {
                    listener.onConnectionFailed("Unable to bind ServerSocket to ports 8080-" + (DEFAULT_PORT + MAX_PORT_ATTEMPTS - 1));
                }
                return;
            }

            String myId = IDResolver.generateIDFromIP();
            if (listener != null) {
                listener.onServerStarted(listeningPort, myId);
            }

            while (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket incoming = serverSocket.accept();
                    incoming.setTcpNoDelay(true);
                    incoming.setSoTimeout(15000);

                    synchronized (this) {
                        if (isConnected) {
                            // Already connected to an active peer; reject redundant socket
                            try { incoming.close(); } catch (IOException ignored) {}
                            continue;
                        }

                        this.activeSocket = incoming;
                        this.isConnected = true;
                        setupStreamer(incoming);
                    }
                } catch (IOException e) {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        if (listener != null) {
                            listener.onConnectionFailed("Incoming connection error: " + e.getMessage());
                        }
                    }
                }
            }
        }, "Connecta-ServerListener");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Connects to a remote peer using their 8-character Connecta Hex ID.
     */
    public void connectToPeer(String partnerID) {
        String ip = IDResolver.resolveIPFromID(partnerID);
        if (ip == null) {
            if (listener != null) {
                listener.onConnectionFailed("Invalid Connecta ID format. Must be an 8-character Hex code.");
            }
            return;
        }
        connectToAddress(ip, DEFAULT_PORT);
    }

    /**
     * Connects directly to a resolved LAN IP and Port.
     * Enforces connection timeouts and delivers clean, specific error diagnostics.
     */
    public void connectToAddress(String ip, int targetPort) {
        if (isConnected) return;

        Thread clientThread = new Thread(() -> {
            Socket socket = new Socket();
            try {
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(15000);

                // Timeout connection to avoid hanging Swing EDT
                socket.connect(new InetSocketAddress(ip, targetPort), CONNECT_TIMEOUT_MS);
            } catch (IOException ex1) {
                // If connecting on same machine and default failed, attempt fallback port
                int alternatePort = (targetPort == DEFAULT_PORT) ? DEFAULT_PORT + 1 : DEFAULT_PORT;
                try {
                    socket.close();
                } catch (IOException ignored) {}

                socket = new Socket();
                try {
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(15000);
                    socket.connect(new InetSocketAddress(ip, alternatePort), CONNECT_TIMEOUT_MS);
                } catch (Exception ex2) {
                    String errorMsg = buildSpecificConnectionErrorMessage(ip, targetPort, ex1);
                    if (listener != null) {
                        listener.onConnectionFailed(errorMsg);
                    }
                    try { socket.close(); } catch (IOException ignored) {}
                    return;
                }
            }

            synchronized (this) {
                if (isConnected) {
                    try { socket.close(); } catch (IOException ignored) {}
                    return;
                }
                this.activeSocket = socket;
                this.isConnected = true;
                setupStreamer(socket);
            }
        }, "Connecta-ClientConnector");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private String buildSpecificConnectionErrorMessage(String ip, int port, Exception ex) {
        if (ex instanceof ConnectException) {
            return "Connection refused by " + ip + ":" + port + ". Ensure Connecta is running on the partner device and firewall allows incoming connections.";
        } else if (ex instanceof SocketTimeoutException) {
            return "Connection timed out connecting to " + ip + ". The device may be offline, sleeping, or on a different Wi-Fi network.";
        } else if (ex instanceof NoRouteToHostException) {
            return "No network route to host " + ip + ". Verify both devices are connected to the same LAN.";
        } else if (ex instanceof UnknownHostException) {
            return "Cannot resolve partner IP: " + ip;
        } else {
            return "Connection failed to " + ip + ":" + port + " (" + ex.getMessage() + ")";
        }
    }

    /**
     * Initializes the SocketStreamer upon a successful socket establishment.
     */
    private void setupStreamer(Socket socket) {
        try {
            streamer = new SocketStreamer(socket);
            streamer.setStatusListener(new SocketStreamer.StreamerStatusListener() {
                @Override
                public void onPacketSent(FilePacket packet) {}

                @Override
                public void onDisconnected(String reason) {
                    synchronized (NetworkManager.this) {
                        isConnected = false;
                        activeSocket = null;
                        streamer = null;
                    }
                    if (listener != null) {
                        listener.onPeerDisconnected(reason);
                    }
                }
            });

            streamer.start();

            String peerIP = socket.getInetAddress().getHostAddress();
            int peerPort = socket.getPort();
            if (listener != null) {
                listener.onPeerConnected(peerIP, peerPort, streamer);
            }
        } catch (IOException e) {
            disconnect();
            if (listener != null) {
                listener.onConnectionFailed("Failed to initialize data streams: " + e.getMessage());
            }
        }
    }

    /**
     * Disconnects the active peer session cleanly.
     */
    public synchronized void disconnect() {
        isConnected = false;
        if (streamer != null) {
            streamer.close();
            streamer = null;
        }
        if (activeSocket != null && !activeSocket.isClosed()) {
            try {
                activeSocket.close();
            } catch (IOException ignored) {}
            activeSocket = null;
        }
    }

    /**
     * Full shutdown of server and active sockets.
     */
    public void shutdown() {
        disconnect();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public boolean isConnected() {
        return isConnected && activeSocket != null && activeSocket.isConnected() && !activeSocket.isClosed();
    }

    public SocketStreamer getStreamer() {
        return streamer;
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public String getPeerAddress() {
        if (activeSocket != null && activeSocket.isConnected()) {
            return activeSocket.getInetAddress().getHostAddress();
        }
        return "Unknown";
    }

    public int getPeerPort() {
        if (activeSocket != null && activeSocket.isConnected()) {
            return activeSocket.getPort();
        }
        return DEFAULT_PORT;
    }
}
