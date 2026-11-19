package core;

import filesystem.FileChunkManager;
import filesystem.FolderWatcherDaemon;
import filesystem.SyncStateManager;
import network.FilePacket;
import network.IDResolver;
import network.NetworkManager;
import network.SocketStreamer;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AppManager (Central Application Orchestrator)
 * 
 * Bridges GUI, Networking, and File System modules into a coherent synchronization engine.
 * Coordinates real-time peer pairing, automated folder monitoring, packet streaming,
 * and UI feedback loops.
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. Stack (Stack<String> - LIFO Data Structure):
 *    - Structure: A thread-safe Last-In-First-Out (LIFO) stack storing the relative paths
 *      of recently synchronized files.
 *    - Purpose: Enables the user to view recent synchronization activities in reverse-chronological
 *      order and undo/rollback the most recently synchronized file state.
 *    - Algorithmic Complexity:
 *      * Push: O(1) constant time to push a newly transferred file onto the top of the stack.
 *      * Pop:  O(1) constant time to retrieve and remove the most recently transferred file.
 * 
 * 2. Hash Table (SyncStateManager) & FIFO Queue (SocketStreamer) Integration:
 *    - Coordinates O(1) loop prevention lookup before enqueuing chunks into the FIFO Queue.
 * =========================================================================================
 */
public class AppManager {

    private static AppManager instance;

    // Core Subsystems
    private final NetworkManager networkManager;
    private final SyncStateManager syncStateManager;
    private final FileChunkManager chunkManager;
    private FolderWatcherDaemon folderWatcher;
    private Thread watcherThread;

    // Configuration
    private String sharedFolderPath;
    private String myConnectaID = "Resolving...";
    private String connectedPeerAddress = null;

    // DSA IMPLEMENTATION: LIFO Stack for tracking recent sync history and undo
    private final Stack<String> transferHistoryStack = new Stack<>();

    // Real-Time Transfer Metrics
    private final AtomicInteger filesSentCount = new AtomicInteger(0);
    private final AtomicInteger filesReceivedCount = new AtomicInteger(0);
    private final AtomicInteger totalTransfersCount = new AtomicInteger(0);

    // Active incoming file assembly tracking
    private String currentIncomingFile = null;
    private long currentIncomingFileSize = 0;
    private int currentIncomingTotalChunks = 0;

    // GUI Observer Listeners
    private final CopyOnWriteArrayList<AppEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Observer interface for broadcasting engine events to Swing UI components.
     */
    public interface AppEventListener {
        void onServerReady(String myID, int port);
        void onPeerConnected(String peerIP);
        void onPeerDisconnected(String reason);
        void onConnectionError(String error);
        void onTransferProgress(String fileName, int percent, String statusText, boolean isOutgoing);
        void onTransferComplete(String fileName, boolean isOutgoing);
        void onLog(String fileName, String message);
        void onStatsUpdated(int sent, int received, int total);
        void onSharedFolderChanged(String newPath);
    }

    /**
     * Singleton instance accessor.
     */
    public static synchronized AppManager getInstance() {
        if (instance == null) {
            instance = new AppManager();
        }
        return instance;
    }

