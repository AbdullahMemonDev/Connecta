package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LanDiscoveryDaemon
 * 
 * Provides automated peer discovery over Local Area Networks (LAN) using UDP broadcast beacons.
 * Allows paired Connecta devices to automatically discover each other's current IP addresses
 * (even across dynamic DHCP reallocations or Wi-Fi network switches) and trigger instantaneous
 * TCP background reconnection.
 * 
 * Features:
 * 1. Broadcasts to both subnet-directed broadcast addresses (e.g., 192.168.1.255) and global 255.255.255.255.
 * 2. Broadcasts Permanent Connecta ID, Device Name, active LAN IPv4, and TCP listening port.
 * 3. Maintains a concurrent registry of discovered LAN peers.
 * 4. Notifies AppManager when peers appear or change IP addresses.
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

    /**
     * Model representing a remote peer discovered on the LAN.
     */
    public static class DiscoveredPeer {
        public final String id;
        public final String name;
        public final String ip;
        public final int port;
        public final long lastSeen;

        public DiscoveredPeer(String id, String name, String ip, int port, long lastSeen) {
            this.id = id != null ? id.trim().toUpperCase() : "";
            this.name = name != null ? name.trim() : "Unknown";
            this.ip = ip != null ? ip.trim() : "";
            this.port = port > 0 ? port : 8080;
            this.lastSeen = lastSeen;
        }
    }

    // Registry of actively discovered peers on the LAN
    private final ConcurrentHashMap<String, DiscoveredPeer> discoveredPeers = new ConcurrentHashMap<>();

    public interface DiscoveryListener {
        void onPeerDiscovered(String peerId, String peerName, String peerIp, int peerPort);
    }

    public LanDiscoveryDaemon(String myConnectaID, String myDeviceName, int myTcpPort, DiscoveryListener listener) {
        this.myConnectaID = myConnectaID != null ? myConnectaID.trim().toUpperCase() : "UNKNOWN";
        this.myDeviceName = myDeviceName != null ? myDeviceName.replace("|", "_") : "Connecta-Device";
        this.myTcpPort = myTcpPort;
        this.listener = listener;
    }

    /**
     * Retrieves an actively discovered LAN peer by its 8-character Connecta ID.
     */
    public DiscoveredPeer getDiscoveredPeer(String peerId) {
        if (peerId == null) return null;
        return discoveredPeers.get(peerId.trim().toUpperCase());
    }

    /**
     * Returns a snapshot of all currently discovered peers on the LAN.
     */
    public List<DiscoveredPeer> getAllDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers.values());
    }

    /**
     * Starts the UDP sender and receiver threads.
     */
    public void start() {
        startListenerThread();
        startBeaconThread();
    }

    private List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> broadcastList = new ArrayList<>();
        try {
            broadcastList.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {}

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;

                for (InterfaceAddress ifAddr : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = ifAddr.getBroadcast();
                    if (broadcast != null && !broadcastList.contains(broadcast)) {
                        broadcastList.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {}
        return broadcastList;
    }

    private void startBeaconThread() {
        beaconThread = new Thread(() -> {
            try {
                broadcastSocket = new DatagramSocket();
                broadcastSocket.setBroadcast(true);

                while (running) {
                    try {
                        String currentLanIp = IDResolver.getLocalIPv4Address();
                        String message = BEACON_PREFIX + myConnectaID + "|" + myDeviceName + "|" + currentLanIp + "|" + myTcpPort;
                        byte[] buffer = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                        List<InetAddress> targets = getBroadcastAddresses();
                        for (InetAddress target : targets) {
                            try {
                                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, target, UDP_DISCOVERY_PORT);
                                broadcastSocket.send(packet);
                            } catch (IOException ignored) {}
                        }
                    } catch (Exception ignored) {}

                    // Broadcast beacon every 3.0 seconds
                    try {
                        Thread.sleep(3000);
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
                            String peerId = parts[0].trim().toUpperCase();
                            String peerName = parts[1].trim();
                            String peerIp = packet.getAddress().getHostAddress();
                            int peerPort = 8080;

                            if (parts.length >= 4) {
                                // Payload format: ID | Name | LAN_IP | Port
                                String declaredIp = parts[2].trim();
                                if (!declaredIp.isEmpty() && !declaredIp.equals("127.0.0.1") && IDResolver.isPrivateLANIPv4(declaredIp)) {
                                    peerIp = declaredIp;
                                }
                                try {
                                    peerPort = Integer.parseInt(parts[3].trim());
                                } catch (Exception ignored) {}
                            } else {
                                // Legacy payload format: ID | Name | Port
                                try {
                                    peerPort = Integer.parseInt(parts[2].trim());
                                } catch (Exception ignored) {}
                            }

                            // Verify peer IP is a valid private LAN address and not self-loopback
                            if (!peerId.equalsIgnoreCase(myConnectaID) && IDResolver.isPrivateLANIPv4(peerIp)) {
                                DiscoveredPeer discovered = new DiscoveredPeer(peerId, peerName, peerIp, peerPort, System.currentTimeMillis());
                                discoveredPeers.put(peerId, discovered);

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
