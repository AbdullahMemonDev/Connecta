package filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SyncStateManager
 * 
 * Tracks file synchronization history using high-performance cryptographic hashing
 * and concurrent hash-table storage to eliminate distributed sync loops.
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. Hash Table (ConcurrentHashMap<String, String>):
 *    - Structure: Key = Relative File Path (e.g., "docs/report.pdf"), Value = MD5 Hex Checksum.
 *    - Search Complexity: O(1) average-case time complexity for lookups, insertions, and updates.
 *    - Thread Safety: Leverages ConcurrentHashMap with lock-striping (bucket-level locking)
 *      to allow concurrent, non-blocking reads and partitioned writes across multiple background
 *      file-watcher and network-receiver threads.
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

        String existingChecksum = syncHistory.get(relativePath);
        return existingChecksum != null && existingChecksum.equalsIgnoreCase(checksum);
    }

    /**
     * Updates the Hash Table with the latest synced state of a file.
     * 
     * DSA Complexity: O(1) hash table insertion.
     */
    public void recordSyncedState(String relativePath, String checksum) {
        if (relativePath != null && checksum != null) {
            syncHistory.put(relativePath, checksum);
        }
    }

    /**
     * Removes a file entry from the Hash Table when deleted.
     * 
     * DSA Complexity: O(1) hash table deletion.
     */
    public void removeHistory(String relativePath) {
        if (relativePath != null) {
            syncHistory.remove(relativePath);
        }
    }

    /**
     * Retrieves the stored checksum for a given relative path.
     * 
     * DSA Complexity: O(1) hash table lookup.
     */
    public String getChecksum(String relativePath) {
        if (relativePath == null) return null;
        return syncHistory.get(relativePath);
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
