package filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SyncStateManager
 * 
 * Tracks file synchronization history using high-performance cryptographic hashing
 * and concurrent hash-table storage to eliminate distributed sync loops and power
 * automatic catch-up synchronization.
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. Hash Table (ConcurrentHashMap<String, String>):
 *    - Structure: Key = Normalized Relative File Path (e.g., "docs/report.pdf"), Value = MD5 Hex Checksum.
 *    - Search Complexity: O(1) average-case time complexity for lookups, insertions, and updates.
 *    - Thread Safety: Leverages ConcurrentHashMap with lock-striping (bucket-level locking)
 *      to allow concurrent, non-blocking reads and partitioned writes across multiple background
 *      file-watcher, network-receiver, and catch-up sync threads.
 *    - Distributed Infinite Loop Prevention:
 *      In peer-to-peer folder synchronization, when Peer A transfers a modified file to Peer B,
 *      Peer B writes the file to disk. The operating system's native file-system watcher fires
 *      a 'MODIFY' event on Peer B. Without loop prevention, Peer B would detect this event and
 *      send the file back to Peer A, creating an infinite ping-pong network loop.
 *      By performing an O(1) hash table lookup against the stored MD5 checksum, Peer B confirms
 *      the current disk content matches the recorded state and safely discards the echo event.
 * =========================================================================================
 */
public class SyncStateManager {

    // DSA IMPLEMENTATION: Concurrent Hash Table mapping Relative Path -> MD5 Checksum
    private final ConcurrentHashMap<String, String> syncHistory = new ConcurrentHashMap<>();

    /**
     * Normalizes a file path across platforms:
     * - Replaces all Windows backslashes with forward slashes
     * - Removes any redundant leading slashes
     * - Trims whitespace
     */
    public static String normalizePath(String path) {
        if (path == null) return "";
        String p = path.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    /**
     * Calculates the MD5 cryptographic checksum of a local disk file.
     * Reads in 8KB blocks to ensure memory-bounded computation for large files.
     * 
     * @param file The disk file to hash.
     * @return 32-character Hexadecimal MD5 checksum, or null if computation fails.
     */
    public String calculateChecksum(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192]; // Fixed 8KB sequential buffer
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }

            byte[] digestBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digestBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if the file with given relative path and checksum is already synced.
     * 
     * DSA Complexity: O(1) hash table search.
     * 
     * @param relativePath Relative path from the root shared directory.
     * @param checksum Current MD5 checksum of the file.
     * @return true if the file already exists in history with the exact same checksum.
     */
    public boolean isAlreadySynced(String relativePath, String checksum) {
        if (relativePath == null || checksum == null) {
            return false;
        }

        String norm = normalizePath(relativePath);

        // Always treat temporary or staging files as already accounted for
        if (norm.endsWith(FileChunkManager.TMP_EXTENSION) || norm.endsWith(".tmp") || norm.endsWith(".part")) {
            return true;
        }

        String existingChecksum = syncHistory.get(norm);
        return existingChecksum != null && existingChecksum.equalsIgnoreCase(checksum.trim());
    }

    /**
     * Updates the Hash Table with the latest synced state of a file.
     * 
     * DSA Complexity: O(1) hash table insertion.
     */
    public void recordSyncedState(String relativePath, String checksum) {
        if (relativePath != null && checksum != null) {
            String norm = normalizePath(relativePath);
            syncHistory.put(norm, checksum.trim());
        }
    }

    /**
     * Removes a file entry from the Hash Table when deleted.
     * 
     * DSA Complexity: O(1) hash table deletion.
     */
    public void removeHistory(String relativePath) {
        if (relativePath != null) {
            syncHistory.remove(normalizePath(relativePath));
        }
    }

    /**
     * Retrieves the stored checksum for a given relative path.
     * 
     * DSA Complexity: O(1) hash table lookup.
     */
    public String getChecksum(String relativePath) {
        if (relativePath == null) return null;
        return syncHistory.get(normalizePath(relativePath));
    }

    /**
     * Builds a serialized manifest string of all currently indexed files.
     * Format: relativePath=checksum\n
     */
    public String buildManifestString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : syncHistory.entrySet()) {
            if (!entry.getKey().endsWith(FileChunkManager.TMP_EXTENSION) && !entry.getKey().endsWith(".tmp")) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Compares local Hash Table state with an incoming peer manifest to identify
     * files that we are missing or have outdated checksums.
     * 
     * @param remoteManifest Raw manifest string from peer.
     * @return List of relative file paths that we need to request/update from peer.
     */
    public List<String> findFilesNeedingSyncFromRemote(String remoteManifest) {
        List<String> neededFiles = new ArrayList<>();
        if (remoteManifest == null || remoteManifest.trim().isEmpty()) return neededFiles;

        String[] lines = remoteManifest.split("\n");
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                String remotePath = normalizePath(line.substring(0, idx).trim());
                String remoteHash = line.substring(idx + 1).trim();

                String localHash = syncHistory.get(remotePath);
                if (localHash == null || !localHash.equalsIgnoreCase(remoteHash)) {
                    neededFiles.add(remotePath);
                }
            }
        }
        return neededFiles;
    }

    /**
     * Returns an unmodifiable view of all currently tracked files and their checksums.
     */
    public Map<String, String> getAllTrackedFiles() {
        return Collections.unmodifiableMap(syncHistory);
    }

    /**
     * Clears all state history (e.g., when switching shared folders).
     */
    public void clear() {
        syncHistory.clear();
    }
}
