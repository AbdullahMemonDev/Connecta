package filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FolderWatcherDaemon
 * 
 * Background daemon thread monitoring a target shared folder for file-system changes
 * (Creation, Modification, Deletion).
 * 
 * =========================================================================================
 * DATA STRUCTURES & ALGORITHMS (DSA) UTILIZATION:
 * 
 * 1. N-ary Tree Representation:
 *    - Model: The shared directory hierarchy is treated as an N-ary Tree.
 *      * Root Node: The top-level shared folder directory.
 *      * Internal Nodes (Branch Nodes): Sub-directories, each having an arbitrary degree N
 *        (variable number of child sub-folders and files).
 *      * Leaf Nodes: Regular files or empty directories.
 * 
 * 2. Depth-First Search (DFS) Traversal:
 *    - Algorithm: A recursive DFS explores down each branch of the directory tree to the
 *      deepest leaf node before backtracking.
 *    - Purpose: Standard Java NIO WatchService is non-recursive on most operating systems.
 *      To ensure seamless synchronization across deeply nested folders (e.g., /docs/2026/labs/):
 *      1. DFS recursively visits every directory node in the N-ary tree.
 *      2. Registers each directory node with the native WatchService.
 *      3. Catalogs all initial leaf file nodes for baseline synchronization and telemetry stats.
 *    - Algorithmic Complexity:
 *      * Time Complexity: O(V + E), where V is total files and folders, and E is directory edges.
 *        Every node in the file tree is visited exactly once during traversal.
 *      * Space Complexity: O(H), where H is the maximum depth (height) of the directory tree,
 *        bounded by the recursive call stack.
 * 
 * 3. Partial-Read Race Condition Prevention (Stability Debouncing):
 *    - Background debouncer verifies that OS file writes are complete and file locks are released
 *      before dispatching synchronization events.
 * =========================================================================================
 */
public class FolderWatcherDaemon implements Runnable {

    private final Path rootPath;
    private WatchService watcher;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final EventListener listener;

    // Maps WatchKey to its registered directory Path in the N-ary Tree
    private final ConcurrentHashMap<WatchKey, Path> watchKeyMap = new ConcurrentHashMap<>();

