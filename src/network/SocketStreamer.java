package network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

/**
 * SocketStreamer Network Transmission Engine
 * 
 * Manages bi-directional, asynchronous streaming of file chunks and protocol headers
 * across a connected TCP peer socket.
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. FIFO Queue (Queue<FilePacket> implemented via LinkedList):
 *    - Structure: A thread-safe First-In-First-Out (FIFO) buffer that stores outgoing packets.
 *    - Purpose: Decouples file chunking and UI interaction from network transmission. When large
 *      files are read and chunked, packets are enqueued into the FIFO buffer without blocking the
 *      caller. The dedicated sender thread de-queues packets strictly in order.
 *    - Algorithmic Complexity:
 *      * Enqueue (outgoingQueue.offer): O(1) constant time insertion at tail of doubly-linked list.
 *      * Dequeue (outgoingQueue.poll):  O(1) constant time removal from head of doubly-linked list.
 * 
 * 2. Sequential Memory Arrays (byte[]):
 *    - Chunks of 8192 bytes (8KB) are transferred as fixed-length byte arrays.
 *    - Provides high spatial locality and eliminates memory fragmentation while streaming.
 * =========================================================================================
 */
public class SocketStreamer {

    private final Socket socket;
    private final DataInputStream inStream;
    private final DataOutputStream outStream;

    // DSA IMPLEMENTATION: FIFO Queue for outgoing network packets
    private final Queue<FilePacket> outgoingQueue = new LinkedList<>();

    private volatile boolean isRunning = true;
    private Thread senderThread;
    private Thread listenerThread;

    private PacketListener packetListener;
    private StreamerStatusListener statusListener;

    /**
     * Interface to receive incoming FilePackets from the network.
     */
    public interface PacketListener {
        void onPacketReceived(FilePacket packet);
    }

    /**
     * Interface to receive streamer lifecycle and progress events.
     */
    public interface StreamerStatusListener {
        void onPacketSent(FilePacket packet);
        void onDisconnected(String reason);
    }

    /**
     * Initializes the SocketStreamer with buffered streams for optimized throughput.
     */
    public SocketStreamer(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setTcpNoDelay(true); // Disable Nagle's algorithm for instant chunk transmission
        this.inStream = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 65536));
        this.outStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
    }

    public void setPacketListener(PacketListener listener) {
        this.packetListener = listener;
    }

    public void setStatusListener(StreamerStatusListener listener) {
        this.statusListener = listener;
    }

    /**
     * Enqueues a FilePacket into the FIFO Queue in a thread-safe manner.
     * Wakes up the background sender thread if it is waiting for data.
     * 
     * DSA Complexity: O(1) time complexity.
     */
    public synchronized void queuePacket(FilePacket packet) {
        if (!isRunning || packet == null) return;
        outgoingQueue.offer(packet); // Enqueue at tail (FIFO)
        notifyAll(); // Wake up worker thread
    }

    /**
     * Returns the current number of packets pending in the outgoing FIFO queue.
     */
    public synchronized int getQueueSize() {
        return outgoingQueue.size();
    }

    /**
     * Spawns the dedicated background threads for sending (FIFO) and receiving.
     */
    public void start() {
        startSendingThread();
        startListeningThread();
    }

    /**
     * Worker Thread: Continuously drains the FIFO Queue and transmits packets over TCP.
     */
    private void startSendingThread() {
        senderThread = new Thread(() -> {
            while (isRunning) {
                FilePacket packet = null;

                synchronized (this) {
                    while (outgoingQueue.isEmpty() && isRunning) {
                        try {
                            wait(); // Conserve CPU cycles when queue is empty
                        } catch (InterruptedException e) {
                            if (!isRunning) return;
                        }
                    }

                    if (!isRunning) break;
                    packet = outgoingQueue.poll(); // FIFO Dequeue from head: O(1)
                }

                if (packet != null) {
                    try {
                        packet.writeToStream(outStream);
                        if (statusListener != null) {
                            statusListener.onPacketSent(packet);
                        }
                    } catch (IOException e) {
                        handleDisconnect("Network write error: " + e.getMessage());
                        break;
                    }
                }
            }
        }, "Connecta-SocketSender");
        senderThread.setDaemon(true);
        senderThread.start();
    }

    /**
     * Worker Thread: Continuously listens for incoming binary packets on the TCP socket.
     */
    private void startListeningThread() {
        listenerThread = new Thread(() -> {
            while (isRunning) {
                try {
                    FilePacket packet = FilePacket.readFromStream(inStream);
                    if (packet != null && packetListener != null) {
                        packetListener.onPacketReceived(packet);
                    }
                } catch (IOException e) {
                    handleDisconnect("Connection closed by peer: " + e.getMessage());
                    break;
                }
            }
        }, "Connecta-SocketReceiver");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Handles connection termination safely.
     */
    private synchronized void handleDisconnect(String reason) {
        if (!isRunning) return;
        isRunning = false;
        notifyAll();

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        if (statusListener != null) {
            statusListener.onDisconnected(reason);
        }
    }

    /**
     * Shuts down streamer resources, stopping background threads and closing streams.
     */
    public synchronized void close() {
        isRunning = false;
        notifyAll();

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public boolean isRunning() {
        return isRunning && socket != null && socket.isConnected() && !socket.isClosed();
    }
}
