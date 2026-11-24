package core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ConfigManager
 * 
 * Persistent configuration store for Connecta. Automatically saves and restores:
 * 1. Paired Remote Devices (Device ID, Device Name, Last Known LAN IP and Port).
 * 2. Shared Folder Paths and synchronization preferences.
 * 3. Local telemetry identity (Machine name, UI theme preferences).
 * 
 * Persisted in a local properties file: ~/.connecta/config.properties
 */
public class ConfigManager {

    private static ConfigManager instance;

    private static final String CONFIG_DIR_NAME = ".connecta";
    private static final String CONFIG_FILE_NAME = "config.properties";

    private final File configFile;
    private final Properties properties = new Properties();

    private String localDeviceName;
    private boolean darkMode = true;
    private boolean soundEnabled = true;

    private final List<PairedDevice> pairedDevices = new CopyOnWriteArrayList<>();
    private final List<FolderConfig> sharedFolders = new CopyOnWriteArrayList<>();

    /**
     * Paired Remote Device Model
     */
    public static class PairedDevice {
        private final String deviceId;
        private String deviceName;
        private String lastKnownIp;
        private int port;
        private long lastSeen;
        private boolean isOnline;

        public PairedDevice(String deviceId, String deviceName, String lastKnownIp, int port, long lastSeen) {
            this.deviceId = deviceId != null ? deviceId.trim().toUpperCase() : "";
            this.deviceName = deviceName != null ? deviceName.trim() : "Unknown Device";
            this.lastKnownIp = lastKnownIp != null ? lastKnownIp.trim() : "";
            this.port = port > 0 ? port : 8080;
            this.lastSeen = lastSeen > 0 ? lastSeen : System.currentTimeMillis();
            this.isOnline = false;
        }

        public String getDeviceId() { return deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String name) { this.deviceName = name; }
        public String getLastKnownIp() { return lastKnownIp; }
        public void setLastKnownIp(String ip) { this.lastKnownIp = ip; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long time) { this.lastSeen = time; }
        public boolean isOnline() { return isOnline; }
        public void setOnline(boolean online) { this.isOnline = online; }

        public String serialize() {
            return deviceId + "|" + deviceName.replace("|", "_") + "|" + lastKnownIp + "|" + port + "|" + lastSeen;
        }

        public static PairedDevice deserialize(String line) {
            if (line == null || line.trim().isEmpty()) return null;
            String[] parts = line.split("\\|");
            if (parts.length >= 4) {
                String id = parts[0].trim();
                String name = parts[1].trim();
                String ip = parts[2].trim();
                int port = 8080;
                try { port = Integer.parseInt(parts[3].trim()); } catch (Exception ignored) {}
                long lastSeen = System.currentTimeMillis();
                if (parts.length >= 5) {
                    try { lastSeen = Long.parseLong(parts[4].trim()); } catch (Exception ignored) {}
                }
                return new PairedDevice(id, name, ip, port, lastSeen);
            }
            return null;
        }
    }

    /**
     * Shared Folder Configuration Model
     */
    public static class FolderConfig {
        private final String id;
        private String label;
        private String path;
        private boolean isPaused;
        private String syncStatus = "Up to Date";
        private int totalFiles = 0;
        private int totalFolders = 0;
        private long totalBytes = 0;

        public FolderConfig(String id, String label, String path, boolean isPaused) {
            this.id = id != null ? id : "default";
            this.label = label != null ? label : "Default Sync Folder";
            this.path = path != null ? path : "";
            this.isPaused = isPaused;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isPaused() { return isPaused; }
        public void setPaused(boolean paused) { this.isPaused = paused; }
        public String getSyncStatus() { return syncStatus; }
        public void setSyncStatus(String status) { this.syncStatus = status; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
        public int getTotalFolders() { return totalFolders; }
        public void setTotalFolders(int totalFolders) { this.totalFolders = totalFolders; }
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

        public String serialize() {
            return id + "|" + label.replace("|", "_") + "|" + path.replace("|", "_") + "|" + isPaused;
        }

        public static FolderConfig deserialize(String line) {
            if (line == null || line.trim().isEmpty()) return null;
            String[] parts = line.split("\\|");
            if (parts.length >= 3) {
                String id = parts[0].trim();
                String label = parts[1].trim();
                String path = parts[2].trim();
                boolean paused = false;
                if (parts.length >= 4) {
                    paused = Boolean.parseBoolean(parts[3].trim());
                }
                return new FolderConfig(id, label, path, paused);
            }
            return null;
        }
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private ConfigManager() {
        File dir = new File(System.getProperty("user.home"), CONFIG_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.configFile = new File(dir, CONFIG_FILE_NAME);

        // Detect default local device hostname
        try {
            this.localDeviceName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            this.localDeviceName = "Connecta-Device";
        }

        loadConfig();
    }

    public synchronized void loadConfig() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("Could not load config file: " + e.getMessage());
            }
        }

        this.localDeviceName = properties.getProperty("localDeviceName", this.localDeviceName);
        this.darkMode = Boolean.parseBoolean(properties.getProperty("darkMode", "true"));
        this.soundEnabled = Boolean.parseBoolean(properties.getProperty("soundEnabled", "true"));

        // Load Paired Devices
        pairedDevices.clear();
        String pairedRaw = properties.getProperty("pairedDevices", "");
        if (!pairedRaw.isEmpty()) {
            for (String item : pairedRaw.split(";")) {
                PairedDevice dev = PairedDevice.deserialize(item);
                if (dev != null && !dev.getDeviceId().isEmpty()) {
                    pairedDevices.add(dev);
                }
            }
        }

        // Load Shared Folders
        sharedFolders.clear();
        String foldersRaw = properties.getProperty("sharedFolders", "");
        if (!foldersRaw.isEmpty()) {
            for (String item : foldersRaw.split(";")) {
                FolderConfig f = FolderConfig.deserialize(item);
                if (f != null && !f.getPath().isEmpty()) {
                    sharedFolders.add(f);
                }
            }
        }

        // Ensure at least one default shared folder exists: ~/ConnectaSync
        if (sharedFolders.isEmpty()) {
            String defaultPath = System.getProperty("user.home") + File.separator + "ConnectaSync";
            File d = new File(defaultPath);
            if (!d.exists()) {
                d.mkdirs();
            }
            sharedFolders.add(new FolderConfig("default", "Default Sync Folder", defaultPath, false));
            saveConfig();
        }
    }

