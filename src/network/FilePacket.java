package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * FilePacket Data Structure
 * 
 * Represents the discrete units of network communication passed through the Bounded FIFO Queue
 * in SocketStreamer. Encapsulates control headers, file metadata, sequential 8KB
 * binary memory chunks, handshakes, and keep-alive heartbeats.
 * 
 * =========================================================================
 * BINARY FRAMING PROTOCOL SPECIFICATION:
 * [PacketType (1 byte)]
 * [FileName / RelativePath (UTF string)]
 * [TotalFileSize (8 bytes)]
 * [ChunkIndex / SequenceNumber (4 bytes)]
 * [TotalChunks (4 bytes)]
 * [Checksum (UTF string - empty unless FILE_COMPLETE or FILE_START)]
 * [ChunkLength (4 bytes)]
 * [ChunkBytes (ChunkLength bytes)]
 * =========================================================================
 */
public class FilePacket {

    /**
     * Enumeration defining all packet operational types in the Connecta protocol.
     */
    public enum PacketType {
        HANDSHAKE,       // Peer authentication and identity exchange (ID, Name, Port)
        HEARTBEAT,       // Peer presence and connection health keep-alive ping
        FILE_START,      // Initial file metadata (relative path, file size, total chunks, checksum)
        FILE_CHUNK,      // Sequential 8KB data payload buffer
        FILE_COMPLETE,   // EOF and cryptographic integrity verification (MD5 Checksum)
        FILE_DELETE,     // Remote deletion synchronization event
        UNDO_ACTION,     // Rollback / Undo synchronization command
        MANIFEST_SYNC    // Catch-up synchronization file manifest exchange
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
     * Creates a HANDSHAKE packet exchanging device identity upon TCP connection.
     */
    public static FilePacket createHandshakePacket(String connectaId, String deviceName, int listeningPort, String version) {
        String payload = connectaId + "|" + deviceName + "|" + listeningPort + "|" + version;
        return new FilePacket(PacketType.HANDSHAKE, payload, 0, 0, 0, "", null);
    }

    /**
     * Creates a HEARTBEAT ping packet to maintain active TCP connection state.
     */
    public static FilePacket createHeartbeat() {
        return new FilePacket(PacketType.HEARTBEAT, "", 0, 0, 0, null, null);
    }

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
     * Creates a FILE_COMPLETE packet signaling EOF and final MD5 checksum verification.
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
     * Creates a MANIFEST_SYNC packet containing serialized file paths and checksums for catch-up sync.
     */
    public static FilePacket createManifestPacket(String manifestData) {
        byte[] bytes = manifestData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new FilePacket(PacketType.MANIFEST_SYNC, "MANIFEST", bytes.length, 0, 1, "", bytes);
    }

    // ================= STRICT BINARY FRAMING OVER TCP =================

    /**
     * Serializes this packet directly to the output stream.
     * Guaranteed to be synchronized by the caller or within this method to avoid interleaving.
     */
    public void writeToStream(DataOutputStream out) throws IOException {
        synchronized (out) {
            out.writeByte(type.ordinal());
            out.writeUTF(relativePath != null ? relativePath : "");
            out.writeLong(fileSize);
            out.writeInt(sequenceNumber);
            out.writeInt(totalChunks);
            out.writeUTF(checksum != null ? checksum : "");

            if (chunkData != null && chunkData.length > 0) {
                out.writeInt(chunkData.length);
                out.write(chunkData);
            } else {
                out.writeInt(0);
            }
            out.flush();
        }
    }

    /**
     * Deserializes a FilePacket from the incoming binary TCP stream.
     */
    public static FilePacket readFromStream(DataInputStream in) throws IOException {
        byte typeOrdinal;
        try {
            typeOrdinal = in.readByte();
        } catch (EOFException eof) {
            throw eof; // Propagate EOF cleanly
        }

        if (typeOrdinal < 0 || typeOrdinal >= PacketType.values().length) {
            throw new IOException("Malformed packet: invalid type ordinal " + typeOrdinal);
        }

        PacketType type = PacketType.values()[typeOrdinal];
        String relativePath = in.readUTF();
        long fileSize = in.readLong();
        int sequenceNumber = in.readInt();
        int totalChunks = in.readInt();
        String checksum = in.readUTF();

        int dataLength = in.readInt();
        if (dataLength < 0 || dataLength > 1048576) { // 1MB sanity limit per frame
            throw new IOException("Malformed packet frame: invalid dataLength " + dataLength);
        }

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
