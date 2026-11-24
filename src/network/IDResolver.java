package network;

import core.ConfigManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * IDResolver Utility Class
 * 
 * Provides robust physical LAN IP detection and bi-directional identification mapping.
 * 
 * Key Functions:
 * 1. Permanent Connecta ID: Retrieved directly from ConfigManager (generated once, persisted locally,
 *    and immune to Wi-Fi/DHCP IP switches).
 * 2. Physical LAN IPv4 Resolution: Specifically audits all network interfaces and eliminates
 *    virtual adapters (WSL, VMware, VirtualBox, Hyper-V, vEthernet, Docker, ZeroTier, Tailscale)
 *    to reliably resolve the active physical Wi-Fi or Ethernet LAN IPv4 address (preferring 192.168.x.x,
 *    10.x.x.x, or 172.16-31.x.x).
 * 3. Safe Hex Mapping: Only decodes 8-character hex strings if they resolve to a valid private LAN IPv4 address,
 *    preventing random Connecta UUIDs from mistakenly decoding into public internet addresses.
 */
public class IDResolver {

    private static final String[] VIRTUAL_KEYWORDS = {
        "virtual", "vbox", "vmware", "hyper-v", "wsl", "vethernet", "docker",
        "tailscale", "tap", "tun", "zerotier", "loopback", "npcap", "pcap",
        "teredo", "isatap", "pseudo", "vpn", "host-only", "internal"
    };

    /**
     * Retrieves the persistent unique Connecta ID for this device.
     * Guaranteed never to change across network/Wi-Fi switches.
     */
    public static String getPermanentConnectaID() {
        return ConfigManager.getInstance().getPermanentConnectaID();
    }

    /**
     * Resolves the primary active physical LAN IPv4 address of the local machine and
     * converts it into an 8-character uppercase Hexadecimal Connecta ID.
     * 
     * @return 8-character Hex Connecta ID, or permanent ID fallback.
     */
    public static String generateIDFromIP() {
        try {
            String ip = getLocalIPv4Address();
            if (ip == null || ip.isEmpty() || ip.equals("127.0.0.1") || !isPrivateLANIPv4(ip)) {
                return getPermanentConnectaID();
            }

            StringBuilder hexString = new StringBuilder();
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return getPermanentConnectaID();
            }

            for (String part : parts) {
                int octet = Integer.parseInt(part.trim());
                hexString.append(String.format("%02X", octet));
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            return getPermanentConnectaID();
        }
    }

