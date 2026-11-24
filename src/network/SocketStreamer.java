package network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SocketStreamer Network Transmission Engine
 * 
 * Manages bi-directional, asynchronous streaming of file chunks, protocol headers,
 * and lightweight keep-alive heartbeats across a connected TCP peer socket.
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. Bounded FIFO Queue (ArrayBlockingQueue<FilePacket> with fixed capacity 64):
 *    - Structure: A thread-safe bounded circular array buffer implementing the FIFO queue discipline.
 *    - Purpose (Automatic Backpressure): Eliminates Java Heap OutOfMemoryError. When FileChunkManager
 *      reads large files off fast storage (e.g., NVMe SSD at 500 MB/s), the producer thread is
 *      automatically throttled via outgoingQueue.put(packet) once 64 chunks (512 KB) are buffered.
 *      The file reader pauses until the TCP network sender thread consumes (outgoingQueue.take())
 *      and transmits chunks over the network.
 *    - Memory Footprint: Strictly O(1) constant auxiliary memory footprint (bounded at ~512 KB),
 *      guaranteeing identical memory stability whether streaming 1 MB, 500 MB, or 10 GB files.
 *    - Algorithmic Complexity:
 *      * Enqueue (outgoingQueue.put): O(1) amortized constant time circular array insertion.
 *      * Dequeue (outgoingQueue.take): O(1) amortized constant time circular array removal.
 * 
 * 2. Sequential Memory Arrays (byte[]):
 *    - Chunks of 8192 bytes (8KB) are transferred as fixed-length byte arrays.
 *    - Provides high spatial locality and eliminates memory fragmentation while streaming.
 * =========================================================================================
 */
public class SocketStreamer {

    public static final int QUEUE_CAPACITY = 64; // Strict bounded capacity enforcing backpressure

    private final Socket socket;
    private final DataInputStream inStream;
    private final DataOutputStream outStream;

    // DSA IMPLEMENTATION: Bounded FIFO Queue enforcing backpressure
    private final BlockingQueue<FilePacket> outgoingQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private volatile boolean isRunning = true;
    private Thread senderThread;
    private Thread listenerThread;

    private PacketListener packetListener;
    private StreamerStatusListener statusListener;
    private RateListener rateListener;

    // Real-Time Bandwidth & Heartbeat Metrics
    private final AtomicLong bytesSentInInterval = new AtomicLong(0);
    private final AtomicLong bytesReceivedInInterval = new AtomicLong(0);
    private volatile long lastDataReceivedTimestamp = System.currentTimeMillis();
    private volatile long lastDataSentTimestamp = System.currentTimeMillis();

    private Timer heartbeatAndRateTimer;

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
     * Interface for live bandwidth telemetries.
     */
    public interface RateListener {
        void onRatesUpdated(String uploadRate, String downloadRate, long uploadBps, long downloadBps);
    }

