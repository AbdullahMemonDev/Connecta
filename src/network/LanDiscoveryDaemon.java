package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * LanDiscoveryDaemon
 * 
 * Provides automated peer discovery over Local Area Networks (LAN) using UDP broadcast beacons.
 * Allows paired Connecta devices to automatically discover each other's current IP addresses
 * (even across dynamic DHCP reallocations) and trigger instantaneous TCP background reconnection.
 */
public class LanDiscoveryDaemon {

    public static final int UDP_DISCOVERY_PORT = 21027;
    private static final String BEACON_PREFIX = "CONNECTA_BEACON|";

    private final String myConnectaID;
    private final String myDeviceName;
    private final int myTcpPort;
    private final DiscoveryListener listener;

    private volatile boolean running = true;
    private DatagramSocket broadcastSocket;
    private DatagramSocket listenerSocket;
    private Thread beaconThread;
    private Thread listenerThread;

    public interface DiscoveryListener {
        void onPeerDiscovered(String peerId, String peerName, String peerIp, int peerPort);
    }

    public LanDiscoveryDaemon(String myConnectaID, String myDeviceName, int myTcpPort, DiscoveryListener listener) {
        this.myConnectaID = myConnectaID;
        this.myDeviceName = myDeviceName != null ? myDeviceName.replace("|", "_") : "Connecta-Device";
        this.myTcpPort = myTcpPort;
        this.listener = listener;
    }

    /**
     * Starts the UDP sender and receiver threads.
     */
    public void start() {
        startListenerThread();
        startBeaconThread();
    }

    private void startBeaconThread() {
        beaconThread = new Thread(() -> {
            try {
                broadcastSocket = new DatagramSocket();
                broadcastSocket.setBroadcast(true);

                String message = BEACON_PREFIX + myConnectaID + "|" + myDeviceName + "|" + myTcpPort;
                byte[] buffer = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddr, UDP_DISCOVERY_PORT);
                        broadcastSocket.send(packet);
                    } catch (IOException ignored) {}

                    // Broadcast beacon every 3.5 seconds
                    try {
                        Thread.sleep(3500);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } catch (Exception e) {
                // Sender socket setup failed
            } finally {
                if (broadcastSocket != null && !broadcastSocket.isClosed()) {
                    broadcastSocket.close();
                }
            }
        }, "Connecta-UDPBeaconSender");
        beaconThread.setDaemon(true);
        beaconThread.start();
    }

    private void startListenerThread() {
        listenerThread = new Thread(() -> {
            try {
                listenerSocket = new DatagramSocket(null);
                listenerSocket.setReuseAddress(true);
                listenerSocket.bind(new InetSocketAddress(UDP_DISCOVERY_PORT));

                byte[] receiveBuffer = new byte[1024];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    try {
                        listenerSocket.receive(packet);
                    } catch (IOException e) {
                        if (!running) break;
                        continue;
                    }

                    String content = new String(packet.getData(), 0, packet.getLength(), java.nio.charset.StandardCharsets.UTF_8);
                    if (content.startsWith(BEACON_PREFIX)) {
                        String payload = content.substring(BEACON_PREFIX.length());
                        String[] parts = payload.split("\\|");
                        if (parts.length >= 3) {
                            String peerId = parts[0].trim();
                            String peerName = parts[1].trim();
                            int peerPort = 8080;
                            try {
                                peerPort = Integer.parseInt(parts[2].trim());
                            } catch (Exception ignored) {}

                            // Ignore loopback beacons from this local instance
                            if (!peerId.equalsIgnoreCase(myConnectaID)) {
                                String peerIp = packet.getAddress().getHostAddress();
                                if (listener != null) {
                                    listener.onPeerDiscovered(peerId, peerName, peerIp, peerPort);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (listenerSocket != null && !listenerSocket.isClosed()) {
                    listenerSocket.close();
                }
            }
        }, "Connecta-UDPBeaconListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stop() {
        running = false;
        if (broadcastSocket != null && !broadcastSocket.isClosed()) {
            broadcastSocket.close();
        }
        if (listenerSocket != null && !listenerSocket.isClosed()) {
            listenerSocket.close();
        }
        if (beaconThread != null) beaconThread.interrupt();
        if (listenerThread != null) listenerThread.interrupt();
    }
}
