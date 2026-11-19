package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * NetworkManager
 * 
 * Central network controller responsible for hosting the incoming TCP server daemon
 * and initiating outbound socket connections to peer devices.
 * 
 * Manages the lifecycle of the SocketStreamer session once a socket handshake is complete.
 */
public class NetworkManager {

    public static final int DEFAULT_PORT = 8080;
    public static final int FALLBACK_PORT = 8081;

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
        void onPeerConnected(String peerIP, SocketStreamer streamer);
        void onPeerDisconnected(String reason);
        void onConnectionFailed(String reason);
    }

    public void setNetworkListener(NetworkListener listener) {
        this.listener = listener;
    }

    /**
     * Starts listening for incoming peer connections on a dedicated daemon thread.
     * Automatically attempts DEFAULT_PORT (8080) and falls back to FALLBACK_PORT (8081)
     * if the primary port is already bound (e.g., when testing two instances on the same PC).
     */
    public void startServer() {
        Thread serverThread = new Thread(() -> {
            try {
                try {
                    serverSocket = new ServerSocket(DEFAULT_PORT);
                    listeningPort = DEFAULT_PORT;
                } catch (IOException bindEx) {
                    // Try fallback port for same-machine dual-instance testing
                    serverSocket = new ServerSocket(FALLBACK_PORT);
                    listeningPort = FALLBACK_PORT;
                }

                String myId = IDResolver.generateIDFromIP();
                if (listener != null) {
                    listener.onServerStarted(listeningPort, myId);
                }

                while (!serverSocket.isClosed()) {
                    Socket incoming = serverSocket.accept();
                    synchronized (this) {
                        if (isConnected) {
                            // Already connected to a peer; reject redundant incoming socket
                            try { incoming.close(); } catch (IOException ignored) {}
                            continue;
                        }

                        this.activeSocket = incoming;
                        this.isConnected = true;
                        setupStreamer(incoming);
                    }
                }
            } catch (IOException e) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    if (listener != null) {
                        listener.onConnectionFailed("Server socket error: " + e.getMessage());
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
        if (isConnected) {
            if (listener != null) {
                listener.onConnectionFailed("Already connected to an active peer.");
            }
            return;
        }

        Thread clientThread = new Thread(() -> {
            String ip = IDResolver.resolveIPFromID(partnerID);
            if (ip == null) {
                if (listener != null) {
                    listener.onConnectionFailed("Invalid Connecta ID format. Must be an 8-character Hex code.");
                }
                return;
            }

            // Attempt primary port 8080 first
            Socket socket = null;
            try {
                socket = new Socket(ip, DEFAULT_PORT);
            } catch (IOException ex1) {
                // If connecting to localhost or same LAN, try fallback port 8081
                try {
                    socket = new Socket(ip, FALLBACK_PORT);
                } catch (IOException ex2) {
                    if (listener != null) {
                        listener.onConnectionFailed("Could not connect to " + ip + " (Device unreachable or offline).");
                    }
                    return;
                }
            }

            synchronized (this) {
                this.activeSocket = socket;
                this.isConnected = true;
                setupStreamer(socket);
            }
        }, "Connecta-ClientConnector");
        clientThread.setDaemon(true);
        clientThread.start();
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
            if (listener != null) {
                listener.onPeerConnected(peerIP, streamer);
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
}
