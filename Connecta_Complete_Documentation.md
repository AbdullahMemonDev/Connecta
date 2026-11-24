# CONNECTA: SEAMLESS LAN FOLDER SYNCHRONIZATION SYSTEM
## Complete Architectural Specification, Technical Reference & University Viva Defense Manual

---

## 1. Project Overview & Core Features

### 1.1 Executive Summary
**Connecta** is a high-performance, decentralized, peer-to-peer (P2P) desktop folder synchronization application engineered in Java using Java Swing and AWT. It is designed to establish an encrypted-ready, zero-cloud synchronization tunnel between two computing devices situated on the same Local Area Network (LAN) or Wi-Fi network. 

In traditional consumer file synchronization solutions (such as Google Drive, Dropbox, or OneDrive), file modifications are uploaded to a remote third-party cloud server before being downloaded by peer devices. This approach suffers from:
1. **Severe Bandwidth Waste:** Pushing hundreds of megabytes or gigabytes over the public internet when both devices sit just feet apart on the same local router.
2. **Privacy and Security Vulnerabilities:** Uploading confidential files to remote commercial servers.
3. **Cloud Quotas & Internet Dependency:** Total failure to synchronize when the external Internet uplink is severed or throttled.

Connecta solves these limitations by establishing **direct, offline, full-duplex TCP socket pipelines** combined with **UDP local subnet broadcast discovery**. Files flow directly from Device A to Device B over local Ethernet/Wi-Fi links at physical wire speeds (often 30–80+ MB/s on Gigabit Wi-Fi/LAN), requiring zero internet access, zero third-party registration, and zero cloud intermediaries.

```
+-------------------------------------------------------------------------------+
|                             CONNECTA P2P TOPOLOGY                             |
+-------------------------------------------------------------------------------+
|                                                                               |
|   +-----------------------+                       +-----------------------+   |
|   |   LAPTOP A (Peer 1)   |                       |   LAPTOP B (Peer 2)   |   |
|   |  ID: C78A6CAD         |                       |  ID: 4F2A91BC         |   |
|   |  IP: 192.168.20.195   |                       |  IP: 192.168.20.194   |   |
|   |  Folder: D:\SharedA   |                       |  Folder: C:\SharedB   |   |
|   +-----------+-----------+                       +-----------+-----------+   |
|               |                                               |               |
|               |       UDP Beacon Discovery (Port 21027)       |               |
|               |< - - - - - - - - - - - - - - - - - - - - - - >|               |
|               |                                               |               |
|               |    Direct TCP Socket Streamer (Port 8080)     |               |
|               |==============================================>| (Chunks/Sync) |
|               |<==============================================| (Chunks/Sync) |
|               |                                               |               |
|   +-----------v-----------------------------------------------v-----------+   |
|   |                 LOCAL PHYSICAL LAN / WI-FI ROUTER                     |   |
|   |            (Zero Internet Required / 100% Private Offline)            |   |
|   +-----------------------------------------------------------------------+   |
+-------------------------------------------------------------------------------+
```

---

