package filesystem; // Declares the package where this file-system watcher resides

import java.io.File; // Imports Java File class to handle local disk file references
import java.nio.file.*; // Imports Java NIO packages for WatchService, Path, and file system events

public class FolderWatcherDaemon implements Runnable {
    
    // The directory path that the daemon will monitor for changes
    private final Path sharedFolderPath;
    
    // Native OS file system watch service instance
    private WatchService watcher;
    
    // Volatile flag to safely control the running state across background threads
    private volatile boolean running = true;
    
    // Callback interface reference to notify the central app manager of file events
    private EventListener listener;

    /**
     * Interface callback to notify the central AppManager when an OS file event occurs.
     */
    public interface EventListener {
        void onFileEventDetected(File file, WatchKind kind);
    }

    // Enumeration defining the types of file system events we track
    public enum WatchKind { CREATE, MODIFY, DELETE }

    /**
     * Constructor initializes the WatchService and registers file system events on the target directory.
     * @param folderPath The string path of the shared folder to monitor.
     * @param listener The event listener callback implementation.
     */
    public FolderWatcherDaemon(String folderPath, EventListener listener) throws Exception {
        // Convert the string folder path into a Java NIO Path object
        this.sharedFolderPath = Paths.get(folderPath);
        
        // Convert Path to File to check if the shared directory exists on disk
        File folder = sharedFolderPath.toFile();
        if (!folder.exists()) {
            folder.mkdirs(); // Automatically create the shared folder hierarchy if it doesn't exist
        }

        // Initialize the native operating system watch service using Java NIO
        this.watcher = FileSystems.getDefault().newWatchService();
        
        // Register interest in file creation, modification, and deletion system events
        this.sharedFolderPath.register(watcher, 
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);
            
        // Assign the passed event listener callback
        this.listener = listener;
    }

    @Override
    public void run() {
        // Print startup diagnostic log showing the absolute path being watched
        System.out.println("FolderWatcherDaemon started monitoring directory: " + sharedFolderPath.toAbsolutePath());
        
        // Infinite background loop running while 'running' is true
        while (running) {
            WatchKey key;
            try {
                // take() blocks execution efficiently without CPU overhead until an OS file event occurs or watcher is closed
                key = watcher.take();
            } catch (InterruptedException | ClosedWatchServiceException x) {
                // Thread interrupted or watch service closed gracefully during shutdown
                return;
            }
            
            // Iterate through all pending events captured by the watch key
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // Ignore overflow events where OS system buffers might have dropped events
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                // Cast the event to extract the relative filename path, suppressing the unchecked cast warning
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                
                // Resolve the filename against the shared folder path to get the full File object
                File targetFile = sharedFolderPath.resolve(filename).toFile();

                // Map Java NIO WatchEvent kinds to our application-level WatchKind enum and trigger callback
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    listener.onFileEventDetected(targetFile, WatchKind.CREATE);
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    listener.onFileEventDetected(targetFile, WatchKind.MODIFY);
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    listener.onFileEventDetected(targetFile, WatchKind.DELETE);
                }
            }

            // Reset the key to receive subsequent events; if the key is no longer valid, break the loop
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }

    /**
     * Stops the background monitoring daemon thread safely.
     */
    public void stopWatching() {
        running = false; // Set flag to false to terminate the while loop
        try {
            if (watcher != null) {
                watcher.close(); // Close the watch service resource
            }
        } catch (Exception e) {
            e.printStackTrace(); // Print trace if closing fails
        }
    }
}