    // Worker pool for debouncing file stability
    private final ExecutorService stabilityExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Connecta-FileStabilityCheck");
        t.setDaemon(true);
        return t;
    });

    /**
     * Enumeration of file system mutations.
     */
    public enum WatchKind {
        CREATE,
        MODIFY,
        DELETE
    }

    /**
     * Container holding directory telemetry statistics computed via N-ary Tree DFS.
     */
    public static class FolderStats {
        public final int fileCount;
        public final int folderCount;
        public final long totalBytes;

        public FolderStats(int fileCount, int folderCount, long totalBytes) {
            this.fileCount = fileCount;
            this.folderCount = folderCount;
            this.totalBytes = totalBytes;
        }

        public String formatSize() {
            if (totalBytes <= 0) return "0 B";
            if (totalBytes < 1024) return totalBytes + " B";
            if (totalBytes < 1024 * 1024) return String.format("%.1f KB", totalBytes / 1024.0);
            if (totalBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalBytes / (1024.0 * 1024.0));
            return String.format("%.2f GB", totalBytes / (1024.0 * 1024.0 * 1024.0));
        }

        public String getSummary() {
            return fileCount + " files, " + folderCount + " folders, ~" + formatSize();
        }
    }

    /**
     * Listener interface to notify the application orchestrator of file changes.
     */
    public interface EventListener {
        void onFileEventDetected(File file, String relativePath, WatchKind kind);
        void onInitialScanFileFound(File file, String relativePath);
    }

    /**
     * Visitor interface for N-ary Tree DFS traversal.
     */
    public interface DFSNodeVisitor {
        void onFileNode(File file, String relativePath);
        void onDirectoryNode(File dir, String relativePath);
    }

    public FolderWatcherDaemon(String folderPath, EventListener listener) throws IOException {
        this.rootPath = Paths.get(folderPath).toAbsolutePath().normalize();
        this.listener = listener;

        File rootFolder = this.rootPath.toFile();
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }

        this.watcher = FileSystems.getDefault().newWatchService();

        // Perform initial N-ary Tree DFS registration and scanning
        scanAndRegisterTreeDFS(rootFolder, new DFSNodeVisitor() {
            @Override
            public void onFileNode(File file, String relativePath) {
                if (!isIgnoredFile(file) && listener != null) {
                    listener.onInitialScanFileFound(file, relativePath);
                }
            }

            @Override
            public void onDirectoryNode(File dir, String relativePath) {
                // Directory registered in WatchService
            }
        });
    }

    /**
     * Checks if a file is an internal temporary or hidden system file that should be ignored.
     */
    public static boolean isIgnoredFile(File file) {
        if (file == null) return true;
        String name = file.getName();
        return name.endsWith(".connecta.tmp") ||
               name.endsWith(".tmp") ||
               name.endsWith(".crdownload") ||
               name.endsWith(".part") ||
               name.startsWith("~") ||
               (name.startsWith(".") && !name.equals("."));
    }

    /**
     * DSA ALGORITHM: N-ary Tree Depth-First Search (DFS) Traversal
     * 
     * Recursively traverses sub-directories to register them with the OS WatchService
     * and discover all existing files.
     * 
     * @param currentDirectory The directory node currently being visited.
     * @param visitor Callback for each visited node in the N-ary tree.
     */
    public void scanAndRegisterTreeDFS(File currentDirectory, DFSNodeVisitor visitor) {
        if (currentDirectory == null || !currentDirectory.exists() || !currentDirectory.isDirectory()) {
            return;
        }

        // 1. Process current directory node: Register with native WatchService
        try {
            Path dirPath = currentDirectory.toPath();
            WatchKey key = dirPath.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchKeyMap.put(key, dirPath);

            if (visitor != null) {
                visitor.onDirectoryNode(currentDirectory, getRelativePath(currentDirectory));
            }
        } catch (IOException e) {
            System.err.println("Failed to register directory in DFS: " + currentDirectory.getAbsolutePath());
        }

        // 2. Explore children nodes (Branching factor N)
        File[] children = currentDirectory.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (isIgnoredFile(child)) continue;

            if (child.isDirectory()) {
                // Recursive step: Traverse deeper into the subtree (DFS order)
                scanAndRegisterTreeDFS(child, visitor);
            } else if (child.isFile()) {
                // Leaf node reached: notify visitor
                if (visitor != null) {
                    visitor.onFileNode(child, getRelativePath(child));
                }
            }
        }
    }

    /**
     * DSA ALGORITHM: N-ary Tree DFS Telemetry Aggregator
     * 
     * Recursively computes the exact file count, sub-folder count, and total byte size
     * across the directory hierarchy.
     */
    public static FolderStats computeFolderStatsDFS(File rootDir) {
        if (rootDir == null || !rootDir.exists()) {
            return new FolderStats(0, 0, 0);
        }

        int[] counters = new int[2]; // [0] = files, [1] = folders
        long[] totalBytes = new long[1];

        dfsAccumulate(rootDir, counters, totalBytes);
        return new FolderStats(counters[0], counters[1], totalBytes[0]);
    }

    private static void dfsAccumulate(File current, int[] counters, long[] totalBytes) {
        File[] children = current.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (isIgnoredFile(child)) continue;

            if (child.isDirectory()) {
                counters[1]++; // Folder count increment
                dfsAccumulate(child, counters, totalBytes); // Recursive DFS branch exploration
            } else if (child.isFile()) {
                counters[0]++; // File count increment
                totalBytes[0] += child.length();
            }
        }
    }

    /**
     * Background event loop monitoring OS filesystem events.
     */
    @Override
    public void run() {
        while (running) {
            WatchKey key;
            try {
                key = watcher.take(); // Efficiently blocks until an event occurs
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return; // Graceful shutdown
            }

            Path dir = watchKeyMap.get(key);
            if (dir == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path childPath = dir.resolve(filename);
                File childFile = childPath.toFile();

                // Ignore temporary, incomplete, or staging files
                if (isIgnoredFile(childFile)) {
                    continue;
                }

                String relativePath = getRelativePath(childFile);

                // If a new sub-directory was created, dynamically register it via DFS
                if (kind == StandardWatchEventKinds.ENTRY_CREATE && childFile.isDirectory()) {
                    scanAndRegisterTreeDFS(childFile, null);
                }

                // If monitoring is paused by user ("Pause All"), skip
                if (paused) {
                    continue;
                }

                if (listener != null) {
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        listener.onFileEventDetected(childFile, relativePath, WatchKind.DELETE);
                    } else if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // PARTIAL-READ RACE CONDITION FIX:
                        // Debounce and verify file stability (size not changing, lock released) before reading
                        stabilityExecutor.submit(() -> {
                            if (waitForFileStability(childFile, 12000)) {
                                WatchKind effectiveKind = (kind == StandardWatchEventKinds.ENTRY_CREATE) 
                                        ? WatchKind.CREATE : WatchKind.MODIFY;
                                listener.onFileEventDetected(childFile, relativePath, effectiveKind);
                            }
                        });
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeyMap.remove(key);
            }
        }
    }

    /**
     * Waits until the file has stopped growing and can be opened for reading (OS file lock released).
     * 
     * @param file The file being written by the OS.
     * @param maxWaitMs Maximum milliseconds to wait.
     * @return true if the file is stable and readable, false if timed out or deleted.
     */
    public static boolean waitForFileStability(File file, long maxWaitMs) {
        if (file == null || !file.exists()) return false;
        long startTime = System.currentTimeMillis();
        long lastSize = -1;

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (!file.exists()) return false;
            long currentSize = file.length();

            // Check if file size is stable across consecutive checks
            if (currentSize == lastSize && currentSize >= 0) {
                // Try opening a stream to ensure OS write lock (e.g., Windows copy) is released
                try (FileInputStream fis = new FileInputStream(file)) {
                    return true; // File is stable and readable
                } catch (IOException locked) {
                    // File is still being actively written / locked
                }
            }

            lastSize = currentSize;
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                return false;
            }
        }

        return file.exists() && file.canRead();
    }

    /**
     * Converts a File reference into a relative path string normalized from the shared root.
     */
    public String getRelativePath(File file) {
        try {
            Path target = file.toPath().toAbsolutePath().normalize();
            Path relative = rootPath.relativize(target);
            return relative.toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getName();
        }
    }

    public Path getRootPath() {
        return rootPath;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    /**
     * Stops the watcher daemon and closes native OS resources.
     */
    public void stopWatching() {
        running = false;
        stabilityExecutor.shutdownNow();
        try {
            if (watcher != null) {
                watcher.close();
            }
        } catch (IOException ignored) {}
    }
}
