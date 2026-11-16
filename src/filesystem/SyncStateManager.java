
// SyncStateManager

package filesystem; // Declares the package folder where this class resides

import java.io.File; // Imports Java File class to reference local files on disk
import java.io.FileInputStream; // Imports FileInputStream to read raw bytes from files
import java.security.MessageDigest; // Imports MessageDigest to generate cryptographic hashes (MD5)
import java.util.concurrent.ConcurrentHashMap; // Imports thread-safe map to avoid race conditions across multi-threads

public class SyncStateManager {
    
    // Thread-safe map storing file relative paths as keys and their MD5 checksums as values.
    // ConcurrentHashMap prevents ConcurrentModificationException when background threads access it simultaneously.
    private ConcurrentHashMap<String, String> syncHistory = new ConcurrentHashMap<>();

    /**
     * Calculates the MD5 checksum of a given file.
     * @param file The file whose hash needs to be calculated.
     * @return A hexadecimal string representing the MD5 hash, or null if invalid.
     */
    public String calculateChecksum(File file) {
        // Validation check: return null if file is null, doesn't exist, or is a directory
        if (file == null || !file.exists() || file.isDirectory()) {
            return null;
        }
        
        // Try-with-resources block ensures the file stream is automatically closed after reading
        try (FileInputStream fis = new FileInputStream(file)) {
            
            // Initialize MessageDigest instance specifically for the "MD5" algorithm
            MessageDigest md = MessageDigest.getInstance("MD5");
            
            // Create an 8KB byte buffer chunk to read the file in blocks (memory-safe for large files)
            byte[] buffer = new byte[8192];
            int bytesCount;
            
            // Read file chunks iteratively until EOF (-1) is reached
            while ((bytesCount = fis.read(buffer)) != -1) {
                // Update the digest engine with the bytes read in the current buffer
                md.update(buffer, 0, bytesCount);
            }
            
            // Complete the hash computation and get the resulting raw byte array
            byte[] bytes = md.digest();
            
            // Convert the raw byte array into a readable Hexadecimal string format
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                // Converts byte to hex and pads with leading zeros if necessary
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString(); // Return final checksum string
            
        } catch (Exception e) {
            e.printStackTrace(); // Print stack trace if an I/O or crypto exception occurs
            return null;
        }
    }

    /**
     * Checks if the file event is already accounted for to prevent infinite sync loops.
     * @param relativePath The path of the file relative to the shared root folder.
     * @param checksum The current MD5 checksum of the file.
     * @return true if it's a duplicate event (should be ignored), false if it's new.
     */
    public boolean isAlreadySynced(String relativePath, String checksum) {
        // Guard clause against null arguments
        if (relativePath == null || checksum == null) {
            return false;
        }
        
        // Retrieve the last recorded checksum for this file path from history
        String existingChecksum = syncHistory.get(relativePath);
        
        // If the path exists in history and its checksum matches the current one, it's a duplicate loop event
        if (existingChecksum != null && existingChecksum.equals(checksum)) {
            return true; // Ignore duplicate event to prevent infinite looping across peers
        }
        
        // Otherwise, update the history map with this new/modified state
        syncHistory.put(relativePath, checksum);
        return false; // Not a duplicate; process/sync the file event
    }
    
    /**
     * Clears history record when a file is deleted from the shared directory.
     * @param relativePath The relative path of the deleted file.
     */
    public void removeHistory(String relativePath) {
        syncHistory.remove(relativePath); // Removes entry from the concurrent map
    }
}
