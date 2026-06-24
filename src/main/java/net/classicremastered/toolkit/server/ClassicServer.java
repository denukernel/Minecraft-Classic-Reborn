package net.classicremastered.toolkit.server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public final class ClassicServer {
    private static final int PORT = 25565;
    private static final int WIDTH = 256;
    private static final int HEIGHT = 64;
    private static final int DEPTH = 256;
    
    private final byte[] blocks = new byte[WIDTH * HEIGHT * DEPTH];
    private final List<ClientHandler> clients = new ArrayList<>();
    private final boolean[] playerIdPool = new boolean[128]; // IDs 0-127

    private final Set<String> admins = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> bannedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> mutedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private void loadConfig() {
        loadSetFromFile("admins.txt", admins);
        loadSetFromFile("bans.txt", bannedPlayers);
    }

    private void loadSetFromFile(String fileName, Set<String> set) {
        File file = new File(fileName);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    set.add(line.toLowerCase());
                }
            }
        } catch (IOException e) {
            log("[ClassicServer] Failed to load " + fileName + ": " + e.getMessage());
        }
    }

    private void saveSetToFile(String fileName, Set<String> set) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            for (String val : set) {
                pw.println(val);
            }
        } catch (IOException e) {
            log("[ClassicServer] Failed to save " + fileName + ": " + e.getMessage());
        }
    }

    private ClientHandler findClient(String name) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.username.equalsIgnoreCase(name)) {
                    return client;
                }
            }
        }
        return null;
    }

    // GUI Components
    private JFrame frame;
    private JTextArea logArea;
    private DefaultListModel<String> playersListModel;
    private JList<String> playersList;
    private JTextField chatInputField;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClassicServer server = new ClassicServer();
            server.createAndShowGUI();
            // Start server socket in a background thread
            new Thread(server::start, "Server-Socket-Thread").start();
        });
    }

    public ClassicServer() {
        loadConfig();
        // Generate a flat world:
        // Stone up to y = 30
        // Dirt at y = 31
        // Grass at y = 32
        // Air above y = 32
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    int index = (y * DEPTH + z) * WIDTH + x;
                    if (y < 30) {
                        blocks[index] = 1; // Stone
                    } else if (y == 30) {
                        blocks[index] = 3; // Dirt
                    } else if (y == 31) {
                        blocks[index] = 2; // Grass
                    } else {
                        blocks[index] = 0; // Air
                    }
                }
            }
        }
    }

    private void createAndShowGUI() {
        // Set premium dark look-and-feel if possible, or standard clean layout
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("Classic Remastered Server - Toolkit");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);
        frame.setLayout(new BorderLayout(5, 5));

        // 1) Log Area (Center)
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(220, 220, 220));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Server Log"
        ));
        frame.add(logScrollPane, BorderLayout.CENTER);

        // 2) Players List (East / Right side)
        playersListModel = new DefaultListModel<>();
        playersList = new JList<>(playersListModel);
        playersList.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JScrollPane playersScrollPane = new JScrollPane(playersList);
        playersScrollPane.setPreferredSize(new Dimension(180, 0));
        playersScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Players Online"
        ));
        frame.add(playersScrollPane, BorderLayout.EAST);

        // 3) Broadcast / Chat Input (South / Bottom side)
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        chatInputField = new JTextField();
        chatInputField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bottomPanel.add(chatInputField, BorderLayout.CENTER);

        JButton sendButton = new JButton("Broadcast");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bottomPanel.add(sendButton, BorderLayout.EAST);
        
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // ActionListener to send messages
        ActionListener sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = chatInputField.getText().trim();
                if (!text.isEmpty()) {
                    if (text.startsWith("/")) {
                        handleConsoleCommand(text);
                    } else {
                        broadcastServerChat(text);
                    }
                    chatInputField.setText("");
                }
            }
        };
        chatInputField.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        log("Server GUI initialized.");
    }

    private void log(String message) {
        System.out.println(message);
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    private void syncPlayersList() {
        SwingUtilities.invokeLater(() -> {
            if (playersListModel != null) {
                playersListModel.clear();
                synchronized (clients) {
                    for (ClientHandler client : clients) {
                        playersListModel.addElement(client.username + " (ID: " + client.playerId + ")");
                    }
                }
            }
        });
    }

    public void start() {
        log("[ClassicServer] Starting Minecraft Classic Server on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("[ClassicServer] Bound to port " + PORT + ". Waiting for players...");
            while (true) {
                Socket socket = serverSocket.accept();
                log("[ClassicServer] Incoming connection from " + socket.getRemoteSocketAddress());
                
                synchronized (clients) {
                    int id = getNextPlayerId();
                    if (id == -1) {
                        log("[ClassicServer] Server is full! Rejecting client.");
                        sendDisconnect(socket, "Server is full!");
                        socket.close();
                        continue;
                    }
                    playerIdPool[id] = true;
                    ClientHandler handler = new ClientHandler(socket, (byte) id);
                    clients.add(handler);
                    handler.start();
                }
            }
        } catch (IOException e) {
            log("[ClassicServer] Server encountered an error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getNextPlayerId() {
        for (int i = 0; i < playerIdPool.length; i++) {
            if (!playerIdPool[i]) return i;
        }
        return -1;
    }

    private void releasePlayerId(int id) {
        if (id >= 0 && id < playerIdPool.length) {
            playerIdPool[id] = false;
        }
    }

    private void sendDisconnect(Socket s, String reason) {
        if (s == null) return;
        try {
            OutputStream out = s.getOutputStream();
            out.write(14); // Disconnect opcode
            writeString(out, reason);
            out.flush();
        } catch (IOException ignored) {}
    }

    // Packet serialization helper
    private static void writeString(OutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[64];
        Arrays.fill(padded, (byte) 32); // Pad with spaces
        System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 64));
        out.write(padded);
    }

    private static String readString(InputStream in) throws IOException {
        byte[] bytes = new byte[64];
        int read = 0;
        while (read < 64) {
            int r = in.read(bytes, read, 64 - read);
            if (r == -1) throw new EOFException();
            read += r;
        }
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    private static void writeShort(OutputStream out, short val) throws IOException {
        out.write((val >>> 8) & 0xFF);
        out.write(val & 0xFF);
    }

    private static short readShort(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        if ((b1 | b2) < 0) throw new EOFException();
        return (short) ((b1 << 8) + b2);
    }

    private static void writeInt(OutputStream out, int val) throws IOException {
        out.write((val >>> 24) & 0xFF);
        out.write((val >>> 16) & 0xFF);
        out.write((val >>> 8) & 0xFF);
        out.write(val & 0xFF);
    }

    private synchronized void updateBlock(int x, int y, int z, byte blockType) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < DEPTH) {
            int index = (y * DEPTH + z) * WIDTH + x;
            blocks[index] = blockType;
        }
    }

    private void broadcast(byte[] packet) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendPacket(packet);
            }
        }
    }

    private void broadcastExclude(byte[] packet, byte excludePlayerId) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.playerId != excludePlayerId) {
                    client.sendPacket(packet);
                }
            }
        }
    }

    private void broadcastServerChat(String msg) {
        String serverMsg = "&d[Server] " + msg;
        log("[Chat] " + serverMsg);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(13); // Chat Message Opcode
            baos.write(-1); // ID -1 for server announcement
            writeString(baos, serverMsg);
            broadcast(baos.toByteArray());
        } catch (IOException ignored) {}
    }

    private void handleConsoleCommand(String fullCommand) {
        String[] parts = fullCommand.trim().split("\\s+", 3);
        if (parts.length == 0) return;
        
        String cmd = parts[0].substring(1).toLowerCase();
        
        if (cmd.equals("help")) {
            log("[Console] Available commands: /help, /kick, /ban, /mute, /unmute, /admin, /deadmin, /spawnfake, /removefake");
            return;
        }
        
        if (cmd.equals("kick")) {
            if (parts.length < 2) {
                log("[Console] Usage: /kick <player> [reason]");
                return;
            }
            String targetName = parts[1];
            String reason = parts.length > 2 ? parts[2] : "Kicked by console";
            ClientHandler target = findClient(targetName);
            if (target == null) {
                log("[Console] Player not found: " + targetName);
            } else {
                sendDisconnect(target.socket, reason);
                target.disconnect();
                broadcastServerChat("&e" + target.username + " was kicked: " + reason);
            }
        } else if (cmd.equals("ban")) {
            if (parts.length < 2) {
                log("[Console] Usage: /ban <player> [reason]");
                return;
            }
            String targetName = parts[1];
            String reason = parts.length > 2 ? parts[2] : "Banned by console";
            
            bannedPlayers.add(targetName.toLowerCase());
            saveSetToFile("bans.txt", bannedPlayers);
            
            ClientHandler target = findClient(targetName);
            if (target != null) {
                sendDisconnect(target.socket, "Banned: " + reason);
                target.disconnect();
            }
            broadcastServerChat("&e" + targetName + " was banned: " + reason);
        } else if (cmd.equals("mute")) {
            if (parts.length < 2) {
                log("[Console] Usage: /mute <player>");
                return;
            }
            String targetName = parts[1];
            mutedPlayers.add(targetName.toLowerCase());
            log("[Console] Muted " + targetName);
            ClientHandler target = findClient(targetName);
            if (target != null) {
                target.sendSystemMessage("&cYou have been muted by console.");
            }
        } else if (cmd.equals("unmute")) {
            if (parts.length < 2) {
                log("[Console] Usage: /unmute <player>");
                return;
            }
            String targetName = parts[1];
            mutedPlayers.remove(targetName.toLowerCase());
            log("[Console] Unmuted " + targetName);
            ClientHandler target = findClient(targetName);
            if (target != null) {
                target.sendSystemMessage("&aYou have been unmuted.");
            }
        } else if (cmd.equals("admin")) {
            if (parts.length < 2) {
                log("[Console] Usage: /admin <player>");
                return;
            }
            String targetName = parts[1];
            admins.add(targetName.toLowerCase());
            saveSetToFile("admins.txt", admins);
            log("[Console] Promoted " + targetName + " to admin.");
            ClientHandler target = findClient(targetName);
            if (target != null) {
                target.sendSystemMessage("&aYou are now an admin.");
            }
        } else if (cmd.equals("deadmin")) {
            if (parts.length < 2) {
                log("[Console] Usage: /deadmin <player>");
                return;
            }
            String targetName = parts[1];
            admins.remove(targetName.toLowerCase());
            saveSetToFile("admins.txt", admins);
            log("[Console] Demoted " + targetName + " from admin.");
            ClientHandler target = findClient(targetName);
            if (target != null) {
                target.sendSystemMessage("&cYou are no longer an admin.");
            }
        } else if (cmd.equals("spawnfake")) {
            if (parts.length < 2) {
                log("[Console] Usage: /spawnfake <name>");
                return;
            }
            String name = parts[1];
            spawnFakePlayer(name);
        } else if (cmd.equals("removefake")) {
            if (parts.length < 2) {
                log("[Console] Usage: /removefake <name>");
                return;
            }
            String name = parts[1];
            removeFakePlayer(name);
        } else {
            log("[Console] Unknown command. Type /help");
        }
    }

    private void spawnFakePlayer(String name) {
        if (findClient(name) != null) {
            log("[ClassicServer] Player name already in use: " + name);
            return;
        }
        int id = getNextPlayerId();
        if (id == -1) {
            log("[ClassicServer] Server is full! Cannot spawn fake player.");
            return;
        }
        playerIdPool[id] = true;
        
        ClientHandler fake = new ClientHandler(name, (byte) id);
        synchronized (clients) {
            clients.add(fake);
        }
        
        log("[ClassicServer] Spawned fake player: " + name + " (ID: " + id + ")");
        syncPlayersList();
        
        // Broadcast spawn packet of new fake player to all existing clients
        ByteArrayOutputStream spawnPacket = new ByteArrayOutputStream();
        try {
            spawnPacket.write(7); // Spawn opcode
            spawnPacket.write(id);
            writeString(spawnPacket, name);
            writeShort(spawnPacket, fake.px);
            writeShort(spawnPacket, fake.py);
            writeShort(spawnPacket, fake.pz);
            spawnPacket.write(fake.pyaw);
            spawnPacket.write(fake.ppitch);
            broadcast(spawnPacket.toByteArray());
        } catch (IOException ignored) {}
        
        // Announcement Chat
        broadcastServerChat("&e" + name + " joined the game.");
    }

    private void removeFakePlayer(String name) {
        ClientHandler fake = findClient(name);
        if (fake == null || fake.socket != null) {
            log("[ClassicServer] Fake player not found: " + name);
            return;
        }
        
        fake.disconnect();
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private final byte playerId;
        private String username = "Player";
        private boolean connected = true;
        
        private InputStream in;
        private OutputStream out;

        private short px, py, pz;
        private byte pyaw, ppitch;

        public ClientHandler(Socket socket, byte playerId) {
            this.socket = socket;
            this.playerId = playerId;
        }

        public ClientHandler(String username, byte playerId) {
            this.socket = null;
            this.playerId = playerId;
            this.username = username;
            this.connected = true;
            this.px = (short) (128 * 32);
            this.py = (short) (33.625 * 32);
            this.pz = (short) (128 * 32);
            this.pyaw = 0;
            this.ppitch = 0;
        }

        public synchronized void sendPacket(byte[] packet) {
            if (!connected || socket == null || out == null) return;
            try {
                out.write(packet);
                out.flush();
            } catch (IOException e) {
                log("[ClassicServer] Failed to send packet to " + username + ": " + e.getMessage());
                disconnect();
            }
        }

        private void disconnect() {
            if (!connected) return;
            connected = false;
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
            
            synchronized (clients) {
                clients.remove(this);
                releasePlayerId(playerId);
            }

            log("[ClassicServer] " + username + " disconnected.");
            syncPlayersList();
            
            // Broadcast despawn packet
            byte[] despawnPacket = new byte[2];
            despawnPacket[0] = 12; // Despawn opcode
            despawnPacket[1] = playerId;
            broadcast(despawnPacket);

            // Broadcast chat message about disconnect
            sendGlobalChat("&e" + username + " left the game.");
        }

        private void sendGlobalChat(String msg) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                baos.write(13); // Chat Message Opcode
                baos.write(-1); // ID -1 for server announcement
                writeString(baos, msg);
                broadcast(baos.toByteArray());
            } catch (IOException ignored) {}
        }

        @Override
        public void run() {
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();

                // 1) Read IDENTIFICATION from client
                int opcode = in.read();
                if (opcode != 0) {
                    log("[ClassicServer] Unexpected opcode: " + opcode + ". Disconnecting client.");
                    disconnect();
                    return;
                }
                int protocolVer = in.read();
                username = readString(in);
                String verificationKey = readString(in);
                int clientType = in.read();

                log("[ClassicServer] Client username: " + username + " (Protocol version: " + protocolVer + ")");
                
                if (bannedPlayers.contains(username.toLowerCase())) {
                    log("[ClassicServer] Banned player " + username + " tried to connect. Rejecting.");
                    sendDisconnect(socket, "You are banned!");
                    disconnect();
                    return;
                }

                synchronized (admins) {
                    if (admins.isEmpty()) {
                        admins.add(username.toLowerCase());
                        saveSetToFile("admins.txt", admins);
                        log("[ClassicServer] admins.txt was empty. Auto-promoted " + username + " to admin.");
                    }
                }

                syncPlayersList();

                // 2) Write IDENTIFICATION back to client
                out.write(0); // identification opcode
                out.write(7); // protocol version
                writeString(out, "Classic Remastered Server");
                writeString(out, "Direct Multiplayer Connect Host");
                out.write(0x00); // UserType: normal player
                out.flush();

                // 3) Send Level Initialization
                out.write(2); // Level init opcode
                out.flush();

                // 4) GZIP compress the level blocks
                ByteArrayOutputStream levelBytes = new ByteArrayOutputStream();
                try (GZIPOutputStream gzos = new GZIPOutputStream(levelBytes);
                     DataOutputStream dos = new DataOutputStream(gzos)) {
                    dos.writeInt(blocks.length);
                    dos.write(blocks);
                }
                byte[] compressedLevel = levelBytes.toByteArray();

                // Stream level chunks
                int offset = 0;
                while (offset < compressedLevel.length) {
                    int length = Math.min(1024, compressedLevel.length - offset);
                    out.write(3); // Level data opcode
                    writeShort(out, (short) length);
                    
                    byte[] chunk = new byte[1024];
                    System.arraycopy(compressedLevel, offset, chunk, 0, length);
                    out.write(chunk);

                    offset += length;
                    int percent = (int) (((long) offset * 100) / compressedLevel.length);
                    out.write((byte) percent);
                    out.flush();
                }

                // 5) Finalize Level
                out.write(4); // Level finalize opcode
                writeShort(out, (short) WIDTH);
                writeShort(out, (short) HEIGHT);
                writeShort(out, (short) DEPTH);
                out.flush();

                // 6) Spawn player locally
                px = (short) (128 * 32);
                py = (short) (33.625 * 32);
                pz = (short) (128 * 32);
                pyaw = 0;
                ppitch = 0;

                // Send self-spawn packet (id -1 tells the client its own spawn point)
                ByteArrayOutputStream selfSpawn = new ByteArrayOutputStream();
                selfSpawn.write(7); // Spawn opcode
                selfSpawn.write(-1); // ID -1 for self spawn
                writeString(selfSpawn, username);
                writeShort(selfSpawn, px);
                writeShort(selfSpawn, py);
                writeShort(selfSpawn, pz);
                selfSpawn.write(pyaw);
                selfSpawn.write(ppitch);
                sendPacket(selfSpawn.toByteArray());

                // Broadcast spawn packet of new player to all existing clients
                ByteArrayOutputStream newPlayerSpawn = new ByteArrayOutputStream();
                newPlayerSpawn.write(7);
                newPlayerSpawn.write(playerId);
                writeString(newPlayerSpawn, username);
                writeShort(newPlayerSpawn, px);
                writeShort(newPlayerSpawn, py);
                writeShort(newPlayerSpawn, pz);
                newPlayerSpawn.write(pyaw);
                newPlayerSpawn.write(ppitch);
                broadcastExclude(newPlayerSpawn.toByteArray(), playerId);

                // Send existing player spawns to the new player
                synchronized (clients) {
                    for (ClientHandler other : clients) {
                        if (other.playerId != this.playerId) {
                            ByteArrayOutputStream otherSpawn = new ByteArrayOutputStream();
                            otherSpawn.write(7);
                            otherSpawn.write(other.playerId);
                            writeString(otherSpawn, other.username);
                            writeShort(otherSpawn, other.px);
                            writeShort(otherSpawn, other.py);
                            writeShort(otherSpawn, other.pz);
                            otherSpawn.write(other.pyaw);
                            otherSpawn.write(other.ppitch);
                            sendPacket(otherSpawn.toByteArray());
                        }
                    }
                }

                // Announcement Chat
                sendGlobalChat("&e" + username + " joined the game.");

                // 7) Read incoming packets
                while (connected) {
                    int op = in.read();
                    if (op == -1) break; // EOF

                    switch (op) {
                        case 5: { // PLAYER_SET_BLOCK
                            short bx = readShort(in);
                            short by = readShort(in);
                            short bz = readShort(in);
                            byte mode = (byte) in.read();
                            byte blockType = (byte) in.read();
                            
                            byte finalBlock = (mode == 0) ? (byte) 0 : blockType;
                            updateBlock(bx, by, bz, finalBlock);

                            // Broadcast BLOCK_CHANGE
                            ByteArrayOutputStream blockChange = new ByteArrayOutputStream();
                            blockChange.write(6); // Block change opcode
                            writeShort(blockChange, bx);
                            writeShort(blockChange, by);
                            writeShort(blockChange, bz);
                            blockChange.write(finalBlock);
                            broadcast(blockChange.toByteArray());
                            break;
                        }
                        case 8: { // POSITION_ROTATION
                            byte id = (byte) in.read(); // will be -1
                            short cx = readShort(in);
                            short cy = readShort(in);
                            short cz = readShort(in);
                            byte cyaw = (byte) in.read();
                            byte cpitch = (byte) in.read();

                            px = cx;
                            py = cy;
                            pz = cz;
                            pyaw = cyaw;
                            ppitch = cpitch;

                            // Re-broadcast position rotation to others under this client's real id
                            ByteArrayOutputStream posUpdate = new ByteArrayOutputStream();
                            posUpdate.write(8);
                            posUpdate.write(playerId);
                            writeShort(posUpdate, cx);
                            writeShort(posUpdate, cy);
                            writeShort(posUpdate, cz);
                            posUpdate.write(cyaw);
                            posUpdate.write(cpitch);
                            broadcastExclude(posUpdate.toByteArray(), playerId);
                            break;
                        }
                        case 13: { // CHAT_MESSAGE
                            byte id = (byte) in.read(); // will be -1
                            String msg = readString(in);

                            if (msg.startsWith("/")) {
                                handleCommand(msg);
                            } else {
                                if (mutedPlayers.contains(username.toLowerCase())) {
                                    sendSystemMessage("&cYou are muted and cannot chat.");
                                } else {
                                    // Formatted message
                                    String formatted = "<" + username + "> " + msg;
                                    log("[Chat] " + formatted);

                                    ByteArrayOutputStream chatMsg = new ByteArrayOutputStream();
                                    chatMsg.write(13);
                                    chatMsg.write(playerId);
                                    writeString(chatMsg, formatted);
                                    broadcast(chatMsg.toByteArray());
                                }
                            }
                            break;
                        }
                        default:
                            // Unknown packet, skip matching bytes or disconnect
                            log("[ClassicServer] Unknown client opcode: " + op + ". Disconnecting.");
                            disconnect();
                            return;
                    }
                }
            } catch (IOException e) {
                log("[ClassicServer] Handler error for " + username + ": " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void sendSystemMessage(String msg) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                baos.write(13); // Chat Message Opcode
                baos.write(-1); // ID -1 for server announcement
                writeString(baos, msg);
                sendPacket(baos.toByteArray());
            } catch (IOException ignored) {}
        }

        private void handleCommand(String fullCommand) {
            String[] parts = fullCommand.trim().split("\\s+", 3);
            if (parts.length == 0) return;
            
            String cmd = parts[0].substring(1).toLowerCase();
            
            // Common non-admin commands:
            if (cmd.equals("help")) {
                sendSystemMessage("&eAvailable commands: /help, /msg, /me");
                if (admins.contains(username.toLowerCase())) {
                    sendSystemMessage("&cAdmin commands: /kick, /ban, /mute, /unmute, /admin, /deadmin, /spawnfake, /removefake");
                }
                return;
            }
            if (cmd.equals("msg")) {
                if (parts.length < 3) {
                    sendSystemMessage("&cUsage: /msg <player> <message>");
                    return;
                }
                String targetName = parts[1];
                String privMsg = parts[2];
                ClientHandler target = findClient(targetName);
                if (target == null) {
                    sendSystemMessage("&cPlayer not found: " + targetName);
                } else {
                    target.sendSystemMessage("&d[PM] From " + username + ": " + privMsg);
                    sendSystemMessage("&d[PM] To " + target.username + ": " + privMsg);
                }
                return;
            }
            if (cmd.equals("me")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /me <action>");
                    return;
                }
                String action = fullCommand.trim().substring(4);
                sendGlobalChat("* " + username + " " + action);
                return;
            }

            // Check admin permissions for remaining commands:
            if (!admins.contains(username.toLowerCase())) {
                sendSystemMessage("&cYou do not have permission to use this command.");
                return;
            }

            // Admin commands
            if (cmd.equals("kick")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /kick <player> [reason]");
                    return;
                }
                String targetName = parts[1];
                String reason = parts.length > 2 ? parts[2] : "Kicked by admin";
                ClientHandler target = findClient(targetName);
                if (target == null) {
                    sendSystemMessage("&cPlayer not found: " + targetName);
                } else {
                    sendDisconnect(target.socket, reason);
                    target.disconnect();
                    sendGlobalChat("&e" + target.username + " was kicked: " + reason);
                }
            } else if (cmd.equals("ban")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /ban <player> [reason]");
                    return;
                }
                String targetName = parts[1];
                String reason = parts.length > 2 ? parts[2] : "Banned by admin";
                
                bannedPlayers.add(targetName.toLowerCase());
                saveSetToFile("bans.txt", bannedPlayers);
                
                ClientHandler target = findClient(targetName);
                if (target != null) {
                    sendDisconnect(target.socket, "Banned: " + reason);
                    target.disconnect();
                }
                sendGlobalChat("&e" + targetName + " was banned: " + reason);
            } else if (cmd.equals("mute")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /mute <player>");
                    return;
                }
                String targetName = parts[1];
                mutedPlayers.add(targetName.toLowerCase());
                sendSystemMessage("&eMuted " + targetName);
                ClientHandler target = findClient(targetName);
                if (target != null) {
                    target.sendSystemMessage("&cYou have been muted by an admin.");
                }
            } else if (cmd.equals("unmute")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /unmute <player>");
                    return;
                }
                String targetName = parts[1];
                mutedPlayers.remove(targetName.toLowerCase());
                sendSystemMessage("&eUnmuted " + targetName);
                ClientHandler target = findClient(targetName);
                if (target != null) {
                    target.sendSystemMessage("&aYou have been unmuted.");
                }
            } else if (cmd.equals("admin")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /admin <player>");
                    return;
                }
                String targetName = parts[1];
                admins.add(targetName.toLowerCase());
                saveSetToFile("admins.txt", admins);
                sendSystemMessage("&ePromoted " + targetName + " to admin.");
                ClientHandler target = findClient(targetName);
                if (target != null) {
                    target.sendSystemMessage("&aYou are now an admin.");
                }
            } else if (cmd.equals("deadmin")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /deadmin <player>");
                    return;
                }
                String targetName = parts[1];
                admins.remove(targetName.toLowerCase());
                saveSetToFile("admins.txt", admins);
                sendSystemMessage("&eDemoted " + targetName + " from admin.");
                ClientHandler target = findClient(targetName);
                if (target != null) {
                    target.sendSystemMessage("&cYou are no longer an admin.");
                }
            } else if (cmd.equals("spawnfake")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /spawnfake <name>");
                    return;
                }
                String name = parts[1];
                spawnFakePlayer(name);
            } else if (cmd.equals("removefake")) {
                if (parts.length < 2) {
                    sendSystemMessage("&cUsage: /removefake <name>");
                    return;
                }
                String name = parts[1];
                removeFakePlayer(name);
            } else {
                sendSystemMessage("&cUnknown command. Type /help for assistance.");
            }
        }
    }
}
