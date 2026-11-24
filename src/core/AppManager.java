package core;

import filesystem.FileChunkManager;
import filesystem.FolderWatcherDaemon;
import filesystem.SyncStateManager;
import network.FilePacket;
import network.IDResolver;
import network.LanDiscoveryDaemon;
import network.NetworkManager;
import network.SocketStreamer;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AppManager (Central Application Orchestrator)
 * 
 * Bridges GUI, Networking, File System, and Persistent Configuration modules.
 * Coordinates real-time peer pairing, automated UDP LAN discovery, periodic auto-reconnect,
 * bidirectional catch-up synchronization, live telemetry rates, and DSA algorithms.
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. Bounded FIFO Queue (SocketStreamer / ArrayBlockingQueue):
 *    - Structure: Backpressure-enforcing FIFO queue strictly bounded to 64 chunks (512 KB).
 *    - Memory Complexity: Constant O(1) heap footprint, preventing OutOfMemoryError on large files.
 * 
 * 2. Hash Table (SyncStateManager / ConcurrentHashMap<String, String>):
 *    - Structure: Maps relative file paths to their 32-character MD5 checksums.
 *    - Search Complexity: O(1) average-case time complexity.
 *    - Purpose: Distributed infinite loop prevention and manifest comparison during catch-up sync.
 * 
 * 3. Stack (Stack<String> - LIFO Data Structure):
 *    - Structure: A thread-safe Last-In-First-Out (LIFO) stack storing the relative paths
 *      of recently synchronized files.
 *    - Purpose: Enables rollback/undo of the most recently transferred file state.
 *    - Algorithmic Complexity:
 *      * Push: O(1) constant time to push a newly transferred file.
 *      * Pop:  O(1) constant time to retrieve and remove the top file.
 * 
 * 4. N-ary Tree & Depth-First Search (DFS) (FolderWatcherDaemon):
 *    - Structure: Directory hierarchy modeled as an N-ary tree with recursive DFS traversal.
 *    - Complexity: O(V + E) time complexity to catalog and compute telemetry on all files/folders.
 * 
 * 5. Sequential Memory Arrays (byte[]):
 *    - 8192-byte (8KB) fixed contiguous primitive arrays with complete buffer isolation.
 * =========================================================================================
 */
public class AppManager {

    private static AppManager instance;

    // Core Subsystems
    private final NetworkManager networkManager;
    private final SyncStateManager syncStateManager;
    private final FileChunkManager chunkManager;
    private final ConfigManager configManager;
    private LanDiscoveryDaemon discoveryDaemon;
    private FolderWatcherDaemon folderWatcher;
    private Thread watcherThread;

    // Identification & Network State
    private String myConnectaID = "Resolving...";
    private String localDeviceName;
    private String connectedPeerAddress = null;
    private int connectedPeerPort = 8080;
    private String connectedPeerID = null;
    private String connectedPeerName = null;

    // Telemetry & Bandwidth
    private final long appStartTime = System.currentTimeMillis();
    private String currentUploadRate = "0 B/s";
    private String currentDownloadRate = "0 B/s";
    private FolderWatcherDaemon.FolderStats currentFolderStats = new FolderWatcherDaemon.FolderStats(0, 0, 0);

    // Active incoming file staging map (RelativePath -> ExpectedChecksum)
    private final ConcurrentHashMap<String, String> incomingFileExpectedChecksums = new ConcurrentHashMap<>();

    // DSA IMPLEMENTATION: LIFO Stack for tracking recent sync history and undo
    private final Stack<String> transferHistoryStack = new Stack<>();

    // Real-Time Transfer Metrics
    private final AtomicInteger filesSentCount = new AtomicInteger(0);
    private final AtomicInteger filesReceivedCount = new AtomicInteger(0);
    private final AtomicInteger totalTransfersCount = new AtomicInteger(0);

    // Auto-Reconnect Timer
    private Timer autoReconnectTimer;
    private volatile boolean isConnecting = false;