    /**
     * Initializes the SocketStreamer with buffered streams for optimized throughput.
     */
    public SocketStreamer(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setTcpNoDelay(true); // Disable Nagle's algorithm for instant chunk transmission
        this.socket.setSoTimeout(15000); // 15-second socket read timeout to prevent indefinite hangs
        this.inStream = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 65536));
        this.outStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
    }

    public void setPacketListener(PacketListener listener) {
        this.packetListener = listener;
    }

    public void setStatusListener(StreamerStatusListener listener) {
        this.statusListener = listener;
    }

    public void setRateListener(RateListener listener) {
        this.rateListener = listener;
    }

    /**
     * Enqueues a FilePacket into the Bounded FIFO Queue.
     * Enforces backpressure: blocks caller if queue capacity (64) is reached.
     * 
     * DSA Complexity: O(1) time complexity.
     */
    public void queuePacket(FilePacket packet) throws InterruptedException {
        if (!isRunning || packet == null) return;
        outgoingQueue.put(packet); // Blocks producer when bounded queue is full
    }

    /**
     * Non-blocking or timed enqueue for heartbeats and control packets.
     */
    public boolean offerPacket(FilePacket packet, long timeoutMs) {
        if (!isRunning || packet == null) return false;
        try {
            return outgoingQueue.offer(packet, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Returns the current number of packets pending in the bounded outgoing FIFO queue.
     */
    public int getQueueSize() {
        return outgoingQueue.size();
    }

    /**
     * Spawns the dedicated background threads for sending (FIFO) and receiving,
     * and starts the 1-second Heartbeat / Bandwidth telemetry timer.
     */
    public void start() {
        startSendingThread();
        startListeningThread();
        startHeartbeatAndRateTimer();
    }

    /**
     * Periodic 1-second timer:
     * 1. Sends Heartbeat ping if channel has been idle for >= 3 seconds.
     * 2. Checks for Heartbeat timeout (> 12 seconds with zero incoming packets).
     * 3. Calculates and publishes real-time Upload and Download rates.
     */
    private void startHeartbeatAndRateTimer() {
        heartbeatAndRateTimer = new Timer("Connecta-HeartbeatRateTimer", true);
        heartbeatAndRateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isRunning) {
                    cancel();
                    return;
                }

                long now = System.currentTimeMillis();

                // 1. Send Heartbeat if idle for >= 3 seconds
                if (now - lastDataSentTimestamp >= 3000) {
                    offerPacket(FilePacket.createHeartbeat(), 200);
                }

                // 2. Check for Heartbeat timeout (12 seconds without any data)
                if (now - lastDataReceivedTimestamp > 12000) {
                    handleDisconnect("Heartbeat keep-alive timed out (Peer unreachable)");
                    return;
                }

                // 3. Compute live rates
                long upBytes = bytesSentInInterval.getAndSet(0);
                long downBytes = bytesReceivedInInterval.getAndSet(0);

                if (rateListener != null) {
                    rateListener.onRatesUpdated(formatSpeed(upBytes), formatSpeed(downBytes), upBytes, downBytes);
                }
            }
        }, 1000, 1000);
    }

    /**
     * Worker Thread: Continuously drains the Bounded FIFO Queue and transmits packets over TCP.
     */
    private void startSendingThread() {
        senderThread = new Thread(() -> {
            while (isRunning) {
                FilePacket packet = null;
                try {
                    // DSA FIFO Dequeue: Blocks until packet is available in bounded queue
                    packet = outgoingQueue.poll(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (!isRunning) break;
                    continue;
                }

                if (packet != null && isRunning) {
                    try {
                        int chunkLen = packet.getChunkData() != null ? packet.getChunkData().length : 0;

                        // Synchronize on outStream to guarantee strict packet framing without interleaving
                        synchronized (outStream) {
                            packet.writeToStream(outStream);
                        }

                        lastDataSentTimestamp = System.currentTimeMillis();
                        bytesSentInInterval.addAndGet(chunkLen + 32); // Approximate header overhead

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
                    if (packet != null) {
                        lastDataReceivedTimestamp = System.currentTimeMillis();
                        int chunkLen = packet.getChunkData() != null ? packet.getChunkData().length : 0;
                        bytesReceivedInInterval.addAndGet(chunkLen + 32);

                        if (packetListener != null) {
                            packetListener.onPacketReceived(packet);
                        }
                    }
                } catch (SocketTimeoutException timeout) {
                    // Read timeout (15s): heartbeat watchdog handles disconnection if peer is truly silent
                    if (!isRunning) break;
                } catch (EOFException | SocketException eof) {
                    // Graceful peer disconnection
                    handleDisconnect("Peer disconnected cleanly (" + eof.getMessage() + ")");
                    break;
                } catch (IOException e) {
                    if (isRunning) {
                        handleDisconnect("Stream reading error: " + e.getMessage());
                    }
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

        outgoingQueue.clear();

        if (heartbeatAndRateTimer != null) {
            heartbeatAndRateTimer.cancel();
            heartbeatAndRateTimer = null;
        }

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
        handleDisconnect("Manual stream shutdown");
    }

    public boolean isRunning() {
        return isRunning && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) return "0 B/s";
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        if (bytesPerSec < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSec / 1024.0);
        return String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0));
    }
}
