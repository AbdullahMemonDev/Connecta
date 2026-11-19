package filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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
 *    - Memory Complexity: O(1) auxiliary memory footprint. Instead of loading an entire 500MB
 *      file into heap memory (which causes OutOfMemoryError and massive GC pauses), memory
 *      consumption is bounded strictly to 8KB per chunk operation.
 *    - Hardware Acceleration: Sequential 8KB arrays leverage CPU L1/L2 cache prefetching,
 *      ensuring optimal spatial and temporal locality during disk read/write cycles.
 * =========================================================================================
 */
public class FileChunkManager {

    /**
     * Standard fixed chunk size: 8192 bytes (8KB).
     * Optimal for TCP window sizes and file system block boundaries (typically 4KB/8KB).
     */
    public static final int CHUNK_SIZE = 8192;

    /**
     * Functional callback interface triggered when each 8KB sequential memory chunk is read.
     */
    public interface ChunkCallback {
        void onChunkReady(byte[] chunk, int sequenceNumber, int totalChunks, long totalBytesRead, long fileSize);
    }

    /**
     * Splits a target file into fixed 8192-byte array chunks, streaming each to the callback.
     * 
     * @param file The file on disk to split.
     * @param callback Callback delivering each sequential byte[] array chunk.
     * @throws IOException If disk read errors occur.
     */
    public void splitFileIntoChunks(File file, ChunkCallback callback) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Invalid file reference for chunking.");
        }

        long fileSize = file.length();
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        if (totalChunks == 0) {
            totalChunks = 1; // Zero-byte file edge case
        }

        // Try-with-resources ensures file handles are cleanly closed
        try (FileInputStream fis = new FileInputStream(file)) {
            // DSA: Sequential memory array buffer of fixed size 8192 bytes
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int sequenceNumber = 0;
            long totalBytesRead = 0;

            if (fileSize == 0) {
                // Handle empty file: emit a single 0-byte chunk
                callback.onChunkReady(new byte[0], 0, 1, 0, 0);
                return;
            }

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk;
                if (bytesRead == CHUNK_SIZE) {
                    // Full 8KB block: copy directly to ensure thread safety in outgoing FIFO queue
                    chunk = new byte[CHUNK_SIZE];
                    System.arraycopy(buffer, 0, chunk, 0, CHUNK_SIZE);
                } else {
                    // Fractional final chunk: trim array to exact remaining length
                    chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                }

                totalBytesRead += bytesRead;
                callback.onChunkReady(chunk, sequenceNumber++, totalChunks, totalBytesRead, fileSize);
            }
        }
    }

    /**
     * Sequentially appends an incoming 8KB memory chunk into the target destination file.
     * Automatically constructs missing directory structures in nested folders.
     * 
     * @param destinationFile The target destination file path on disk.
     * @param chunkData The 8KB byte array chunk to append.
     * @param isFirstChunk If true, truncates/overwrites any stale prior version.
     * @throws IOException If disk write fails.
     */
    public void assembleChunkAndWrite(File destinationFile, byte[] chunkData, boolean isFirstChunk) throws IOException {
        if (destinationFile == null || chunkData == null) {
            return;
        }

        // Ensure parent directory hierarchy exists
        File parentDir = destinationFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // If it's the very first chunk of a file, overwrite (append = false); otherwise append = true
        try (FileOutputStream fos = new FileOutputStream(destinationFile, !isFirstChunk)) {
            if (chunkData.length > 0) {
                fos.write(chunkData);
                fos.flush();
            }
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
