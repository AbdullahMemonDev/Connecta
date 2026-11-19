package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * FilePacket Data Structure
 * 
 * Represents the discrete units of network communication passed through the FIFO Queue
 * in SocketStreamer. Encapsulates control headers, file metadata, and sequential 8KB
 * binary memory chunks.
 * 
 * =========================================================================
 * DATA STRUCTURE SPECIFICATION:
 * - Serves as the element type for Queue<FilePacket> (FIFO Queue / LinkedList).
 * - Implements self-contained binary serialization over raw TCP streams.
 * =========================================================================
 */
public class FilePacket {

    /**
     * Enumeration defining all packet operational types in the Connecta protocol.
     */
    public enum PacketType {
        FILE_START,      // Initial file metadata (relative path, file size, total chunks, checksum)
        FILE_CHUNK,      // Sequential 8KB data payload buffer
        FILE_COMPLETE,   // Completion confirmation and integrity verification
        FILE_DELETE,     // Remote deletion synchronization event
        UNDO_ACTION,     // Rollback / Undo synchronization command
        HEARTBEAT        // Peer presence and connection health keep-alive
    }

    private final PacketType type;
    private final String relativePath;
    private final long fileSize;
    private final int sequenceNumber;
    private final int totalChunks;
    private final String checksum;
    private final byte[] chunkData;

    /**
     * Full internal constructor.
     */
    public FilePacket(PacketType type, String relativePath, long fileSize, 
                      int sequenceNumber, int totalChunks, String checksum, byte[] chunkData) {
        this.type = type;
        this.relativePath = relativePath != null ? relativePath : "";
        this.fileSize = fileSize;
        this.sequenceNumber = sequenceNumber;
        this.totalChunks = totalChunks;
        this.checksum = checksum != null ? checksum : "";
        this.chunkData = chunkData;
    }

    // ================= FACTORY BUILDER METHODS =================

    /**
     * Creates a FILE_START packet transmitting metadata before chunk transmission begins.
     */
    public static FilePacket createStartPacket(String relativePath, long fileSize, int totalChunks, String checksum) {
        return new FilePacket(PacketType.FILE_START, relativePath, fileSize, 0, totalChunks, checksum, null);
    }

    /**
     * Creates a FILE_CHUNK packet holding an 8KB sequential memory array buffer.
     */
    public static FilePacket createChunkPacket(String relativePath, int sequenceNumber, int totalChunks, byte[] chunkData) {
        return new FilePacket(PacketType.FILE_CHUNK, relativePath, 0, sequenceNumber, totalChunks, null, chunkData);
    }

    /**
     * Creates a FILE_COMPLETE packet signaling that all chunks have been transmitted.
     */
    public static FilePacket createCompletePacket(String relativePath, String checksum) {
        return new FilePacket(PacketType.FILE_COMPLETE, relativePath, 0, 0, 0, checksum, null);
    }

    /**
     * Creates a FILE_DELETE packet notifying the peer to remove a deleted file.
     */
    public static FilePacket createDeletePacket(String relativePath) {
        return new FilePacket(PacketType.FILE_DELETE, relativePath, 0, 0, 0, null, null);
    }

    /**
     * Creates an UNDO_ACTION packet requesting peer rollback of the last transferred file.
     */
    public static FilePacket createUndoPacket(String relativePath) {
        return new FilePacket(PacketType.UNDO_ACTION, relativePath, 0, 0, 0, null, null);
    }

    /**
     * Creates a HEARTBEAT ping packet.
     */
    public static FilePacket createHeartbeat() {
        return new FilePacket(PacketType.HEARTBEAT, "", 0, 0, 0, null, null);
    }

    // ================= BINARY SERIALIZATION OVER TCP =================

    /**
     * Serializes this packet directly to the output stream.
     * Uses optimized binary writes without heavy Java object serialization overhead.
     */
    public void writeToStream(DataOutputStream out) throws IOException {
        out.writeByte(type.ordinal());
        out.writeUTF(relativePath);
        out.writeLong(fileSize);
        out.writeInt(sequenceNumber);
        out.writeInt(totalChunks);
        out.writeUTF(checksum);

        if (chunkData != null && chunkData.length > 0) {
            out.writeInt(chunkData.length);
            out.write(chunkData);
        } else {
            out.writeInt(0);
        }
        out.flush();
    }

    /**
     * Deserializes a FilePacket from the incoming binary TCP stream.
     */
    public static FilePacket readFromStream(DataInputStream in) throws IOException {
        byte typeOrdinal = in.readByte();
        if (typeOrdinal < 0 || typeOrdinal >= PacketType.values().length) {
            throw new IOException("Invalid packet type ordinal: " + typeOrdinal);
        }
        PacketType type = PacketType.values()[typeOrdinal];
        String relativePath = in.readUTF();
        long fileSize = in.readLong();
        int sequenceNumber = in.readInt();
        int totalChunks = in.readInt();
        String checksum = in.readUTF();

        int dataLength = in.readInt();
        byte[] chunkData = null;
        if (dataLength > 0) {
            chunkData = new byte[dataLength];
            in.readFully(chunkData, 0, dataLength);
        }

        return new FilePacket(type, relativePath, fileSize, sequenceNumber, totalChunks, checksum, chunkData);
    }

    // ================= GETTERS =================

    public PacketType getType() {
        return type;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public String getChecksum() {
        return checksum;
    }

    public byte[] getChunkData() {
        return chunkData;
    }
}
