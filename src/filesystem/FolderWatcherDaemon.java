package filesystem;

import java.io.File;
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
 *      3. Catalogs all initial leaf file nodes for baseline synchronization.
 *    - Algorithmic Complexity:
 *      * Time Complexity: O(V + E), where V is total files and folders, and E is directory edges.
 *        Every node in the file tree is visited exactly once during traversal.
 *      * Space Complexity: O(H), where H is the maximum depth (height) of the directory tree,
 *        bounded by the recursive call stack.
 * =========================================================================================
 */
public class FolderWatcherDaemon implements Runnable {

    private final Path rootPath;
    private WatchService watcher;
    private volatile boolean running = true;
    private final EventListener listener;

    // Maps WatchKey to its registered directory Path in the N-ary Tree
    private final ConcurrentHashMap<WatchKey, Path> watchKeyMap = new ConcurrentHashMap<>();

    /**
     * Enumeration of file system mutations.
     */
    public enum WatchKind {
        CREATE,
        MODIFY,
        DELETE
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
                if (listener != null) {
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
                String relativePath = getRelativePath(childFile);

                // If a new sub-directory was created, dynamically register it via DFS
                if (kind == StandardWatchEventKinds.ENTRY_CREATE && childFile.isDirectory()) {
                    scanAndRegisterTreeDFS(childFile, null);
                }

                if (listener != null) {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        listener.onFileEventDetected(childFile, relativePath, WatchKind.CREATE);
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        listener.onFileEventDetected(childFile, relativePath, WatchKind.MODIFY);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        listener.onFileEventDetected(childFile, relativePath, WatchKind.DELETE);
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

    /**
     * Stops the watcher daemon and closes native OS resources.
     */
    public void stopWatching() {
        running = false;
        try {
            if (watcher != null) {
                watcher.close();
            }
        } catch (IOException ignored) {}
    }
}