    /**
     * Converts an 8-character Hexadecimal Connecta ID back into a standard
     * dotted-quad IPv4 address (e.g., "C0A80105" -> "192.168.1.5").
     * 
     * CRITICAL SAFETY: Only returns the IP if it falls strictly within a private
     * LAN subnet (192.168.x.x, 10.x.x.x, 172.16-31.x.x). Random Connecta IDs
     * (e.g. "7940F566") will return null to prevent timeout connections to public IPs.
     * 
     * @param id The 8-character Hex ID.
     * @return Decoded private IPv4 string, or null if ID format is invalid or not a LAN IP.
     */
    public static String resolveIPFromID(String id) {
        if (id == null) return null;
        String cleanId = id.trim().toUpperCase().replace("-", "").replace(":", "");
        if (cleanId.length() != 8) {
            return null; // Invalid length for 4-byte IPv4 representation
        }

        try {
            StringBuilder ip = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                String hexByte = cleanId.substring(i, i + 2);
                int octet = Integer.parseInt(hexByte, 16);
                if (octet < 0 || octet > 255) {
                    return null;
                }
                ip.append(octet);
                if (i < 6) {
                    ip.append(".");
                }
            }
            String resolved = ip.toString();
            // Reject any IP that is NOT a private local LAN IP
            if (!isPrivateLANIPv4(resolved)) {
                return null;
            }
            return resolved;
        } catch (NumberFormatException e) {
            return null; // Non-hexadecimal characters encountered
        }
    }

    /**
     * Validates whether a given string is a dotted-decimal IPv4 address.
     */
    public static boolean isIPv4Address(String input) {
        if (input == null) return false;
        String[] parts = input.trim().split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int octet = Integer.parseInt(part.trim());
                if (octet < 0 || octet > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Checks if a given IPv4 address belongs to standard private local LAN subnets:
     * - 192.168.0.0/16
     * - 10.0.0.0/8
     * - 172.16.0.0/12
     */
    public static boolean isPrivateLANIPv4(String ip) {
        if (ip == null || !isIPv4Address(ip)) return false;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("10.")) return true;
        if (isPrivate172(ip)) return true;
        return false;
    }

    /**
     * Resolves the primary active physical LAN IPv4 address (Wi-Fi or Ethernet).
     * Strictly filters out loopback, inactive, and virtual adapters, prioritizing
     * private subnets (192.168.x.x, 10.x.x.x, 172.16-31.x.x).
     * 
     * @return Dotted-decimal IPv4 address string (e.g. "192.168.1.5"), or "127.0.0.1" fallback.
     */
    public static String getLocalIPv4Address() {
        try {
            List<InterfaceCandidate> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Skip inactive, loopback, or point-to-point adapters
                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }

                String combinedName = (iface.getName() + " " + iface.getDisplayName()).toLowerCase();

                // Check and reject known virtual adapter keywords
                boolean isVirtual = iface.isVirtual();
                if (!isVirtual) {
                    for (String keyword : VIRTUAL_KEYWORDS) {
                        if (combinedName.contains(keyword)) {
                            isVirtual = true;
                            break;
                        }
                    }
                }
                if (isVirtual) {
                    continue; // Skip virtual adapter
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        int score = calculateInterfaceScore(combinedName, addr, host);
                        if (score > 0) {
                            candidates.add(new InterfaceCandidate(host, score));
                        }
                    }
                }
            }

            if (!candidates.isEmpty()) {
                Collections.sort(candidates, (a, b) -> Integer.compare(b.score, a.score));
                return candidates.get(0).ip;
            }

            // Fallback: check local host address if private
            InetAddress localHost = InetAddress.getLocalHost();
            if (localHost instanceof Inet4Address && !localHost.isLoopbackAddress()) {
                String host = localHost.getHostAddress();
                if (isPrivateLANIPv4(host)) {
                    return host;
                }
            }
            return "127.0.0.1";
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * Evaluates and scores an interface to select the most reliable physical LAN adapter.
     */
    private static int calculateInterfaceScore(String combinedName, InetAddress addr, String host) {
        // Enforce private LAN IPv4 requirement
        if (!isPrivateLANIPv4(host)) {
            return -1000;
        }

        int score = 100;

        // Prefer physical Wi-Fi and Ethernet adapters
        if (combinedName.contains("wi-fi") || combinedName.contains("wifi") || combinedName.contains("wlan") || combinedName.contains("wireless") || combinedName.contains("802.11")) {
            score += 200;
        } else if (combinedName.contains("ethernet") || combinedName.contains("eth") || combinedName.contains("en0") || combinedName.contains("en1") || combinedName.contains("gigabit") || combinedName.contains("lan")) {
            score += 180;
        }

        // Subnet prioritization
        if (host.startsWith("192.168.")) {
            score += 80;
        } else if (host.startsWith("10.")) {
            score += 60;
        } else if (isPrivate172(host)) {
            score += 50;
        }

        if (addr.isSiteLocalAddress()) {
            score += 30;
        }

        return score;
    }

    private static boolean isPrivate172(String ip) {
        if (!ip.startsWith("172.")) return false;
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static class InterfaceCandidate {
        final String ip;
        final int score;

        InterfaceCandidate(String ip, int score) {
            this.ip = ip;
            this.score = score;
        }
    }
}
