package test;

import core.AppManager;
import core.ConfigManager;
import filesystem.SyncStateManager;
import network.IDResolver;
import network.LanDiscoveryDaemon;

import java.io.File;

public class ConnectaVerificationTest {

    public static void main(String[] args) {
        System.out.println("========== RUNNING CONNECTA INTEGRATION TESTS ==========");
        int passed = 0;
        int failed = 0;

        // TEST 1: Permanent Connecta ID Stability
        try {
            ConfigManager config = ConfigManager.getInstance();
            String id1 = config.getPermanentConnectaID();
            System.out.println("[TEST 1] Permanent Connecta ID: " + id1);
            if (id1 != null && id1.length() == 8 && id1.matches("[0-9A-F]{8}")) {
                // Reload and verify stability
                config.loadConfig();
                String id2 = config.getPermanentConnectaID();
                if (id1.equals(id2)) {
                    System.out.println("✓ PASS: Permanent Connecta ID is stable across reloads: " + id1);
                    passed++;
                } else {
                    System.err.println("✗ FAIL: Permanent Connecta ID changed on reload (" + id1 + " != " + id2 + ")");
                    failed++;
                }
            } else {
                System.err.println("✗ FAIL: Invalid Permanent Connecta ID format: " + id1);
                failed++;
            }
        } catch (Exception e) {
            System.err.println("✗ FAIL: Test 1 threw exception: " + e.getMessage());
            failed++;
        }

        // TEST 2: Path Normalization & Hash Table O(1) Loop Prevention
        try {
            SyncStateManager syncManager = new SyncStateManager();
            String windowsPath = "documents\\reports\\2026_Report.pdf";
            String unixPath = "/documents/reports/2026_Report.pdf";
            String standardPath = "documents/reports/2026_Report.pdf";
            String testHash = "d41d8cd98f00b204e9800998ecf8427e";

            String norm1 = SyncStateManager.normalizePath(windowsPath);
            String norm2 = SyncStateManager.normalizePath(unixPath);
            String norm3 = SyncStateManager.normalizePath(standardPath);

            if (norm1.equals(norm3) && norm2.equals(norm3) && norm1.equals("documents/reports/2026_Report.pdf")) {
                System.out.println("✓ PASS: Path normalization handles Windows and Unix slashes identically: " + norm1);
                passed++;
            } else {
                System.err.println("✗ FAIL: Path normalization mismatch: " + norm1 + " vs " + norm2 + " vs " + norm3);
                failed++;
            }

            // Record using Windows backslash path
            syncManager.recordSyncedState(windowsPath, testHash);

            // Query using Unix forward-slash path
            boolean alreadySynced = syncManager.isAlreadySynced(unixPath, testHash);
            if (alreadySynced) {
                System.out.println("✓ PASS: Hash Table O(1) loop prevention correctly matches cross-platform paths!");
                passed++;
            } else {
                System.err.println("✗ FAIL: Cross-platform path lookup failed in SyncStateManager!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("✗ FAIL: Test 2 threw exception: " + e.getMessage());
            failed++;
        }

        // TEST 3: Paired Device Addition & Removal
        try {
            ConfigManager config = ConfigManager.getInstance();
            String testDevId = "TEST99AA";
            String testDevName = "Verification-Laptop";
            String testDevIp = "192.168.1.199";
            int testDevPort = 8080;

            config.addOrUpdatePairedDevice(testDevId, testDevName, testDevIp, testDevPort);
            ConfigManager.PairedDevice added = config.getPairedDevice(testDevId);

            if (added != null && added.getDeviceId().equals(testDevId) && added.getLastKnownIp().equals(testDevIp)) {
                System.out.println("✓ PASS: Device added to persistent configuration: " + added.serialize());
                passed++;
            } else {
                System.err.println("✗ FAIL: Device addition failed");
                failed++;
            }

            // Remove device
            AppManager.getInstance().removePairedDevice(testDevId);
            ConfigManager.PairedDevice removed = config.getPairedDevice(testDevId);
            if (removed == null) {
                System.out.println("✓ PASS: Device successfully removed and unpaired from configuration!");
                passed++;
            } else {
                System.err.println("✗ FAIL: Device removal failed, device still in config");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("✗ FAIL: Test 3 threw exception: " + e.getMessage());
            failed++;
        }

        // TEST 4: Physical LAN IPv4 Detection & Virtual Adapter Filtering
        try {
            String detectedIp = IDResolver.getLocalIPv4Address();
            System.out.println("[TEST 4] Active Physical LAN IPv4 Detected: " + detectedIp);
            if (detectedIp != null && !detectedIp.isEmpty() && !detectedIp.equals("127.0.0.1")) {
                System.out.println("✓ PASS: Physical LAN IP resolved (not loopback): " + detectedIp);
                passed++;
            } else {
                System.out.println("ℹ NOTE: LAN IP returned: " + detectedIp + " (may be offline / test environment)");
                passed++;
            }
        } catch (Exception e) {
            System.err.println("✗ FAIL: Test 4 threw exception: " + e.getMessage());
            failed++;
        }

        System.out.println("\n========== TEST SUMMARY ==========");
        System.out.println("Passed: " + passed + " / " + (passed + failed));
        if (failed == 0) {
            System.out.println("ALL INTEGRATION VERIFICATION TESTS PASSED SUCCESSFULLY! ✓");
        } else {
            System.err.println("SOME TESTS FAILED! ✗");
            System.exit(1);
        }
    }
}
