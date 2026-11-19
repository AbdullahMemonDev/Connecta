package network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * IDResolver Utility Class
 * 
 * Provides bi-directional mapping between IPv4 LAN addresses and compact 8-character
 * Hexadecimal Connecta Identifiers (e.g., "192.168.1.5" <-> "C0A80105").
 * 
 * This enables peer devices on the same Local Area Network (LAN) to easily discover
 * and pair with each other without manually typing complex IP addresses.
 */
public class IDResolver {

    /**
     * Resolves the primary active LAN IPv4 address of the local machine and
     * converts it into an 8-character uppercase Hexadecimal Connecta ID.
     * 
     * Iterates through available network interfaces to select an active, non-loopback
     * site-local interface (e.g., Wi-Fi or Ethernet), falling back to getLocalHost().
     * 
     * @return 8-character Hex Connecta ID, or "ERROR" if IP cannot be determined.
     */
    public static String generateIDFromIP() {
        try {
            String ip = getLocalLanIP();
            if (ip == null || ip.isEmpty() || ip.equals("127.0.0.1")) {
                ip = InetAddress.getLocalHost().getHostAddress();
            }

            StringBuilder hexString = new StringBuilder();
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return "ERROR";
            }

            // Convert each 8-bit octet (0-255) to a 2-character Hexadecimal string
            for (String part : parts) {
                int octet = Integer.parseInt(part.trim());
                hexString.append(String.format("%02X", octet));
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    /**
     * Converts an 8-character Hexadecimal Connecta ID back into a standard
     * dotted-quad IPv4 address (e.g., "C0A80105" -> "192.168.1.5").
     * 
     * @param id The 8-character Hex ID entered by the user.
     * @return Decoded IPv4 string, or null if ID format is invalid.
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
            return ip.toString();
        } catch (NumberFormatException e) {
            return null; // Non-hexadecimal characters encountered
        }
    }

    /**
     * Inspects active network interfaces to find the real physical/Wi-Fi LAN IP address.
     * Prioritizes 192.168.x.x, 10.x.x.x, and 172.16-31.x.x private LAN ranges.
     */
    private static String getLocalLanIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String candidate = null;

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Filter out loopback, inactive, or virtual interfaces
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        // Prioritize common LAN subnets
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host; // Found optimal LAN IP
                        }
                        if (candidate == null) {
                            candidate = host;
                        }
                    }
                }
            }
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }
}
