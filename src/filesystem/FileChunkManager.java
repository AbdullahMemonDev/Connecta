package filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * FileChunkManager
 * 
 * Handles disk I/O operations for splitting files into fixed-size binary chunks
 * and sequentially reassembling received chunks back into identical disk files.
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. Sequential Memory Arrays (byte[]):
 *    - Memory Buffer: Employs fixed-size 8192-byte (8KB) contiguous primitive byte arrays.
 *    - Memory Complexity: Strictly O(1) auxiliary memory footprint. Instead of loading an entire
 *      file into heap memory, memory consumption is bounded strictly to 8KB per chunk operation.
 *    - Buffer Isolation: Never reuses or exposes the internal read buffer. Every chunk emitted
 *      to the async Bounded FIFO Queue is cloned via Arrays.copyOf() to prevent data corruption.
 *    - Atomic Staging: Inbound chunks are assembled into a staging file (<filename>.connecta.tmp),
 *      cryptographically verified against MD5 checksum, and atomically committed to storage.
 * =========================================================================================
 */
public class FileChunkManager {

    /**
     * Standard fixed chunk size: 8192 bytes (8KB).
     * Optimal for TCP window sizes and file system block boundaries.
     */
    public static final int CHUNK_SIZE = 8192;
    public static final String TMP_EXTENSION = ".connecta.tmp";

    /**
     * Functional callback interface triggered when each 8KB sequential memory chunk is read.
     */
    public interface ChunkCallback {
        void onChunkReady(byte[] chunk, int sequenceNumber, int totalChunks, long totalBytesRead, long fileSize) throws InterruptedException;
    }

    /**
     * Splits a target file into fixed 8192-byte array chunks, streaming each to the callback.
     * Enforces strict buffer isolation: creates an independent copy of the bytes read
     * (Arrays.copyOf) before passing it to the asynchronous Bounded FIFO Queue.
     * 
     * @param file The file on disk to split.
     * @param callback Callback delivering each sequential byte[] array chunk.
     * @throws IOException If disk read errors occur.
     * @throws InterruptedException If blocked on bounded queue backpressure and interrupted.
     */
    public void splitFileIntoChunks(File file, ChunkCallback callback) throws IOException, InterruptedException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Invalid file reference for chunking.");
        }

        long fileSize = file.length();
        int totalChunks = calculateTotalChunks(fileSize);

        // Try-with-resources ensures file handles are cleanly closed
        try (FileInputStream fis = new FileInputStream(file)) {
            // DSA: Sequential memory array buffer of fixed size 8192 bytes
            byte[] readBuffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int sequenceNumber = 0;
            long totalBytesRead = 0;

            if (fileSize == 0) {
                // Handle empty file: emit a single 0-byte chunk
                callback.onChunkReady(new byte[0], 0, 1, 0, 0);
                return;
            }

            while ((bytesRead = fis.read(readBuffer)) != -1) {
                // BUFFER REUSE FIX: Never pass readBuffer directly into async queue!
                // Always isolate and clone exact bytes read to prevent next read from overwriting
                byte[] isolatedChunk = Arrays.copyOf(readBuffer, bytesRead);

                totalBytesRead += bytesRead;
                callback.onChunkReady(isolatedChunk, sequenceNumber++, totalChunks, totalBytesRead, fileSize);
            }
        }
    }

    /**
     * Sequentially writes an incoming 8KB memory chunk into a temporary staging file
     * (<destinationFile>.connecta.tmp).
     * 
     * @param tempFile The staging temporary file on disk.
     * @param chunkData The 8KB byte array chunk to append.
     * @param isFirstChunk If true, truncates/overwrites any stale prior temporary version.
     * @throws IOException If disk write fails.
     */
    public void writeChunkToTempFile(File tempFile, byte[] chunkData, boolean isFirstChunk) throws IOException {
        if (tempFile == null) {
            return;
        }

        File parentDir = tempFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Open in overwrite mode for chunk 0, append mode for subsequent chunks
        try (FileOutputStream fos = new FileOutputStream(tempFile, !isFirstChunk)) {
            if (chunkData != null && chunkData.length > 0) {
                fos.write(chunkData);
            }
            fos.flush();
        }
    }

    /**
     * Finalizes file reassembly:
     * 1. Verifies the cryptographic MD5 checksum of the temporary staging file.
     * 2. Atomically replaces/renames the staging file to the final destination file.
     * 3. Cleans up temporary resources upon any mismatch.
     * 
     * @param tempFile The staging temporary file.
     * @param finalDestFile The target destination file.
     * @param expectedMD5 The MD5 checksum sent in the FILE_COMPLETE packet.
     * @param syncManager The SyncStateManager instance to pre-record the hash.
     * @param relativePath Relative path of the file from shared root.
     * @return true if checksum verified and file committed atomically, false otherwise.
     */
    public boolean finalizeReassembly(File tempFile, File finalDestFile, String expectedMD5, 
                                      SyncStateManager syncManager, String relativePath) {
        if (tempFile == null || !tempFile.exists() || finalDestFile == null) {
            return false;
        }

        try {
            // 1. Calculate MD5 checksum of assembled temporary staging file
            String actualMD5 = syncManager.calculateChecksum(tempFile);
            if (actualMD5 == null || (expectedMD5 != null && !expectedMD5.isEmpty() && !actualMD5.equalsIgnoreCase(expectedMD5))) {
                System.err.println("Integrity Check Failed for: " + relativePath + " (Expected: " + expectedMD5 + ", Got: " + actualMD5 + ")");
                tempFile.delete(); // Purge corrupted temporary file
                return false;
            }

            // 2. Pre-record into SyncStateManager BEFORE atomic rename
            // This ensures local FolderWatcherDaemon immediately recognizes file as synced
            syncManager.recordSyncedState(relativePath, actualMD5);

            // 3. Ensure destination parent directory exists
            File parent = finalDestFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            // 4. Atomically commit staging file to destination
            try {
                Files.move(tempFile.toPath(), finalDestFile.toPath(), 
                           StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception crossFsEx) {
                // Fallback to standard replace if atomic move across filesystems is unsupported
                Files.move(tempFile.toPath(), finalDestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        } catch (Exception e) {
            System.err.println("Failed to finalize file reassembly: " + e.getMessage());
            tempFile.delete();
            return false;
        }
    }

    /**
     * Calculates total chunks required for a file of given byte length.
     */
    public static int calculateTotalChunks(long fileSize) {
        if (fileSize <= 0) return 1;
        return (int) Math.ceil((double) fileSize / CHUNK_SIZE);
    }
}