### 1.2 Inspiration from Syncthing
Connecta draws direct architectural inspiration from **[Syncthing](https://syncthing.net/)**, the open-source decentralized file synchronization engine:
- **Block-Based / Chunk-Based Streaming:** Instead of treating files as monolithic memory blobs, files are partitioned into uniform 8192-byte (8KB) sequential memory chunks. This guarantees constant $O(1)$ memory consumption and avoids `OutOfMemoryError` even on multi-gigabyte files.
- **Cryptographic Hash Verification:** Every file has its SHA/MD5 digest recorded and verified before it is committed to disk, preventing truncated or corrupt files from overwriting healthy data.
- **Continuous Local Daemon Monitoring:** Using operating-system-level notification hooks (via Java NIO `WatchService`), the shared directory is constantly surveyed for mutations.
- **Decentralized Pairwise Trust:** Devices are paired via unique device identifiers rather than vulnerable centralized user accounts.

---

### 1.3 Detailed Feature-by-Feature Breakdown

#### 1. Permanent ID-Based Pairing
- **The Problem:** LAN IP addresses are assigned dynamically by router DHCP. Every time a laptop disconnects, switches Wi-Fi access points, or restarts, its IPv4 address changes (e.g., from `192.168.1.15` to `192.168.1.42`), breaking traditional IP-based pairing.
- **Connecta's Solution:** Connecta decouples device identity from the physical IP. Upon initial installation, `ConfigManager` generates an immutable **Permanent 8-character Hex Connecta ID** (e.g., `C78A6CAD`) derived from cryptographic UUID entropy. This ID is saved permanently in `config.properties`. When pairing, users enter only their partner's 8-character ID. The application resolves the current LAN IP behind the scenes.

#### 2. UDP LAN Auto-Discovery Daemon (`LanDiscoveryDaemon`)
- Operating on UDP port `21027`, Connecta runs a continuous background beacon thread that broadcasts a discovery payload (`CONNECTA_BEACON|ID|DeviceName|IP|Port`) every 3 seconds to both the subnet-directed broadcast address (e.g., `192.168.20.255`) and the global broadcast address `255.255.255.255`.
- A concurrent listener thread receives incoming beacons from any other Connecta instance on the subnet. When a beacon with a paired peer's Connecta ID is intercepted, Connecta automatically updates its internal IP table and triggers instant reconnection without requiring any manual user interaction.

#### 3. Persistent Configuration & Automatic Background Reconnection
- All runtime settings (Permanent ID, machine hostname, primary shared folder path, UI theme, sound toggle, and paired device records) are persistently serialized in `D:\Projects\Connecta\config.properties`.
- Once two laptops pair successfully even once, their identities are committed to persistent storage. Upon subsequent launches, `AppManager` inspects the saved peer list and spins up an automatic reconnection daemon that continuously probes the LAN via UDP and TCP. The moment both laptops are active on the same subnet, they establish a session automatically.

#### 4. Real-Time Bidirectional Folder Synchronization
- When any file is created, modified, renamed, or deleted inside the designated shared folder on Device A, Connecta’s `FolderWatcherDaemon` intercepts the OS filesystem event.
- After debouncing file locks to ensure writing applications (such as Microsoft Word or Notepad) have finished writing, the file is chunked into 8KB buffers and streamed across the TCP socket to Device B.
- Device B receives the chunks into an isolated temporary staging file (`<filename>.connecta.tmp`), performs an MD5 checksum integrity audit, and atomically moves it to its final destination path.

#### 5. Catch-Up Synchronization (Offline Difference Reconciliation)
- If Device B was turned off or disconnected while Device A modified files, Connecta executes an automatic **Catch-Up Synchronization** sequence immediately upon reconnection.
- Device A generates an in-memory directory manifest containing relative paths and current MD5 checksums of all local files using an **N-ary Tree Recursive Depth-First Search (DFS)** traversal.
- This manifest is transmitted to Device B via a `MANIFEST_SYNC` packet. Both peers compare remote versus local hashes and queue missing or outdated files for automatic transmission.

#### 6. Manual Ad-Hoc File Transmission
- In addition to automated background synchronization, the UI provides a **"Choose & Send File"** button. Users can pick any file from their filesystem via a native file chooser dialog, which immediately packages, chunks, and streams the selected file to the connected peer regardless of whether it resides in the shared folder.

#### 7. Live Transfer Telemetry & Bandwidth Meters
- During active transfers, `SocketStreamer` calculates instantaneous transfer rates using interval byte counters (`bytesSentInInterval`, `bytesReceivedInInterval`) sampled every 1000 milliseconds.
- The UI displays:
  - Real-time Upload Rate (e.g., `4.8 MB/s`) and Download Rate (`12.3 MB/s`).
  - Total Files Sent, Files Received, and Cumulative Transfers.
  - Active transfer progress bar with percentage and filename status.
  - Total shared folder metrics (total files, subfolders, and cumulative storage size).
  - Continuous application uptime ticker (`HH:MM:SS`).

#### 8. Dynamic Dual-Theme UI (Cyber Neon Dark Mode vs. Clean Modern Light Mode)
- **Cyber Neon Dark Theme:** Built for tech enthusiasts and terminal aesthetics, utilizing a deep obsidian background (`#090D1C`), card navy (`#111B35`), electric cyan (`#00D9FF`), neon violet (`#8055FF`), and emerald green (`#34D399`).
- **Clean Modern Light Theme:** A high-contrast daylight aesthetic utilizing soft mist backgrounds (`#EFF5FC`), crisp white cards (`#FFFFFF`), and deep slate typography (`#17233A`).
- The theme can be toggled instantly via a header button and is persisted across sessions in `config.properties`.

#### 9. Device Management, Pause/Resume & Safe Removal
- **Pause/Resume:** Users can pause folder watching with one click to perform batch local edits without triggering mid-edit network syncs.
- **Unpair/Remove Device:** Users can remove paired devices from the dashboard with confirmation dialogs, revoking automatic reconnection rights.
- **LIFO Stack Undo Transfer:** A one-click undo mechanism backed by a `Stack<String>` allows users to roll back the most recently synced file.

---

## 2. Complete System Architecture & Folder Structure

### 2.1 Workspace Directory Structure
The Connecta codebase is strictly segregated into modular packages following clean architecture principles:

```
D:\Projects\Connecta\
├── config.properties                       # Local persistent configuration file
├── bin/                                    # Compiled JVM bytecode (.class files)
│   ├── core/
│   ├── filesystem/
│   ├── gui/
│   ├── network/
│   └── test/
└── src/                                    # Source code directory
    ├── core/
    │   ├── AppManager.java                 # Central Application Orchestrator / Facade
    │   └── ConfigManager.java              # Persistent Configuration & Storage Manager
    ├── filesystem/
    │   ├── FileChunkManager.java           # 8KB Chunking, Staging & Reassembly Engine
    │   ├── FolderWatcherDaemon.java        # Java NIO WatchService & N-ary Tree DFS Scanner
    │   └── SyncStateManager.java           # O(1) Concurrent Hash Map & Anti-Loop Engine
    ├── gui/
    │   ├── ConnectaMainWindow.java         # Master JFrame, CardLayout Host & Entry Point
    │   ├── ConnectionPanel.java            # Aesthetic Device Pairing & Setup Screen
    │   ├── SplashPanel.java                # Animated Launch Splash View
    │   ├── SyncDashboardPanel.java         # Syncthing Two-Column Control Center
    │   └── WelcomePanel.java               # First-Time Onboarding & Feature Introduction
    ├── network/
    │   ├── FilePacket.java                 # Binary Network Packet Framing Protocol
    │   ├── IDResolver.java                 # NetworkInterface Scanner & LAN IPv4 Resolver
    │   ├── LanDiscoveryDaemon.java         # UDP Subnet Broadcast & Peer Discovery Daemon
    │   ├── NetworkManager.java             # TCP ServerSocket Host & Socket Connector
    │   └── SocketStreamer.java             # Bounded FIFO Queue Network Streaming Engine
    └── test/
        └── ConnectaVerificationTest.java   # Automated Regression & DSA Verification Suite
```

---

### 2.2 Deep-Dive File Catalog (What Every Single `.java` File Does)

| Package | Class Name | Responsibilities & Architectural Role |
| :--- | :--- | :--- |
| `core` | **`AppManager.java`** | **The Brain / Orchestrator of the Application.** Implements the Singleton and Mediator design patterns. Coordinates all interactions between the GUI, network threads, and filesystem daemons. Manages connection state, dispatches UI events onto the Swing Event Dispatch Thread (EDT), queues outgoing transfers via a single-thread executor to prevent packet interleaving, maintains the LIFO undo history stack, and debounces catch-up sync. |
| `core` | **`ConfigManager.java`** | **Persistent Storage Engine.** Implements a thread-safe Singleton managing reading and writing to `config.properties`. Generates and persists the permanent 8-character Connecta ID, serializes paired devices (`deviceId\|name\|ip\|port\|lastSeen`), stores shared folder paths, and maintains user preferences like theme and sound. |
| `filesystem` | **`FileChunkManager.java`** | **Disk I/O & Memory Safety Engine.** Splits files into uniform 8192-byte (`byte[]`) primitive buffers using strict memory buffer isolation (`Arrays.copyOf`). On the receiving side, it writes incoming chunks into a temporary file (`.connecta.tmp`), cryptographically validates the final MD5 checksum, and performs an atomic filesystem replace via `Files.move()` to prevent partial writes. |
| `filesystem` | **`FolderWatcherDaemon.java`** | **Operating System File Event Listener.** Uses Java NIO `WatchService` to monitor the shared folder. Because standard `WatchService` is non-recursive, it executes an **N-ary Tree Recursive Depth-First Search (DFS)** to visit and register every sub-folder. Incorporates an intelligent file stability debouncer to ensure files are fully written by local software before network transmission. |
| `filesystem` | **`SyncStateManager.java`** | **State Tracking & Loop Prevention Engine.** Maintains an in-memory `ConcurrentHashMap<String, String>` mapping normalized relative file paths to their latest MD5 checksums. Provides $O(1)$ lookup to detect local vs. remote modifications and eliminates infinite ping-pong sync loops. |
| `gui` | **`ConnectaMainWindow.java`** | **Master Graphical Window.** The `JFrame` container hosting a `CardLayout` that switches between `SplashPanel`, `WelcomePanel`, `ConnectionPanel`, and `SyncDashboardPanel`. Contains the main application entry point (`public static void main`) and configures system-wide anti-aliasing. |
| `gui` | **`ConnectionPanel.java`** | **Device Pairing Interface.** Renders the local Connecta ID with a one-click copy button, shows detected local LAN IP, displays the current shared directory, and provides input fields for entering a partner's ID to initiate connection. |
| `gui` | **`SplashPanel.java`** | **Launch Screen.** Custom-painted Cyber Neon splash screen rendered using Java 2D graphics with linear gradient fills, glowing logos, and status text displayed during startup. |
| `gui` | **`SyncDashboardPanel.java`** | **Main Control Center.** Syncthing-inspired two-column dashboard: Left column displays folder cards, file counts, storage stats, and folder actions; Right column displays telemetry dials, live upload/download bandwidth, peer presence cards, manual file send buttons, and the LIFO undo button. |
| `gui` | **`WelcomePanel.java`** | **Onboarding View.** Displayed on pristine installations with zero paired devices. Introduces users to Connecta's P2P concept and guides them into pairing. |
| `network` | **`FilePacket.java`** | **Binary Socket Packet Framing Protocol.** Encapsulates all data transmitted across the TCP socket. Implements custom binary serialization and deserialization via `DataOutputStream` and `DataInputStream` for packet types: `HANDSHAKE`, `HEARTBEAT`, `FILE_START`, `FILE_CHUNK`, `FILE_COMPLETE`, `FILE_DELETE`, `UNDO_ACTION`, and `MANIFEST_SYNC`. |
| `network` | **`IDResolver.java`** | **LAN Network Interface Detective.** Iterates through physical `NetworkInterface` hardware cards on the OS. Filters out loopback (`127.0.0.1`), virtual adapters (VirtualBox, VMware, WSL, Hyper-V), and public WAN IPs, isolating the valid private IPv4 LAN address (`192.168.x.x`, `10.x.x.x`, `172.16.x.x–172.31.x.x`). |
| `network` | **`LanDiscoveryDaemon.java`** | **UDP LAN Beacon Engine.** Runs concurrent UDP sender and listener threads on port `21027`. Broadcasts periodic identity beacons across local subnets and maintains an active thread-safe registry of discovered peers for zero-configuration automatic reconnection. |
| `network` | **`NetworkManager.java`** | **TCP Server & Client Socket Manager.** Hosts the background `ServerSocket` daemon (defaulting to port `8080` with automatic fallback to `8081–8084` if occupied). Handles incoming socket connections, executes outbound connection attempts with socket timeouts, and instantiates `SocketStreamer` sessions. |
| `network` | **`SocketStreamer.java`** | **Asynchronous Socket Streaming Engine.** Bridges Java I/O with network sockets. Employs an `ArrayBlockingQueue<FilePacket>` with a bounded capacity of 64 chunks to provide **automatic backpressure**. Houses dedicated background sender and receiver threads, periodic heartbeats, and live bandwidth rate calculation. |
| `test` | **`ConnectaVerificationTest.java`** | **Automated Regression Test Suite.** Validates ID persistence, LAN IP subnet filtering, cross-platform path normalization, 0-byte file packet framing, and 8KB chunk staging and MD5 reassembly without running the GUI. |

---

### 2.3 System Architecture & Data Flow Diagram

```
+========================================================================================================+
|                                      CONNECTA SYSTEM ARCHITECTURE                                      |
+========================================================================================================+

     [ PRESENTATION LAYER (GUI) ]
     +---------------------------------------------------------------------------------------------+
     |  ConnectaMainWindow (CardLayout: Splash -> Welcome -> ConnectionPanel -> SyncDashboardPanel)|
     +---------------------------------------------------------------------------------------------+
                                                 | User Actions (Pair, Send, Rescan, Theme, Undo)
                                                 v
     [ ORCHESTRATION & MEDIATOR LAYER (CORE) ]
     +---------------------------------------------------------------------------------------------+
     |                                      AppManager (Singleton)                                 |
     |  - Coordinates GUI, Network & Filesystem        - Manages Event Dispatch (EDT)              |
     |  - Single-Thread Outgoing Transmission Queue   - LIFO Stack Undo History (Stack<String>)    |
     |  - Catch-up Sync Debounce Lock                 - Dynamic Telemetry Aggregator               |
     +---------------------------------------------------------------------------------------------+
               |                               |                                      |
               v                               v                                      v
     [ CONFIGURATION ]              [ FILESYSTEM ENGINE ]                   [ NETWORK ENGINE ]
     +--------------------+   +------------------------------------+   +--------------------------------+
     |   ConfigManager    |   |         FolderWatcherDaemon        |   |         NetworkManager         |
     | - config.properties|   | - Java NIO WatchService            |   | - ServerSocket (0.0.0.0:8080)  |
     | - Permanent ID     |   | - N-ary Tree DFS Scanner           |   | - Outbound Client Socket       |
     | - Paired Devices   |   | - File Stability Debouncer         |   +--------------------------------+
     | - Shared Folders   |   +------------------------------------+                   |
     +--------------------+                    |                                       v
                                               v                       +--------------------------------+
                              +------------------------------------+   |         SocketStreamer         |
                              |          SyncStateManager          |   | - Bounded FIFO Queue (cap: 64) |
                              | - ConcurrentHashMap (Path -> MD5)  |   | - Sender & Listener Threads    |
                              | - O(1) Loop Prevention             |   | - 1s Interval Bandwidth Meters |
                              +------------------------------------+   +--------------------------------+
                                               |                                       |
                                               v                                       v
                              +------------------------------------+   +--------------------------------+
                              |          FileChunkManager          |   |      LanDiscoveryDaemon        |
                              | - 8KB byte[] Isolated Buffers      |   | - UDP Port 21027 Broadcast     |
                              | - Staging (<file>.connecta.tmp)    |   | - Auto-Discovery & Reconnect   |
                              | - Atomic Move & MD5 Checksum       |   +--------------------------------+
                              +------------------------------------+                   |
                                               |                                       v
                                               |                              +------------------+
                                               +=======> [ LOCAL LAN ] <======|   FilePacket     |
                                                         (Physical Wi-Fi)     | Binary Protocol  |
                                                                              +------------------+
```

---

## 3. End-to-End Working (Step-by-Step Lifecycle)

### 3.1 Startup & Bootstrapping Sequence
1. **JVM Initialization:** The user executes the application. In `ConnectaMainWindow.main()`, system anti-aliasing hints are activated (`awt.useSystemAAFontSettings = on`).
2. **Configuration Loading (`ConfigManager`):**
   - The singleton `ConfigManager.getInstance()` reads `config.properties` from `D:\Projects\Connecta`.
   - If `permanent.connecta.id` does not exist (fresh installation), it generates an 8-character hexadecimal string from `UUID.randomUUID()` and immediately flushes it to disk.
   - Restores saved paired devices, shared folder path (`D:\Projects\Connecta\Shared` by default), UI theme, and audio preferences.
3. **Core Engine Launch (`AppManager.start()`):**
   - **Local IP Resolution:** `IDResolver.getLocalIPv4Address()` scans the host's active network hardware to isolate the physical LAN IPv4 address (e.g., `192.168.20.195`).
   - **TCP Server Binding:** `NetworkManager.startServer()` attempts to bind a `ServerSocket` to wildcard `0.0.0.0:8080`. If port 8080 is in use, it gracefully falls back through ports 8081–8084.
   - **UDP Discovery Launch:** `LanDiscoveryDaemon.start()` starts the UDP broadcast beacon thread and listener socket on port `21027`.
   - **Filesystem Daemon:** `FolderWatcherDaemon` launches, performing an initial recursive N-ary Tree scan of the shared folder and registering native OS watch keys.
   - **Auto-Reconnect Daemon:** A scheduled timer begins checking every 5 seconds for known paired peers that are currently offline, attempting auto-reconnect via discovered UDP IPs or last-known addresses.
4. **UI Navigation:**
   - `SplashPanel` renders for 2.2 seconds.
   - If `ConfigManager` has at least one paired device recorded, Connecta skips the onboarding screen and transitions directly into `SyncDashboardPanel` (attempting automatic connection in the background).
   - If no devices are paired, it displays `WelcomePanel` / `ConnectionPanel`.

---

### 3.2 Device Discovery & Pairing Handshake

```
LAPTOP A (192.168.20.195)                                LAPTOP B (192.168.20.194)
      |                                                                |
      | 1. UDP Beacon: "CONNECTA_BEACON|C78A6CAD|LaptopA|...|8080"      |
      |--------------------------------------------------------------->| (Intercepted on Port 21027)
      |                                                                |
      | 2. User enters Partner ID "C78A6CAD" on Laptop B                |
      |    (LanDiscoveryDaemon resolves ID to 192.168.20.195:8080)     |
      |                                                                |
      | 3. TCP SYN Connect Request to 192.168.20.195:8080              |
      |<---------------------------------------------------------------|
      |                                                                |
      | 4. TCP ServerSocket.accept() creates active Socket             |
      |--------------------------------------------------------------->|
      |                                                                |
      | 5. HANDSHAKE Packet: "4F2A91BC|LaptopB|8080|1.0.0"             |
      |<---------------------------------------------------------------|
      |                                                                |
      | 6. Mutual Handshake Verification & Persistent Config Save      |
      |--------------------------------------------------------------->|
      |                                                                |
      | 7. Session Established: Status -> "CONNECTED"                  |
      |    Catch-Up Sync Triggered                                     |
```

1. **Discovery:** Both laptops broadcast UDP beacons. Laptop B's `LanDiscoveryDaemon` stores Laptop A's ID `C78A6CAD` mapped to IP `192.168.20.195` and port `8080`.
2. **Initiation:** The user on Laptop B enters Laptop A's ID `C78A6CAD` and clicks **Connect Device**.
3. **Resolution:** `IDResolver` checks `LanDiscoveryDaemon`. If present, it resolves the ID to `192.168.20.195`. (If not yet seen via UDP, it allows direct LAN IPv4 fallback).
4. **TCP Connection:** `NetworkManager.connectToPeer("192.168.20.195", 8080)` opens a TCP connection with a 4000ms timeout on a dedicated worker thread (never blocking the Swing EDT).
5. **Handshake Protocol:** Immediately upon connection, both sides exchange a `HANDSHAKE` packet containing their Connecta ID, device hostname, and listening port.
6. **Persistence:** `ConfigManager` adds the peer to its persistent paired devices list in `config.properties`.
7. **Transition:** Both interfaces update their status pills to emerald green (`● CONNECTED`) and trigger Catch-Up Sync.

---

### 3.3 Real-Time File Synchronization Pipeline

```
+---------------------------------------------------------------------------------------------------+
|                        REAL-TIME FILE SYNCHRONIZATION PIPELINE (STEP-BY-STEP)                     |
+---------------------------------------------------------------------------------------------------+

[ SENDER: DEVICE A ]
 1. User saves "Report.pdf" into D:\SharedFolder\docs\
 2. Java NIO WatchService intercepts ENTRY_CREATE / ENTRY_MODIFY
 3. FolderWatcherDaemon debounces file:
    - Waits across consecutive intervals (500ms * 2 = 1.0s)
    - Verifies file size is stable and file is readable via FileInputStream
 4. Anti-Loop Check: SyncStateManager computes MD5. If already matching syncHistory, abort!
 5. AppManager dispatches task to Single-Thread Outgoing Executor:
    a. Sends FILE_START packet (Path: "docs/Report.pdf", Size: 24576, Chunks: 3, MD5: 9a8b7c...)
    b. FileChunkManager splits file into 8192-byte chunks:
       - Buffer Isolation: byte[] isolatedChunk = Arrays.copyOf(buffer, bytesRead)
       - Pushes FILE_CHUNK packet into ArrayBlockingQueue<FilePacket>(64) [BACKPRESSURE]
    c. SocketStreamer sender thread dequeues chunk and streams bytes over TCP Socket
    d. Sends FILE_COMPLETE packet (Path: "docs/Report.pdf", MD5: 9a8b7c...)
 6. Push path onto LIFO Undo Stack (transferHistoryStack.push("docs/Report.pdf"))

                           =====================================>
                                  STREAMED ACROSS LAN TCP
                           =====================================>

[ RECEIVER: DEVICE B ]
 1. SocketStreamer listener thread reads binary stream into FilePacket
 2. On FILE_START:
    - Normalizes path ("docs/Report.pdf")
    - Pre-records path in recentlyReceivedFiles anti-loop cache
    - Deletes any stale staging file (docs/Report.pdf.connecta.tmp)
 3. On FILE_CHUNK:
    - FileChunkManager appends byte[] to docs/Report.pdf.connecta.tmp via FileOutputStream
 4. On FILE_COMPLETE:
    - Integrity Check: Computes MD5 of docs/Report.pdf.connecta.tmp
    - If MD5 == expected MD5:
      * Pre-records MD5 in SyncStateManager so local FolderWatcher will ignore it
      * Atomically commits file: Files.move(tmpPath, finalPath, ATOMIC_MOVE, REPLACE_EXISTING)
      * Pushes path onto Receiver's LIFO Undo Stack
      * Plays success audio chime and updates GUI folder cards and telemetry
    - If MD5 != expected MD5:
      * Discards corrupted temp file immediately and logs warning
+---------------------------------------------------------------------------------------------------+
```

---

### 3.4 Disconnection & Auto-Reconnect Recovery
1. **Heartbeat Probing:** When no active file chunks are flowing, `SocketStreamer`'s heartbeat timer fires every 2500ms, transmitting a lightweight `HEARTBEAT` packet.
2. **Failure Detection:** `SocketStreamer` verifies whether data has been received within the last 6000ms (`HEARTBEAT_TIMEOUT_MS = 6000`). If a partner laptop closes its lid, turns off Wi-Fi, or crashes, the socket read times out.
3. **Graceful Teardown:** The socket is cleanly closed. `NetworkManager` fires `onPeerDisconnected("Heartbeat timeout")`. `AppManager` updates the UI to `● OFFLINE` (or `● DISCONNECTED`).
4. **Auto-Reconnect Loop:** `AppManager`'s auto-reconnect timer wakes up every 5 seconds. It queries `LanDiscoveryDaemon` for the offline peer's ID. As soon as the partner reopens their laptop and re-associates with Wi-Fi, its UDP beacon is heard. `AppManager` automatically initiates a background TCP connection, re-establishes the session, and triggers Catch-Up Sync to transfer any files edited while offline.

---

## 4. Deep Dive into Backend Classes, Methods & Framing Protocol

### 4.1 Binary Socket Packet Framing Protocol

To achieve high-throughput data transfer and prevent packet corruption, Connecta uses a custom, strongly typed binary framing protocol implemented in `network.FilePacket` and transmitted via `java.io.DataOutputStream` and `java.io.DataInputStream`.

#### Binary Frame Layout (Over the Wire)
Every packet on the TCP socket conforms to the following strict byte layout:

```
+------------+----------------+--------------+-----------------+---------------+----------------+----------------+--------------------+
| Field      | Type Ordinal   | RelativePath | FileSize        | SequenceNum   | TotalChunks    | Checksum       | DataLength (N)     | Raw Chunk Data     |
+------------+----------------+--------------+-----------------+---------------+----------------+----------------+--------------------+
| Data Type  | 1 Byte (byte)  | UTF-8 String | 8 Bytes (long)  | 4 Bytes (int) | 4 Bytes (int)  | UTF-8 String   | 4 Bytes (int)      | N Bytes (byte[])   |
+------------+----------------+--------------+-----------------+---------------+----------------+----------------+--------------------+
| Wire Repr. | out.writeByte  | out.writeUTF | out.writeLong   | out.writeInt  | out.writeInt   | out.writeUTF   | out.writeInt       | out.write(data)    |
+------------+----------------+--------------+-----------------+---------------+----------------+----------------+--------------------+
```

#### Protocol Serialization Method (`FilePacket.java`)
```java
public void writeToStream(DataOutputStream out) throws IOException {
    synchronized (out) {
        out.writeByte(type.ordinal());                          // 1. Packet type identifier (0-7)
        out.writeUTF(relativePath != null ? relativePath : ""); // 2. Cross-platform relative path
        out.writeLong(fileSize);                                // 3. Total file byte size
        out.writeInt(sequenceNumber);                           // 4. Current chunk index (0..total-1)
        out.writeInt(totalChunks);                              // 5. Total chunks in transfer
        out.writeUTF(checksum != null ? checksum : "");         // 6. Expected MD5 digest
        if (chunkData != null && chunkData.length > 0) {
            out.writeInt(chunkData.length);                     // 7. Length of binary chunk payload
            out.write(chunkData);                               // 8. Raw byte array (up to 8192 bytes)
        } else {
            out.writeInt(0);                                    // 0-byte payload marker
        }
        out.flush();
    }
}
```

#### Packet Types & Their Purpose

| Packet Type | Ordinal | Payload Content | Purpose |
| :--- | :---: | :--- | :--- |
| `HANDSHAKE` | `0` | `ID\|DeviceName\|Port\|Version` | Transmitted immediately upon connection to mutually authenticate identities. |
| `HEARTBEAT` | `1` | Empty / Keep-alive | Periodic ping sent every 2.5s to maintain socket state and detect dead connections. |
| `FILE_START`| `2` | Path, Total Size, Total Chunks, MD5 | Announces an incoming file transmission and triggers staging allocation on receiver. |
| `FILE_CHUNK`| `3` | Sequence Number, 8KB `byte[]` | Carries an isolated 8KB block of file data. |
| `FILE_COMPLETE` | `4`| Path, Final MD5 Checksum | Signals EOF. Prompts receiver to verify digest and atomically rename the file. |
| `FILE_DELETE` | `5` | Relative File Path | Instructs peer to remove a file deleted in the local shared directory. |
| `UNDO_ACTION` | `6` | Relative File Path | Triggers rollback and deletion of a recently synced file popped from the LIFO stack. |
| `MANIFEST_SYNC`| `7`| `path:md5,path:md5,...` | Transmits full directory state during Catch-Up Synchronization. |

---

### 4.2 File Chunking, Buffering & Reassembly Pipeline (`FileChunkManager`)

#### Why 8KB Chunks?
`FileChunkManager.CHUNK_SIZE = 8192` (8 Kilobytes). This size is mathematically optimal for network transmission:
1. It matches standard operating system filesystem disk cluster and block sizes (typically 4KB or 8KB).
2. It aligns comfortably within TCP socket receive and send window buffers, maximizing network card MTU efficiency.
3. It bounds the per-packet serialization overhead to less than $0.5\%$.

#### Buffer Isolation (Preventing Data Corruption)
A common bug in asynchronous socket applications is reusing a single `byte[] buffer` array across multiple read loops. If the producer reads the next chunk before the network worker sends the previous one, data on the wire becomes corrupted. Connecta eliminates this completely:
```java
// FileChunkManager.java (Buffer Isolation Implementation)
byte[] readBuffer = new byte[CHUNK_SIZE];
int bytesRead;
while ((bytesRead = fis.read(readBuffer)) != -1) {
    // CRITICAL: Always clone exact bytes read to isolate memory
    byte[] isolatedChunk = Arrays.copyOf(readBuffer, bytesRead);
    callback.onChunkReady(isolatedChunk, sequenceNumber++, totalChunks, totalBytesRead, fileSize);
}
```

#### Atomic Staging and Commit (`.connecta.tmp`)
To ensure peer readers never access partially written or truncated files:
1. Chunks are appended to `<fileName>.connecta.tmp`.
2. Upon `FILE_COMPLETE`, `finalizeReassembly()` verifies the MD5 checksum of `.connecta.tmp`.
3. If verified, it executes `Files.move(..., StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)`. On supported filesystems, an atomic move is a pointer swap at the filesystem inode/MFT level, completing in microseconds with zero disk write overhead.

---

### 4.3 Filesystem Monitoring & Stability Debouncing (`FolderWatcherDaemon`)

Operating systems fire file events prematurely while files are still open for writing by word processors, video renderers, or text editors. Reading an actively written file results in 0-byte or corrupted transfers.

Connecta incorporates a multi-pass stability debouncing engine:
```java
// FolderWatcherDaemon.java (File Stability Debouncer)
private boolean waitForFileStability(File file) {
    // 0-byte files require 3 consecutive checks (1.5s); non-empty require 2 checks (1.0s)
    int requiredStableChecks = (file.length() == 0) ? 3 : 2;
    int stableCount = 0;
    long lastSize = -1;

    for (int i = 0; i < 20; i++) {
        long currentLength = file.length();
        if (currentLength == lastSize) {
            stableCount++;
            if (stableCount >= requiredStableChecks) {
                // Verify active read lock accessibility
                try (FileInputStream testFis = new FileInputStream(file)) {
                    testFis.read();
                    return true; // File is completely written and unlocked!
                } catch (IOException locked) {
                    // File is still locked exclusively by writing software
                }
            }
        } else {
            stableCount = 0;
            lastSize = currentLength;
        }
        Thread.sleep(500);
    }
    return false;
}
```

---

## 5. Data Structures & Algorithms (DSA) Used (Viva-Ready Section)

Because Connecta is an enterprise-grade Data Structures and Algorithms project, five fundamental computer science structures were selected and integrated into the core architecture:

---

### DSA 1: Hash Table with Lock Striping (`ConcurrentHashMap<String, String>`)
- **Class & Variable Name:** 
  - `filesystem.SyncStateManager.java` $\to$ `private final ConcurrentHashMap<String, String> syncHistory`
  - `network.LanDiscoveryDaemon.java` $\to$ `private final ConcurrentHashMap<String, DiscoveredPeer> discoveredPeers`
- **Why Chosen over Alternatives:** Standard `java.util.HashMap` is non-thread-safe and causes infinite loops or `ConcurrentModificationException` during concurrent reads/writes. `java.util.Hashtable` or `Collections.synchronizedMap` enforces a single global lock, bottlenecking all threads. `ConcurrentHashMap` uses **lock striping** (bucket-level segment locking), permitting hundreds of concurrent reads while allowing simultaneous non-blocking writes across different hash buckets.
- **Complexity:**
  - **Time Complexity:** $O(1)$ expected time for insertion (`put`), retrieval (`get`), and deletion (`remove`).
  - **Space Complexity:** $O(N)$ where $N$ is the total number of synchronized files.
- **Problem Solved:** **The Infinite Ping-Pong Sync Loop.** When Device A writes a file, Device B receives it and saves it to disk. Device B's local `WatchService` intercepts the new file and would ordinarily attempt to send it right back to Device A, creating an infinite network loop. By storing the normalized relative path and MD5 digest in `syncHistory`, both devices perform an $O(1)$ lookup before transmitting; if the local file's MD5 matches the last synced state, the event is immediately discarded.

---

### DSA 2: Bounded FIFO Queue (`ArrayBlockingQueue<FilePacket>`)
- **Class & Variable Name:** 
  - `network.SocketStreamer.java` $\to$ `private final BlockingQueue<FilePacket> outgoingQueue = new ArrayBlockingQueue<>(64);`
- **Why Chosen over Alternatives:** An unbounded queue (like `ConcurrentLinkedQueue` or `LinkedList`) grows infinitely. If a fast NVMe SSD reads a 2 GB file at 500 MB/s while a Wi-Fi link transfers data at 10 MB/s, the producer will push thousands of 8KB chunks into RAM, rapidly exceeding the JVM maximum heap space and causing a fatal `java.lang.OutOfMemoryError`.
- **Complexity:**
  - **Time Complexity:** $O(1)$ constant time for enqueue (`put`) and dequeue (`take`) via circular array pointer arithmetic.
  - **Space Complexity:** Strictly bounded $O(1)$ auxiliary memory footprint ($64 \times 8\text{ KB} \approx 512\text{ KB}$ maximum in-flight RAM allocation).
- **Problem Solved:** **Automatic Flow Control (Backpressure).** When `outgoingQueue` reaches capacity (64 packets), `outgoingQueue.put(packet)` blocks the disk-reader thread. The disk reader resumes only when the TCP sender thread transmits chunks over the network and calls `outgoingQueue.take()`. Memory consumption remains strictly flat regardless of whether transferring a 100 KB text document or a 10 GB ISO file.

---

### DSA 3: Sequential Memory Arrays (`byte[]` 8KB Buffers)
- **Class & Variable Name:** 
  - `filesystem.FileChunkManager.java` $\to$ `public static final int CHUNK_SIZE = 8192;` and `byte[] isolatedChunk`
  - `network.FilePacket.java` $\to$ `private final byte[] chunkData;`
- **Why Chosen over Alternatives:** High-level object wrappers (such as `ArrayList<Byte>`) introduce massive object-header memory bloat (each boxed `Byte` object consumes 24 bytes in the JVM, representing a $2400\%$ memory penalty). Primitive `byte[]` arrays provide contiguous, sequential physical memory allocation.
- **Complexity:**
  - **Time Complexity:** $O(1)$ random access to any byte offset; $O(K)$ linear time copying where $K = 8192$.
  - **Space Complexity:** $O(1)$ constant memory overhead ($8192$ bytes per active chunk operation).
- **Problem Solved:** Guarantees peak CPU cache spatial locality (L1/L2 cache prefetching) and eliminates memory fragmentation, allowing Connecta to sustain maximum wire transfer speeds without triggering JVM garbage collection pauses.

---

### DSA 4: N-ary Tree Model & Recursive Depth-First Search (DFS)
- **Class & Variable Name:** 
  - `filesystem.FolderWatcherDaemon.java` $\to$ methods `scanAndRegisterTreeDFS(File dir, DFSNodeVisitor visitor)` and `calculateStatsDFS(File dir)`
- **Mathematical Model:**
  - **Root Node:** The shared folder root directory (e.g., `D:\SharedFolder`).
  - **Internal Nodes (Branch Nodes):** Subdirectories of arbitrary branching degree $N$ (folders holding variable numbers of child directories and files).
  - **Leaf Nodes:** Regular files or empty directories.
- **Why Chosen over Alternatives:** Standard Java NIO `WatchService` is non-recursive on Windows and Linux; registering only the root folder leaves subfolders completely unwatched. Recursive DFS explores down each directory branch to the deepest leaf before backtracking.
- **Complexity:**
  - **Time Complexity:** $O(V + E)$ where $V$ is total files and directories, and $E$ is the directory links. Every node in the filesystem tree is traversed exactly once.
  - **Space Complexity:** $O(H)$ auxiliary stack space where $H$ is the maximum depth (height) of the directory tree, bounded by the call stack.
- **Problem Solved:** Automatically catalogs deeply nested folder structures (e.g., `Shared/a/b/c/d/file.txt`), registers them with `WatchService`, and computes real-time recursive folder metrics (total files, total subfolders, and cumulative byte size) for dashboard telemetry.

---

### DSA 5: Last-In-First-Out (LIFO) Stack (`Stack<String>`)
- **Class & Variable Name:** 
  - `core.AppManager.java` $\to$ `private final Stack<String> transferHistoryStack = new Stack<>();`
- **Why Chosen over Alternatives:** Queues or lists require linear searches ($O(N)$) to find the most recent operation. A LIFO Stack mirrors the user mental model of "Undo" by placing the most recent operation at the very top of the stack.
- **Complexity:**
  - **Time Complexity:** $O(1)$ constant time for `push()` and `pop()`.
  - **Space Complexity:** $O(M)$ where $M$ is the depth of recorded transfer history.
- **Problem Solved:** **The Synchronized Undo Feature.** When a user accidentally syncs or receives a file, clicking **"Undo Last Sync (LIFO Stack)"** pops the most recent relative file path from `transferHistoryStack` in $O(1)$ time. Connecta deletes the local file and transmits an `UNDO_ACTION` packet across the TCP socket, causing the remote peer to atomically pop and remove the corresponding file on their side.

---

### Comparative DSA Summary Table (Viva Defense Ready)

| Data Structure | Implementation Class & Field | Time Complexity | Auxiliary Space | Alternative Considered | Engineering Justification |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hash Table** | `SyncStateManager.java`<br>`syncHistory` | $O(1)$ Put / Get / Remove | $O(N)$ | `HashMap`<br>`Hashtable` | `ConcurrentHashMap` provides bucket-level lock striping for non-blocking concurrent thread access; eliminates infinite ping-pong sync loops. |
| **Bounded FIFO Queue** | `SocketStreamer.java`<br>`outgoingQueue` | $O(1)$ Enqueue / Dequeue | $O(1)$ constant<br>(bounded 512 KB) | `LinkedList`<br>`ConcurrentLinkedQueue` | Enforces **Automatic Backpressure**. Halts disk reads when the queue reaches 64 chunks, preventing JVM `OutOfMemoryError`. |
| **Sequential Arrays** | `FileChunkManager.java`<br>`byte[] isolatedChunk` | $O(1)$ Access<br>$O(K)$ Copy ($K=8192$) | $O(1)$ constant<br>(8192 bytes) | `ArrayList<Byte>`<br>`ByteBuffer` heap | Contiguous memory allocation maximizes L1/L2 CPU cache hits; eliminates object-wrapper memory overhead and GC pauses. |
| **N-ary Tree & DFS** | `FolderWatcherDaemon.java`<br>`scanAndRegisterTreeDFS()` | $O(V + E)$ Traversal | $O(H)$ call stack depth | BFS Queue<br>Iterative walk | DFS naturally matches filesystem hierarchy; recursively registers subfolders in `WatchService` and calculates directory stats. |
| **LIFO Stack** | `AppManager.java`<br>`transferHistoryStack` | $O(1)$ Push / Pop | $O(M)$ history depth | Circular Array<br>`ArrayList` | Provides instantaneous $O(1)$ reversal of the most recent sync event; coordinates distributed two-device atomic rollback. |

---

## 6. Top Viva Questions & Answers (Examiner Defense Ready)

#### Q1: Why did you build Connecta as a peer-to-peer (P2P) application rather than using a client-server cloud model?
**Answer:** "A client-server cloud architecture introduces high latency, consumes external internet bandwidth, incurs recurring cloud server costs, and compromises user privacy by routing sensitive files through a third party. Connecta is designed for high-speed local data synchronization: by using direct TCP sockets over LAN, transfers operate at physical network wire speed (30–80+ MB/s on Gigabit LAN) without internet access, with 100% data sovereignty and zero cloud dependency."

---

#### Q2: How does Connecta prevent OutOfMemoryError when transmitting a large file (e.g., a 4 GB video)?
**Answer:** "We enforce two architectural safeguards:
1. **Fixed 8KB Chunking:** `FileChunkManager` never loads an entire file into RAM. It reads files in sequential 8192-byte chunks using primitive `byte[]` arrays ($O(1)$ auxiliary space).
2. **Bounded FIFO Queue with Backpressure:** In `SocketStreamer`, packets pass through an `ArrayBlockingQueue<FilePacket>` with a strict capacity limit of 64 chunks (~512 KB). When the queue fills up, `outgoingQueue.put()` blocks the disk reading thread until the TCP sender thread transmits chunks over the network. Memory utilization remains strictly flat and constant regardless of file size."

---

#### Q3: What is the 'Infinite Ping-Pong Loop' in file synchronization, and how did you solve it using DSA?
**Answer:** "The Infinite Ping-Pong Loop occurs when Device A syncs a file to Device B; Device B writes it to disk, causing Device B's local `WatchService` to fire a creation event and send the exact same file back to Device A, repeating indefinitely. 
We solved this in $O(1)$ time using a `ConcurrentHashMap<String, String>` inside `SyncStateManager`. Before sending or writing, Connecta records the normalized file path and its cryptographic MD5 checksum. When a filesystem event triggers, `SyncStateManager.isAlreadySynced(path, hash)` performs an $O(1)$ lookup. If the checksum matches what was just written by the network thread, the event is immediately discarded."

---

#### Q4: Why did you use TCP instead of UDP for file data transfer?
**Answer:** "TCP is a connection-oriented, byte-stream protocol that guarantees in-order packet delivery, error detection, automatic packet retransmission, and flow/congestion control at the transport layer. A single dropped byte in an executable, PDF, or database file corrupts the entire file. Therefore, TCP is mandatory for file data. However, for peer auto-discovery where speed and broadcast capability are paramount, we use UDP beacons on port 21027."

---

#### Q5: How does Connecta handle dynamic IP changes when switching between Wi-Fi networks?
**Answer:** "Connecta decouples identity from IP addresses. On initial run, `ConfigManager` generates an immutable **Permanent 8-character Connecta ID** (e.g., `C78A6CAD`) stored in `config.properties`. During pairing, users exchange this ID. Our background `LanDiscoveryDaemon` continuously broadcasts UDP beacons containing the Permanent ID alongside the device's current LAN IP. If a router assigns a new IP via DHCP, the peer intercepts the beacon, updates its internal IP routing table, and reconnects automatically."

---

#### Q6: Why is Java NIO `WatchService` insufficient on its own, and how does your N-ary Tree DFS solve it?
**Answer:** "Standard Java NIO `WatchService` is non-recursive on Windows and Linux—it only monitors the immediate folder registered. If a user creates a subfolder `Shared/docs/2026/`, events inside `2026/` are completely ignored. To solve this, `FolderWatcherDaemon` models the directory structure as an **N-ary Tree** and executes a **Recursive Depth-First Search (DFS)** during initialization. It traverses every directory node ($O(V+E)$ complexity) and registers each with the native `WatchService`. When a new directory is created at runtime, it is dynamically registered on the fly."

---

#### Q7: What is the Buffer Reuse Bug, and how did you prevent file corruption during chunk transmission?
**Answer:** "In multithreaded networking, if a producer reads data into a single shared `byte[] buffer` and passes that reference into an asynchronous queue, the next disk read will overwrite the array before the network thread has finished sending the previous chunk. This corrupts the data on the wire. We prevent this by enforcing **strict buffer isolation** in `FileChunkManager`: every read chunk is cloned into an independent array via `Arrays.copyOf(readBuffer, bytesRead)` before being enqueued."

---

#### Q8: How does Connecta prevent partial file reads when a local program is still writing to a file?
**Answer:** "When applications (such as Microsoft Office, Photoshop, or text editors) save files, they lock the file and write data in bursts. If Connecta synchronized immediately on `ENTRY_CREATE`, it would read an incomplete 0-byte or truncated file. `FolderWatcherDaemon` implements a **Stability Debouncer** (`waitForFileStability`): it samples the file size across multiple consecutive intervals (500ms intervals) and verifies that an active `FileInputStream` can open and read the file without OS sharing violations before approving it for network transmission."

---

#### Q9: How is the LIFO Stack utilized in Connecta, and how does it maintain distributed consistency?
**Answer:** "We maintain a `Stack<String>` (`transferHistoryStack`) in `AppManager`. Every completed outgoing or incoming transfer pushes the normalized relative path onto the stack ($O(1)$ push). When the user clicks **'Undo Last Sync'**, the most recent path is popped ($O(1)$ pop). Connecta deletes the local file and simultaneously dispatches an `UNDO_ACTION` packet to the connected peer over the TCP socket. The remote peer pops the file from its stack and deletes it locally as well, achieving distributed state rollback."

---

#### Q10: How does Connecta ensure thread safety between background network daemons and the Swing UI?
**Answer:** "Java Swing is single-threaded; any modification to Swing components outside the Event Dispatch Thread (EDT) causes race conditions, visual artifacts, or deadlocks. Connecta isolates backend worker threads (TCP listeners, UDP daemons, file watchers) from the UI. When `AppManager` receives an event, it uses `SwingUtilities.invokeLater(() -> { ... })` to marshal updates onto the EDT safely. Additionally, shared data structures leverage atomic primitives (`AtomicLong`, `AtomicBoolean`) and thread-safe collections (`ConcurrentHashMap`, `CopyOnWriteArrayList`)."

---

## 7. Build, Verification & Execution Instructions

### 7.1 Compilation
Connecta can be compiled using the standard Java Development Kit (JDK 8 or higher) without requiring external build tools like Maven or Gradle:

```powershell
# Open terminal in D:\Projects\Connecta
cd D:\Projects\Connecta

# Compile all source packages into bin directory
javac -encoding UTF-8 -d bin src\core\*.java src\network\*.java src\filesystem\*.java src\gui\*.java src\test\*.java
```

### 7.2 Running Automated Verification Tests
Connecta includes an automated test harness validating ID persistence, subnet validation, path normalization, 0-byte file handling, and 8KB chunking/reassembly:

```powershell
java -cp bin test.ConnectaVerificationTest
```
*Expected Output: `ALL INTEGRATION VERIFICATION TESTS PASSED SUCCESSFULLY! (8 / 8)`*

### 7.3 Launching the Application
To run the full graphical desktop application:

```powershell
java -cp bin gui.ConnectaMainWindow
```

---
*Connecta — Two Devices. One Connection. Zero Cloud.*