    public synchronized void saveConfig() {
        properties.setProperty("localDeviceName", localDeviceName != null ? localDeviceName : "Connecta-Device");
        properties.setProperty("darkMode", String.valueOf(darkMode));
        properties.setProperty("soundEnabled", String.valueOf(soundEnabled));

        // Serialize Paired Devices
        StringBuilder devSb = new StringBuilder();
        for (int i = 0; i < pairedDevices.size(); i++) {
            if (i > 0) devSb.append(";");
            devSb.append(pairedDevices.get(i).serialize());
        }
        properties.setProperty("pairedDevices", devSb.toString());

        // Serialize Shared Folders
        StringBuilder fSb = new StringBuilder();
        for (int i = 0; i < sharedFolders.size(); i++) {
            if (i > 0) fSb.append(";");
            fSb.append(sharedFolders.get(i).serialize());
        }
        properties.setProperty("sharedFolders", fSb.toString());

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "Connecta LAN Folder Sync Configuration");
        } catch (IOException e) {
            System.err.println("Could not save config file: " + e.getMessage());
        }
    }

    public synchronized void addOrUpdatePairedDevice(String id, String name, String ip, int port) {
        if (id == null || id.trim().isEmpty()) return;
        String cleanId = id.trim().toUpperCase();

        PairedDevice existing = null;
        for (PairedDevice d : pairedDevices) {
            if (d.getDeviceId().equalsIgnoreCase(cleanId)) {
                existing = d;
                break;
            }
        }

        if (existing != null) {
            if (name != null && !name.trim().isEmpty()) existing.setDeviceName(name);
            if (ip != null && !ip.trim().isEmpty()) existing.setLastKnownIp(ip);
            if (port > 0) existing.setPort(port);
            existing.setLastSeen(System.currentTimeMillis());
        } else {
            pairedDevices.add(new PairedDevice(cleanId, name, ip, port, System.currentTimeMillis()));
        }
        saveConfig();
    }

    public synchronized void setDeviceOnline(String deviceId, boolean online) {
        if (deviceId == null) return;
        for (PairedDevice d : pairedDevices) {
            if (d.getDeviceId().equalsIgnoreCase(deviceId.trim())) {
                d.setOnline(online);
                if (online) {
                    d.setLastSeen(System.currentTimeMillis());
                }
            }
        }
    }

    public synchronized void removePairedDevice(String id) {
        if (id == null) return;
        pairedDevices.removeIf(d -> d.getDeviceId().equalsIgnoreCase(id.trim()));
        saveConfig();
    }

    public List<PairedDevice> getPairedDevices() {
        return Collections.unmodifiableList(new ArrayList<>(pairedDevices));
    }

    public synchronized PairedDevice getPairedDevice(String deviceId) {
        if (deviceId == null) return null;
        for (PairedDevice d : pairedDevices) {
            if (d.getDeviceId().equalsIgnoreCase(deviceId.trim())) {
                return d;
            }
        }
        return null;
    }

    public synchronized FolderConfig getPrimarySharedFolder() {
        if (sharedFolders.isEmpty()) {
            String defaultPath = System.getProperty("user.home") + File.separator + "ConnectaSync";
            FolderConfig fc = new FolderConfig("default", "Default Sync Folder", defaultPath, false);
            sharedFolders.add(fc);
            saveConfig();
            return fc;
        }
        return sharedFolders.get(0);
    }

    public synchronized void addSharedFolder(String path, String label) {
        if (path == null || path.trim().isEmpty()) return;
        String id = "folder-" + System.currentTimeMillis();
        sharedFolders.add(new FolderConfig(id, label, path.trim(), false));
        saveConfig();
    }

    public List<FolderConfig> getSharedFolders() {
        return Collections.unmodifiableList(new ArrayList<>(sharedFolders));
    }

    public String getLocalDeviceName() {
        return localDeviceName;
    }

    public void setLocalDeviceName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.localDeviceName = name.trim();
            saveConfig();
        }
    }

    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        saveConfig();
    }

    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        saveConfig();
    }
}