    // GUI Observer Listeners
    private final CopyOnWriteArrayList<AppEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Observer interface for broadcasting engine events to Swing UI components.
     */
    public interface AppEventListener {
        void onServerReady(String myID, int port);
        void onPeerConnected(String peerIP, int peerPort, String peerID, String peerName);
        void onPeerDisconnected(String reason);
        void onConnectionError(String error);
        void onTransferProgress(String fileName, int percent, String statusText, boolean isOutgoing);
        void onTransferComplete(String fileName, boolean isOutgoing);
        void onLog(String fileName, String message);
        void onStatsUpdated(int sent, int received, int total);
        void onRatesUpdated(String uploadRate, String downloadRate);
        void onFolderStatsUpdated(FolderWatcherDaemon.FolderStats stats);
        void onSharedFolderChanged(String newPath);
        void onSyncStatusChanged(String statusText);
        void onPairedDevicesChanged();
    }

    public static synchronized AppManager getInstance() {
        if (instance == null) {
            instance = new AppManager();
        }
        return instance;
    }

    private AppManager() {
        this.configManager = ConfigManager.getInstance();
        this.localDeviceName = configManager.getLocalDeviceName();
        this.networkManager = new NetworkManager();
        this.syncStateManager = new SyncStateManager();
        this.chunkManager = new FileChunkManager();

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
     * Initializes background networking, UDP LAN discovery, and auto-reconnect services.
     */
    public void start() {
        this.myConnectaID = IDResolver.generateIDFromIP();
        networkManager.startServer();

        // Calculate initial local folder state
        rescanLocalFolderStats();

        // Start UDP LAN Discovery Broadcast and Listener
        discoveryDaemon = new LanDiscoveryDaemon(myConnectaID, localDeviceName, networkManager.getListeningPort(),
                (peerId, peerName, peerIp, peerPort) -> {
                    handleDiscoveredPeer(peerId, peerName, peerIp, peerPort);
                });
        discoveryDaemon.start();

        // Start Auto-Reconnect Background Thread
        startAutoReconnectTimer();

        // Start monitoring primary shared folder
        startFolderWatcher();
    }

    private void setupNetworkListener() {
        networkManager.setNetworkListener(new NetworkManager.NetworkListener() {
            @Override
            public void onServerStarted(int port, String id) {
                AppManager.this.myConnectaID = id;
                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onServerReady(id, port);
                    }
                });
            }

            @Override
            public void onPeerConnected(String peerIP, int peerPort, SocketStreamer streamer) {
                isConnecting = false;
                connectedPeerAddress = peerIP;
                connectedPeerPort = peerPort;

                setupStreamerListeners(streamer);

                // Immediately send HANDSHAKE packet exchanging identity
                streamer.offerPacket(FilePacket.createHandshakePacket(myConnectaID, localDeviceName, networkManager.getListeningPort(), "2.0"), 500);

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onPeerConnected(peerIP, peerPort, connectedPeerID != null ? connectedPeerID : "Connecting...", 
                                connectedPeerName != null ? connectedPeerName : "Remote Peer");
                        l.onLog("Connection", "Socket connection established with " + peerIP);
                    }
                });
            }

            @Override
            public void onPeerDisconnected(String reason) {
                isConnecting = false;
                if (connectedPeerID != null) {
                    configManager.setDeviceOnline(connectedPeerID, false);
                }

                String oldPeer = connectedPeerAddress;
                connectedPeerAddress = null;
                connectedPeerID = null;
                connectedPeerName = null;
                currentUploadRate = "0 B/s";
                currentDownloadRate = "0 B/s";

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onPeerDisconnected(reason);
                        l.onRatesUpdated("0 B/s", "0 B/s");
                        l.onSyncStatusChanged("Disconnected");
                        l.onPairedDevicesChanged();
                        l.onLog("Connection", "Disconnected from " + (oldPeer != null ? oldPeer : "peer") + ": " + reason);
                    }
                });
            }

            @Override
            public void onConnectionFailed(String reason) {
                isConnecting = false;
                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onConnectionError(reason);
                        l.onLog("Connection", "Failed: " + reason);
                    }
                });
            }
        });
    }

    private void setupStreamerListeners(SocketStreamer streamer) {
        streamer.setRateListener((upRate, downRate, upBps, downBps) -> {
            currentUploadRate = upRate;
            currentDownloadRate = downRate;
            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onRatesUpdated(upRate, downRate);
                }
            });
        });

        streamer.setPacketListener(packet -> {
            switch (packet.getType()) {
                case HANDSHAKE:
                    handleIncomingHandshake(packet);
                    break;
                case HEARTBEAT:
                    // Heartbeat keep-alive; SocketStreamer updates timestamp automatically
                    break;
                case MANIFEST_SYNC:
                    handleIncomingManifest(packet);
                    break;
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
            }
        });
    }

    // ================= HANDSHAKE & PEER PERSISTENCE =================

    private void handleIncomingHandshake(FilePacket packet) {
        String payload = packet.getRelativePath();
        String[] parts = payload.split("\\|");
        if (parts.length >= 3) {
            String pId = parts[0].trim();
            String pName = parts[1].trim();
            int pPort = 8080;
            try { pPort = Integer.parseInt(parts[2].trim()); } catch (Exception ignored) {}

            this.connectedPeerID = pId;
            this.connectedPeerName = pName;

            // Automatically save to Persistent Configuration Manager
            configManager.addOrUpdatePairedDevice(pId, pName, connectedPeerAddress, pPort);
            configManager.setDeviceOnline(pId, true);

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onPeerConnected(connectedPeerAddress, connectedPeerPort, pId, pName);
                    l.onPairedDevicesChanged();
                    l.onSyncStatusChanged("Up to Date");
                    l.onLog("Handshake", "Verified paired device: " + pName + " (" + pId + ")");
                }
            });

            // Trigger Automatic Catch-Up Sync after handshake
            performCatchUpSync();
        }
    }

    private void handleDiscoveredPeer(String peerId, String peerName, String peerIp, int peerPort) {
        ConfigManager.PairedDevice paired = configManager.getPairedDevice(peerId);
        if (paired != null) {
            paired.setLastKnownIp(peerIp);
            paired.setPort(peerPort);
            paired.setDeviceName(peerName);
            configManager.saveConfig();

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onPairedDevicesChanged();
                }
            });

            // If not connected, initiate auto-reconnect immediately
            if (!networkManager.isConnected() && !isConnecting) {
                autoConnectToPeer(peerIp, peerPort, peerId);
            }
        }
    }

    private synchronized void autoConnectToPeer(String ip, int port, String peerId) {
        if (networkManager.isConnected() || isConnecting) return;
        isConnecting = true;

        new Thread(() -> {
            networkManager.connectToAddress(ip, port);
        }, "Connecta-AutoReconnect-" + peerId).start();
    }

    private void startAutoReconnectTimer() {
        autoReconnectTimer = new Timer("Connecta-AutoReconnectTimer", true);
        autoReconnectTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (networkManager.isConnected() || isConnecting) return;

                List<ConfigManager.PairedDevice> paired = configManager.getPairedDevices();
                for (ConfigManager.PairedDevice dev : paired) {
                    if (networkManager.isConnected()) break;
                    String ip = dev.getLastKnownIp();
                    if (ip == null || ip.isEmpty()) {
                        ip = IDResolver.resolveIPFromID(dev.getDeviceId());
                    }
                    if (ip != null && !ip.isEmpty()) {
                        autoConnectToPeer(ip, dev.getPort(), dev.getDeviceId());
                        break;
                    }
                }
            }
        }, 3000, 5000);
    }

    // ================= AUTOMATIC CATCH-UP SYNC =================

    public void performCatchUpSync() {
        if (!networkManager.isConnected()) return;

        new Thread(() -> {
            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onSyncStatusChanged("Syncing (Catch-Up)...");
                    l.onLog("CatchUpSync", "Initiating catch-up directory scan (DFS Traversal)");
                }
            });

            File rootDir = new File(getSharedFolderPath());
            if (!rootDir.exists()) rootDir.mkdirs();

            // 1. Traverse N-ary Tree via DFS to index current files
            FolderWatcherDaemon.computeFolderStatsDFS(rootDir);

            // 2. Exchange Manifest Packet with peer
            String manifest = syncStateManager.buildManifestString();
            SocketStreamer streamer = networkManager.getStreamer();
            if (streamer != null && streamer.isRunning()) {
                streamer.offerPacket(FilePacket.createManifestPacket(manifest), 1000);
            }

            // 3. Scan and sync any local files that differ from recorded history
            scanAndSyncAllLocalFilesDFS(rootDir, "");

            rescanLocalFolderStats();

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onSyncStatusChanged("Up to Date");
                    l.onLog("CatchUpSync", "Catch-up scan completed. Folders synchronized.");
                }
            });
        }, "Connecta-CatchUpSyncWorker").start();
    }

    private void scanAndSyncAllLocalFilesDFS(File currentDir, String relPrefix) {
        File[] children = currentDir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (FolderWatcherDaemon.isIgnoredFile(child)) continue;

            String childRel = relPrefix.isEmpty() ? child.getName() : relPrefix + "/" + child.getName();
            if (child.isDirectory()) {
                scanAndSyncAllLocalFilesDFS(child, childRel);
            } else if (child.isFile()) {
                String checksum = syncStateManager.calculateChecksum(child);
                if (checksum != null) {
                    if (!syncStateManager.isAlreadySynced(childRel, checksum)) {
                        syncFileToPeer(child, childRel);
                    }
                }
            }
        }
    }

    private void handleIncomingManifest(FilePacket packet) {
        if (packet.getChunkData() == null) return;
        String remoteManifest = new String(packet.getChunkData(), java.nio.charset.StandardCharsets.UTF_8);

        new Thread(() -> {
            File rootDir = new File(getSharedFolderPath());
            String[] lines = remoteManifest.split("\n");
            for (String line : lines) {
                int idx = line.indexOf('=');
                if (idx > 0) {
                    String relPath = line.substring(0, idx).trim();
                    String remoteHash = line.substring(idx + 1).trim();

                    File localFile = new File(rootDir, relPath);
                    if (localFile.exists()) {
                        String localHash = syncStateManager.calculateChecksum(localFile);
                        if (localHash != null && !localHash.equalsIgnoreCase(remoteHash)) {
                            if (localFile.lastModified() > 0) {
                                syncFileToPeer(localFile, relPath);
                            }
                        }
                    }
                }
            }
        }, "Connecta-ManifestProcessor").start();
    }

    // ================= INCOMING FILE CHUNKS & ATOMIC REASSEMBLY =================

    private void handleIncomingFileStart(FilePacket packet) {
        String relPath = packet.getRelativePath();
        String expectedChecksum = packet.getChecksum();
        if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
            incomingFileExpectedChecksums.put(relPath, expectedChecksum);
        }

        // Initialize staging temporary file
        File tempFile = new File(getSharedFolderPath(), relPath + FileChunkManager.TMP_EXTENSION);
        if (tempFile.exists()) {
            tempFile.delete(); // Clear stale prior temp file
        }

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onTransferProgress(relPath, 0, "Receiving metadata...", false);
                l.onSyncStatusChanged("Syncing (Incoming)...");
                l.onLog(relPath, "Incoming file staged: " + packet.getFileSize() + " bytes");
            }
        });
    }

    private void handleIncomingFileChunk(FilePacket packet) {
        String relPath = packet.getRelativePath();
        File tempFile = new File(getSharedFolderPath(), relPath + FileChunkManager.TMP_EXTENSION);

        try {
            boolean isFirstChunk = (packet.getSequenceNumber() == 0);
            chunkManager.writeChunkToTempFile(tempFile, packet.getChunkData(), isFirstChunk);

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
        File tempFile = new File(getSharedFolderPath(), relPath + FileChunkManager.TMP_EXTENSION);
        File finalDestFile = new File(getSharedFolderPath(), relPath);

        String expectedChecksum = packet.getChecksum();
        if (expectedChecksum == null || expectedChecksum.isEmpty()) {
            expectedChecksum = incomingFileExpectedChecksums.remove(relPath);
        }

        // ATOMIC COMMIT: Verify MD5 of temp file and atomically move to final location
        boolean success = chunkManager.finalizeReassembly(tempFile, finalDestFile, expectedChecksum, syncStateManager, relPath);

        if (success) {
            // DSA: O(1) Stack push to record file in LIFO history
            synchronized (transferHistoryStack) {
                transferHistoryStack.push(relPath);
            }

            int rec = filesReceivedCount.incrementAndGet();
            int total = totalTransfersCount.incrementAndGet();

            rescanLocalFolderStats();

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onTransferProgress(relPath, 100, "Received & Verified", false);
                    l.onTransferComplete(relPath, false);
                    l.onSyncStatusChanged("Up to Date");
                    l.onLog(relPath, "MD5 verified. Committed atomically to shared folder.");
                    l.onStatsUpdated(filesSentCount.get(), rec, total);
                }
            });
        } else {
            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onTransferProgress(relPath, 0, "Checksum Integrity Error", false);
                    l.onLog(relPath, "Integrity Check Failed! Discarded corrupted file.");
                }
            });
        }
    }

    private void handleIncomingFileDelete(FilePacket packet) {
        String relPath = packet.getRelativePath();
        File fileToDelete = new File(getSharedFolderPath(), relPath);
        if (fileToDelete.exists()) {
            fileToDelete.delete();
            syncStateManager.removeHistory(relPath);
            rescanLocalFolderStats();

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog(relPath, "Deleted remotely by peer");
                }
            });
        }
    }

    private void handleIncomingUndo(FilePacket packet) {
        String relPath = packet.getRelativePath();
        File fileToUndo = new File(getSharedFolderPath(), relPath);
        if (fileToUndo.exists()) {
            fileToUndo.delete();
            syncStateManager.removeHistory(relPath);
            rescanLocalFolderStats();
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

    // ================= OUTGOING FILE SYNC (BOUNDED QUEUE BACKPRESSURE) =================

    public void syncFileToPeer(File file, String relativePath) {
        if (!networkManager.isConnected() || file == null || !file.exists() || !file.isFile()) {
            return;
        }

        if (FolderWatcherDaemon.isIgnoredFile(file)) {
            return;
        }

        SocketStreamer streamer = networkManager.getStreamer();
        if (streamer == null || !streamer.isRunning()) {
            return;
        }

        new Thread(() -> {
            try {
                // PARTIAL-READ RACE CONDITION FIX: Wait until file writing completes and file is readable
                if (!FolderWatcherDaemon.waitForFileStability(file, 10000)) {
                    return;
                }

                String checksum = syncStateManager.calculateChecksum(file);
                if (checksum == null) return;

                // DSA HASH TABLE CHECK: O(1) search complexity to prevent infinite sync loops
                if (syncStateManager.isAlreadySynced(relativePath, checksum)) {
                    return;
                }

                syncStateManager.recordSyncedState(relativePath, checksum);

                long fileSize = file.length();
                int totalChunks = FileChunkManager.calculateTotalChunks(fileSize);

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onTransferProgress(file.getName(), 0, "Queuing file...", true);
                        l.onSyncStatusChanged("Syncing (Outgoing)...");
                        l.onLog(file.getName(), "Starting upload (" + totalChunks + " chunks, " + fileSize + " bytes)");
                    }
                });

                // Send FILE_START packet
                FilePacket startPacket = FilePacket.createStartPacket(relativePath, fileSize, totalChunks, checksum);
                streamer.queuePacket(startPacket); // DSA: Bounded FIFO Queue put()

                // Split file into isolated 8KB byte[] buffers and stream with automatic backpressure
                chunkManager.splitFileIntoChunks(file, (chunk, seq, total, bytesRead, totalBytes) -> {
                    FilePacket chunkPacket = FilePacket.createChunkPacket(relativePath, seq, total, chunk);
                    
                    // DSA BACKPRESSURE: Blocks producer if Bounded Queue (64) is full
                    streamer.queuePacket(chunkPacket);

                    int percent = total > 0 ? (int) (((double) (seq + 1) / total) * 100) : 100;
                    dispatch(() -> {
                        for (AppEventListener l : listeners) {
                            l.onTransferProgress(file.getName(), percent, "Streaming chunk " + (seq + 1) + " of " + total, true);
                        }
                    });
                });

                // Send FILE_COMPLETE packet (EOF + MD5 Checksum)
                FilePacket completePacket = FilePacket.createCompletePacket(relativePath, checksum);
                streamer.queuePacket(completePacket);

                synchronized (transferHistoryStack) {
                    transferHistoryStack.push(relativePath);
                }

                int sent = filesSentCount.incrementAndGet();
                int total = totalTransfersCount.incrementAndGet();

                rescanLocalFolderStats();

                dispatch(() -> {
                    for (AppEventListener l : listeners) {
                        l.onTransferProgress(file.getName(), 100, "Sent successfully", true);
                        l.onTransferComplete(file.getName(), true);
                        l.onSyncStatusChanged("Up to Date");
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

    public void syncFileDeletion(String relativePath) {
        if (!networkManager.isConnected()) return;
        SocketStreamer streamer = networkManager.getStreamer();
        if (streamer != null && streamer.isRunning()) {
            syncStateManager.removeHistory(relativePath);
            streamer.offerPacket(FilePacket.createDeletePacket(relativePath), 1000);
            rescanLocalFolderStats();

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog(relativePath, "Deletion synced to partner");
                }
            });
        }
    }

    // ================= DSA STACK: UNDO RECENT TRANSFER =================

    public String undoRecentTransfer() {
        String lastTransferredFile;
        synchronized (transferHistoryStack) {
            if (transferHistoryStack.isEmpty()) {
                return null;
            }
            lastTransferredFile = transferHistoryStack.pop(); // DSA: LIFO Pop O(1)
        }

        File targetFile = new File(getSharedFolderPath(), lastTransferredFile);
        if (targetFile.exists()) {
            targetFile.delete();
        }

        syncStateManager.removeHistory(lastTransferredFile);

        if (networkManager.isConnected() && networkManager.getStreamer() != null) {
            networkManager.getStreamer().offerPacket(FilePacket.createUndoPacket(lastTransferredFile), 1000);
        }

        rescanLocalFolderStats();

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

    // ================= FOLDER WATCHER & TELEMETRY =================

    public void rescanLocalFolderStats() {
        new Thread(() -> {
            File root = new File(getSharedFolderPath());
            FolderWatcherDaemon.FolderStats stats = FolderWatcherDaemon.computeFolderStatsDFS(root);
            this.currentFolderStats = stats;

            ConfigManager.FolderConfig primary = configManager.getPrimarySharedFolder();
            primary.setTotalFiles(stats.fileCount);
            primary.setTotalFolders(stats.folderCount);
            primary.setTotalBytes(stats.totalBytes);

            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onFolderStatsUpdated(stats);
                }
            });
        }, "Connecta-TelemetryScanner").start();
    }

    private void startFolderWatcher() {
        stopFolderWatcher();

        try {
            folderWatcher = new FolderWatcherDaemon(getSharedFolderPath(), new FolderWatcherDaemon.EventListener() {
                @Override
                public void onFileEventDetected(File file, String relativePath, FolderWatcherDaemon.WatchKind kind) {
                    if (kind == FolderWatcherDaemon.WatchKind.CREATE || kind == FolderWatcherDaemon.WatchKind.MODIFY) {
                        syncFileToPeer(file, relativePath);
                    } else if (kind == FolderWatcherDaemon.WatchKind.DELETE) {
                        syncFileDeletion(relativePath);
                    }
                    rescanLocalFolderStats();
                }

                @Override
                public void onInitialScanFileFound(File file, String relativePath) {
                    String hash = syncStateManager.calculateChecksum(file);
                    if (hash != null) {
                        syncStateManager.recordSyncedState(relativePath, hash);
                    }
                }
            });

            watcherThread = new Thread(folderWatcher, "Connecta-FolderWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
        } catch (Exception e) {
            System.err.println("FolderWatcher startup failed: " + e.getMessage());
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

    public void togglePauseAll() {
        boolean newPaused = !isPaused();
        if (folderWatcher != null) {
            folderWatcher.setPaused(newPaused);
        }
        ConfigManager.FolderConfig primary = configManager.getPrimarySharedFolder();
        primary.setPaused(newPaused);
        primary.setSyncStatus(newPaused ? "Paused" : "Up to Date");

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onSyncStatusChanged(newPaused ? "Paused" : "Up to Date");
                l.onLog("Sync Control", newPaused ? "Synchronization paused" : "Synchronization resumed");
            }
        });
    }

    public boolean isPaused() {
        return folderWatcher != null && folderWatcher.isPaused();
    }

    public void rescanAll() {
        rescanLocalFolderStats();
        if (networkManager.isConnected()) {
            performCatchUpSync();
        } else {
            dispatch(() -> {
                for (AppEventListener l : listeners) {
                    l.onLog("Rescan", "Local N-ary Tree scan completed.");
                }
            });
        }
    }

    public void setSharedFolderPath(String newPath) {
        if (newPath == null || newPath.trim().isEmpty()) return;
        File dir = new File(newPath.trim());
        if (!dir.exists()) {
            dir.mkdirs();
        }

        ConfigManager.FolderConfig primary = configManager.getPrimarySharedFolder();
        primary.setPath(dir.getAbsolutePath());
        configManager.saveConfig();

        this.syncStateManager.clear();
        startFolderWatcher();
        rescanLocalFolderStats();

        dispatch(() -> {
            for (AppEventListener l : listeners) {
                l.onSharedFolderChanged(dir.getAbsolutePath());
                l.onLog("Shared Folder", "Updated path to: " + dir.getAbsolutePath());
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
        if (autoReconnectTimer != null) {
            autoReconnectTimer.cancel();
            autoReconnectTimer = null;
        }
        if (discoveryDaemon != null) {
            discoveryDaemon.stop();
        }
        stopFolderWatcher();
        networkManager.shutdown();
    }

    // ================= GETTERS =================

    public String getMyConnectaID() { return myConnectaID; }
    public String getLocalDeviceName() { return localDeviceName; }
    public String getConnectedPeerAddress() { return connectedPeerAddress; }
    public String getConnectedPeerID() { return connectedPeerID; }
    public String getConnectedPeerName() { return connectedPeerName; }
    public String getSharedFolderPath() { return configManager.getPrimarySharedFolder().getPath(); }
    public boolean isConnected() { return networkManager.isConnected(); }
    public int getFilesSentCount() { return filesSentCount.get(); }
    public int getFilesReceivedCount() { return filesReceivedCount.get(); }
    public int getTotalTransfersCount() { return totalTransfersCount.get(); }
    public String getCurrentUploadRate() { return currentUploadRate; }
    public String getCurrentDownloadRate() { return currentDownloadRate; }
    public FolderWatcherDaemon.FolderStats getCurrentFolderStats() { return currentFolderStats; }
    public ConfigManager getConfigManager() { return configManager; }

    public String getFormattedUptime() {
        long uptimeSec = (System.currentTimeMillis() - appStartTime) / 1000;
        long hours = uptimeSec / 3600;
        long minutes = (uptimeSec % 3600) / 60;
        long seconds = uptimeSec % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void dispatch(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