    private AppManager() {
        this.networkManager = new NetworkManager();
        this.syncStateManager = new SyncStateManager();
        this.chunkManager = new FileChunkManager();

        // Default shared folder location: <user.home>/ConnectaSync
        this.sharedFolderPath = System.getProperty("user.home") + File.separator + "ConnectaSync";
        File dir = new File(this.sharedFolderPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        setupNetworkListener();
    }

    public void addListener(AppEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AppEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Initializes background networking and resolves the unique Connecta ID.
     */
    public void start() {
        this.myConnectaID = IDResolver.generateIDFromIP();
        networkManager.startServer();
    }

    private void setupNetworkListener() {
        networkManager.setNetworkListener(new NetworkManager.NetworkListener() {
            @Override
            public void onServerStarted(int port, String myConnectaID) {
                AppManager.this.myConnectaID = myConnectaID;
                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onServerReady(myConnectaID, port);
                    }
                });
            }

            @Override
            public void onPeerConnected(String peerIP, SocketStreamer streamer) {
                connectedPeerAddress = peerIP;
                setupStreamerListeners(streamer);
                startFolderWatcher();

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onPeerConnected(peerIP);
                        l.onLog("Connection", "Paired with device at " + peerIP);
                    }
                });
            }

            @Override
            public void onPeerDisconnected(String reason) {
                connectedPeerAddress = null;
                stopFolderWatcher();

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onPeerDisconnected(reason);
                        l.onLog("Connection", "Disconnected: " + reason);
                    }
                });
            }

            @Override
            public void onConnectionFailed(String reason) {
                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onConnectionError(reason);
                    }
                });
            }
        });
    }

    /**
     * Configures the packet consumer for incoming network data packets.
     */
    private void setupStreamerListeners(SocketStreamer streamer) {
        streamer.setPacketListener(packet -> {
            switch (packet.getType()) {
                case FILE_START:
                    handleIncomingFileStart(packet);
                    break;
                case FILE_CHUNK:
                    handleIncomingFileChunk(packet);
                    break;
                case FILE_COMPLETE:
                    handleIncomingFileComplete(packet);
                    break;
                case FILE_DELETE:
                    handleIncomingFileDelete(packet);
                    break;
                case UNDO_ACTION:
                    handleIncomingUndo(packet);
                    break;
                case HEARTBEAT:
                    // Keep-alive received
                    break;
            }
        });
    }

    // ================= INCOMING PACKET HANDLERS =================

    private void handleIncomingFileStart(FilePacket packet) {
        this.currentIncomingFile = packet.getRelativePath();
        this.currentIncomingFileSize = packet.getFileSize();
        this.currentIncomingTotalChunks = packet.getTotalChunks();

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onTransferProgress(currentIncomingFile, 0, "Receiving metadata...", false);
                l.onLog(currentIncomingFile, "Incoming transfer initiated");
            }
        });
    }

    private void handleIncomingFileChunk(FilePacket packet) {
        String relPath = packet.getRelativePath();
        File destFile = new File(sharedFolderPath, relPath);

        try {
            boolean isFirstChunk = (packet.getSequenceNumber() == 0);
            chunkManager.assembleChunkAndWrite(destFile, packet.getChunkData(), isFirstChunk);

            int total = packet.getTotalChunks();
            int current = packet.getSequenceNumber() + 1;
            int percent = total > 0 ? (int) (((double) current / total) * 100) : 100;

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onTransferProgress(relPath, percent, "Receiving chunk " + current + " of " + total, false);
                }
            });
        } catch (IOException e) {
            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog(relPath, "Write Error: " + e.getMessage());
                }
            });
        }
    }

    private void handleIncomingFileComplete(FilePacket packet) {
        String relPath = packet.getRelativePath();
        File receivedFile = new File(sharedFolderPath, relPath);

        // Verify checksum and update SyncStateManager to prevent echo loops
        String actualChecksum = syncStateManager.calculateChecksum(receivedFile);
        if (actualChecksum != null) {
            // DSA: O(1) Hash Table insertion to prevent infinite ping-pong sync loop
            syncStateManager.recordSyncedState(relPath, actualChecksum);
        }

        // DSA: O(1) Stack push to record file in LIFO history
        synchronized (transferHistoryStack) {
            transferHistoryStack.push(relPath);
        }

        int rec = filesReceivedCount.incrementAndGet();
        int total = totalTransfersCount.incrementAndGet();

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onTransferProgress(relPath, 100, "Received successfully", false);
                l.onTransferComplete(relPath, false);
                l.onLog(relPath, "Saved to shared directory");
                l.onStatsUpdated(filesSentCount.get(), rec, total);
            }
        });
    }

    private void handleIncomingFileDelete(FilePacket packet) {
        String relPath = packet.getRelativePath();
        File fileToDelete = new File(sharedFolderPath, relPath);
        if (fileToDelete.exists()) {
            fileToDelete.delete();
            syncStateManager.removeHistory(relPath);

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog(relPath, "Deleted remotely by peer");
                }
            });
        }
    }

    private void handleIncomingUndo(FilePacket packet) {
        String relPath = packet.getRelativePath();
        File fileToUndo = new File(sharedFolderPath, relPath);
        if (fileToUndo.exists()) {
            fileToUndo.delete();
            syncStateManager.removeHistory(relPath);
        }

        synchronized (transferHistoryStack) {
            transferHistoryStack.remove(relPath);
        }

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onLog(relPath, "Rollback / Undo applied by peer");
            }
        });
    }

    // ================= OUTGOING FILE SYNC =================

    /**
     * Synchronizes a local file to the peer.
     * Uses DSA Hash Table for O(1) loop prevention and FIFO Queue for sequential streaming.
     */
    public void syncFileToPeer(File file, String relativePath) {
        if (!networkManager.isConnected() || file == null || !file.exists() || !file.isFile()) {
            return;
        }

        SocketStreamer streamer = networkManager.getStreamer();
        if (streamer == null || !streamer.isRunning()) {
            return;
        }

        new Thread(() -> {
            try {
                // 1. Calculate MD5 checksum
                String checksum = syncStateManager.calculateChecksum(file);
                if (checksum == null) return;

                // 2. DSA HASH TABLE CHECK: O(1) search complexity to prevent infinite sync loops
                if (syncStateManager.isAlreadySynced(relativePath, checksum)) {
                    return; // Already synchronized; skip to avoid infinite loop
                }

                // 3. Mark in Hash Table immediately
                syncStateManager.recordSyncedState(relativePath, checksum);

                long fileSize = file.length();
                int totalChunks = FileChunkManager.calculateTotalChunks(fileSize);

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onTransferProgress(file.getName(), 0, "Queuing file...", true);
                        l.onLog(file.getName(), "Starting upload (" + totalChunks + " chunks)");
                    }
                });

                // 4. Send FILE_START packet via FIFO Queue
                FilePacket startPacket = FilePacket.createStartPacket(relativePath, fileSize, totalChunks, checksum);
                streamer.queuePacket(startPacket); // DSA: FIFO Queue offer: O(1)

                // 5. Read file in sequential 8KB byte[] buffers and enqueue into FIFO Queue
                chunkManager.splitFileIntoChunks(file, (chunk, seq, total, bytesRead, totalBytes) -> {
                    FilePacket chunkPacket = FilePacket.createChunkPacket(relativePath, seq, total, chunk);
                    streamer.queuePacket(chunkPacket); // DSA: FIFO Queue buffering: O(1)

                    int percent = total > 0 ? (int) (((double) (seq + 1) / total) * 100) : 100;
                    dispatch(() -> {
                        for (AppEventListener l : listeners) {
                            l.onTransferProgress(file.getName(), percent, "Streaming chunk " + (seq + 1) + " of " + total, true);
                        }
                    });
                });

                // 6. Send FILE_COMPLETE packet
                FilePacket completePacket = FilePacket.createCompletePacket(relativePath, checksum);
                streamer.queuePacket(completePacket);

                // 7. DSA STACK: Push to LIFO transfer history stack: O(1)
                synchronized (transferHistoryStack) {
                    transferHistoryStack.push(relativePath);
                }

                int sent = filesSentCount.incrementAndGet();
                int total = totalTransfersCount.incrementAndGet();

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onTransferProgress(file.getName(), 100, "Sent successfully", true);
                        l.onTransferComplete(file.getName(), true);
                        l.onLog(file.getName(), "Transferred successfully");
                        l.onStatsUpdated(sent, filesReceivedCount.get(), total);
                    }
                });

            } catch (Exception e) {
                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onLog(file.getName(), "Transfer Error: " + e.getMessage());
                    }
                });
            }
        }, "Connecta-FileSender-" + file.getName()).start();
    }

    /**
     * Sends a file deletion notification to peer.
     */
    public void syncFileDeletion(String relativePath) {
        if (!networkManager.isConnected()) return;
        SocketStreamer streamer = networkManager.getStreamer();
        if (streamer != null && streamer.isRunning()) {
            syncStateManager.removeHistory(relativePath);
            streamer.queuePacket(FilePacket.createDeletePacket(relativePath));
            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog(relativePath, "Deletion synced to partner");
                }
            });
        }
    }

    // ================= DSA STACK: UNDO RECENT TRANSFER =================

    /**
     * DSA ALGORITHM: LIFO Stack Pop for Undo Operation
     * 
     * Pops the most recently transferred file from the top of the LIFO Stack,
     * removes it locally, and informs the peer device to roll back.
     * 
     * Complexity: O(1) time complexity.
     * 
     * @return The relative path of the undone file, or null if stack is empty.
     */
    public String undoRecentTransfer() {
        String lastTransferredFile;
        synchronized (transferHistoryStack) {
            if (transferHistoryStack.isEmpty()) {
                return null;
            }
            lastTransferredFile = transferHistoryStack.pop(); // DSA: LIFO Pop O(1)
        }

        // Delete local copy if present in shared directory
        File targetFile = new File(sharedFolderPath, lastTransferredFile);
        if (targetFile.exists()) {
            targetFile.delete();
        }

        // Clear from Hash Table
        syncStateManager.removeHistory(lastTransferredFile);

        // Notify peer via UNDO_ACTION packet
        if (networkManager.isConnected() && networkManager.getStreamer() != null) {
            networkManager.getStreamer().queuePacket(FilePacket.createUndoPacket(lastTransferredFile));
        }

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onLog(lastTransferredFile, "Undone / Rolled back (LIFO Stack pop)");
            }
        });

        return lastTransferredFile;
    }

    public synchronized Stack<String> getTransferHistoryStack() {
        return transferHistoryStack;
    }

    // ================= FOLDER WATCHER CONTROLS =================

    /**
     * Starts the FolderWatcherDaemon using N-ary Tree Depth-First Search (DFS).
     */
    private void startFolderWatcher() {
        stopFolderWatcher();

        try {
            folderWatcher = new FolderWatcherDaemon(sharedFolderPath, new FolderWatcherDaemon.EventListener() {
                @Override
                public void onFileEventDetected(File file, String relativePath, FolderWatcherDaemon.WatchKind kind) {
                    if (kind == FolderWatcherDaemon.WatchKind.CREATE || kind == FolderWatcherDaemon.WatchKind.MODIFY) {
                        syncFileToPeer(file, relativePath);
                    } else if (kind == FolderWatcherDaemon.WatchKind.DELETE) {
                        syncFileDeletion(relativePath);
                    }
                }

                @Override
                public void onInitialScanFileFound(File file, String relativePath) {
                    // Populate initial Hash Table state during initial DFS tree traversal
                    String hash = syncStateManager.calculateChecksum(file);
                    if (hash != null) {
                        syncStateManager.recordSyncedState(relativePath, hash);
                    }
                }
            });

            watcherThread = new Thread(folderWatcher, "Connecta-FolderWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog("FolderWatcher", "Monitoring N-ary directory tree: " + sharedFolderPath);
                }
            });
        } catch (Exception e) {
            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog("FolderWatcher", "Failed to initialize watcher: " + e.getMessage());
                }
            });
        }
    }

    private void stopFolderWatcher() {
        if (folderWatcher != null) {
            folderWatcher.stopWatching();
            folderWatcher = null;
        }
        if (watcherThread != null && watcherThread.isAlive()) {
            watcherThread.interrupt();
            watcherThread = null;
        }
    }

    /**
     * Changes the monitored shared folder path and re-initializes DFS tree indexing.
     */
    public void setSharedFolderPath(String newPath) {
        if (newPath == null || newPath.trim().isEmpty()) return;
        File dir = new File(newPath.trim());
        if (!dir.exists()) {
            dir.mkdirs();
        }

        this.sharedFolderPath = dir.getAbsolutePath();
        this.syncStateManager.clear();

        if (networkManager.isConnected()) {
            startFolderWatcher();
        }

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onSharedFolderChanged(this.sharedFolderPath);
                l.onLog("Shared Folder", "Updated path to: " + this.sharedFolderPath);
            }
        });
    }

    // ================= CONNECTION ACTIONS =================

    public void connectToPeer(String partnerID) {
        networkManager.connectToPeer(partnerID);
    }

    public void disconnectPeer() {
        networkManager.disconnect();
    }

    public void shutdown() {
        stopFolderWatcher();
        networkManager.shutdown();
    }

    // ================= GETTERS =================

    public String getMyConnectaID() {
        return myConnectaID;
    }

    public String getConnectedPeerAddress() {
        return connectedPeerAddress;
    }

    public String getSharedFolderPath() {
        return sharedFolderPath;
    }

    public boolean isConnected() {
        return networkManager.isConnected();
    }

    public int getFilesSentCount() {
        return filesSentCount.get();
    }

    public int getFilesReceivedCount() {
        return filesReceivedCount.get();
    }

    public int getTotalTransfersCount() {
        return totalTransfersCount.get();
    }

    private void dispatch(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
