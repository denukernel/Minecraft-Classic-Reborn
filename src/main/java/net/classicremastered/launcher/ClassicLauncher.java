package net.classicremastered.launcher;

import com.mcraft.api.loader.ModLoader; // <<< ADDED

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.MinecraftApplet;
import net.classicremastered.minecraft.util.JInputBootstrap;
import net.classicremastered.util.LwjglNativesDownloader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class ClassicLauncher {

    public static void main(String[] args) {
        try {
            LwjglNativesDownloader.setupWindowsNatives();
            JInputBootstrap.setupWindows();
            ClassicLauncher app = new ClassicLauncher();

            // Claim single instance (or ask existing to close, then become primary)
            app.singleInstance = SingleInstance.claimOrTakeover(app);

            SwingUtilities.invokeLater(app::show);
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "An error occurred during startup:\n" + t.toString(), 
                "Minecraft Classic - Startup Error", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private final JFrame frame = new JFrame("Minecraft Classic Launcher");

    private final JComboBox<String> cbResolution = new JComboBox<>(new String[] {
        "854 x 480",
        "1280 x 720",
        "1600 x 900",
        "1920 x 1080"
    });
    private final JComboBox<String> cbMemory = new JComboBox<>(new String[] {
        "1024 MB (1 GB)",
        "2048 MB (2 GB)",
        "4096 MB (4 GB - Recommended)",
        "6144 MB (6 GB)",
        "8192 MB (8 GB)"
    });
    private final JCheckBox cbFullscreen = new JCheckBox("Fullscreen", false);
    private final JCheckBox cbMultithreaded = new JCheckBox("Multithreaded Core Mode", true);

    private final JButton btnPlay = new MinecraftButton("Play");
    private final JButton btnQuit = new MinecraftButton("Quit");

    private final LauncherConfig cfg = new LauncherConfig();

    private void show() {
        loadConfig();

        // Set launcher icon(s)
        java.awt.Image logoIcon = null;
        try {
            var icons = loadIcons();
            if (!icons.isEmpty()) {
                frame.setIconImages(icons);
                logoIcon = icons.get(0);
            }
        } catch (Throwable ignored) {
        }

        // Try loading Minecraft dirt/stone background texture
        java.awt.image.BufferedImage bgTexture = null;
        try {
            java.io.InputStream in = ClassicLauncher.class.getResourceAsStream("/dirt.png");
            if (in == null) in = ClassicLauncher.class.getResourceAsStream("/rock.png");
            if (in != null) {
                bgTexture = javax.imageio.ImageIO.read(in);
            }
        } catch (Throwable ignored) {
        }

        final java.awt.image.BufferedImage finalBgTexture = bgTexture;
        final java.awt.Image finalLogoIcon = logoIcon;

        // Custom Background Root Panel
        JPanel root = new JPanel(new BorderLayout(14, 14)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth();
                int h = getHeight();

                if (finalBgTexture != null) {
                    // Tile textured background (scaled 2x for retro pixel art feel)
                    int tw = finalBgTexture.getWidth() * 2;
                    int th = finalBgTexture.getHeight() * 2;
                    for (int x = 0; x < w; x += tw) {
                        for (int y = 0; y < h; y += th) {
                            g2.drawImage(finalBgTexture, x, y, tw, th, null);
                        }
                    }
                    // Dark overlay with slight vignette for sleek modern contrast
                    g2.setColor(new Color(0, 0, 0, 165));
                    g2.fillRect(0, 0, w, h);
                } else {
                    // Fallback sleek dark gradient
                    g2.setPaint(new GradientPaint(0, 0, new Color(42, 42, 46), 0, h, new Color(20, 20, 24)));
                    g2.fillRect(0, 0, w, h);
                }
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // --- Header / Title Area ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setOpaque(false);

        if (finalLogoIcon != null) {
            JLabel iconLabel = new JLabel(new ImageIcon(finalLogoIcon.getScaledInstance(36, 36, Image.SCALE_FAST)));
            headerPanel.add(iconLabel, BorderLayout.WEST);
        }

        JPanel titleTextPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titleTextPanel.setOpaque(false);

        JLabel title = new JLabel("Minecraft 0.30") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                // Shadow
                g2.setFont(getFont());
                g2.setColor(new Color(38, 38, 38));
                g2.drawString(getText(), 2, getHeight() - 4);
                // Main
                g2.setColor(Color.WHITE);
                g2.drawString(getText(), 0, getHeight() - 6);
                g2.dispose();
            }
        };
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setPreferredSize(new Dimension(220, 24));

        JLabel subtitle = new JLabel("IMPROVED EDITION");
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        subtitle.setForeground(new Color(255, 215, 64)); // Classic golden text

        titleTextPanel.add(title);
        titleTextPanel.add(subtitle);
        headerPanel.add(titleTextPanel, BorderLayout.CENTER);

        root.add(headerPanel, BorderLayout.NORTH);

        // --- Settings Panel (Dark translucent card) ---
        JPanel pSettings = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 140));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(255, 255, 255, 30));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pSettings.setOpaque(false);
        pSettings.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Resolution
        JLabel lblRes = new JLabel("Resolution:");
        lblRes.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRes.setForeground(new Color(220, 220, 220));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.35;
        pSettings.add(lblRes, gbc);

        styleComboBox(cbResolution);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.65;
        pSettings.add(cbResolution, gbc);

        // Row 1: Memory (RAM)
        JLabel lblMem = new JLabel("Memory (RAM):");
        lblMem.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblMem.setForeground(new Color(220, 220, 220));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.35;
        pSettings.add(lblMem, gbc);

        styleComboBox(cbMemory);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0.65;
        pSettings.add(cbMemory, gbc);

        // Row 2: Fullscreen CheckBox
        styleCheckBox(cbFullscreen);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(6, 4, 1, 4);
        pSettings.add(cbFullscreen, gbc);

        // Row 3: Multithreaded CheckBox
        styleCheckBox(cbMultithreaded);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(2, 4, 2, 4);
        pSettings.add(cbMultithreaded, gbc);

        root.add(pSettings, BorderLayout.CENTER);

        // --- Actions Footer ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        btnQuit.setPreferredSize(new Dimension(84, 30));
        btnPlay.setPreferredSize(new Dimension(100, 30));

        actions.add(btnQuit);
        actions.add(btnPlay);
        root.add(actions, BorderLayout.SOUTH);

        // Quit: stop any running game first
        btnQuit.addActionListener(e -> {
            try {
                if (runningMc != null) {
                    if (askedToQuitRef != null)
                        askedToQuitRef[0] = true;
                    runningMc.requestQuit();
                }
            } catch (Throwable ignored) {
            }
            // ensure modloader shutdown if alive
            try {
                ModLoader.shutdownIfAlive();
            } catch (Throwable ignored) {
            }
            try {
                if (singleInstance != null)
                    singleInstance.close();
            } catch (Throwable ignored) {
            }
            System.exit(0);
        });

        // Play: if already running, just focus the game window; otherwise launch
        btnPlay.addActionListener(e -> {
            // already running? bring to front
            if (runningMc != null && runningMc.running) {
                if (gameWindow != null) {
                    try {
                        gameWindow.toFront();
                        gameWindow.requestFocus();
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }

            // launch fresh
            saveConfig();
            int[] res = parseRes((String) cbResolution.getSelectedItem());
            // Disable Play while launching/running
            btnPlay.setEnabled(false);
            launchGame(res[0], res[1], cbFullscreen.isSelected());
        });

        cbResolution.setSelectedItem(cfg.resString());
        cbMemory.setSelectedItem(cfg.ramString());
        cbFullscreen.setSelected(cfg.fullscreen);
        cbMultithreaded.setSelected(cfg.multithreaded);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setResizable(false);
        frame.pack();
        frame.setSize(410, 310);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    private static void styleComboBox(JComboBox<String> combo) {
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        combo.setBackground(new Color(36, 36, 40));
        combo.setForeground(Color.WHITE);
        combo.setFocusable(false);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                if (isSelected) {
                    setBackground(new Color(75, 100, 150));
                    setForeground(Color.WHITE);
                } else {
                    setBackground(new Color(36, 36, 40));
                    setForeground(new Color(230, 230, 230));
                }
                return this;
            }
        });
    }

    private static void styleCheckBox(JCheckBox cb) {
        cb.setOpaque(false);
        cb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cb.setForeground(new Color(230, 230, 230));
        cb.setFocusPainted(false);
        cb.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    /**
     * Custom styled Minecraft button matching classic stone button aesthetics
     */
    private static class MinecraftButton extends JButton {
        public MinecraftButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(new Color(224, 224, 224));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            int w = getWidth();
            int h = getHeight();
            boolean pressed = getModel().isPressed();
            boolean hover = getModel().isRollover();
            boolean enabled = isEnabled();

            // Background stone base color
            Color topBg, bottomBg, borderColor, highlightColor, shadowColor;

            if (!enabled) {
                topBg = new Color(70, 70, 70);
                bottomBg = new Color(55, 55, 55);
                borderColor = new Color(30, 30, 30);
                highlightColor = new Color(90, 90, 90);
                shadowColor = new Color(40, 40, 40);
            } else if (pressed) {
                topBg = new Color(65, 65, 65);
                bottomBg = new Color(85, 85, 85);
                borderColor = Color.BLACK;
                highlightColor = new Color(50, 50, 50);
                shadowColor = new Color(130, 130, 130);
            } else if (hover) {
                topBg = new Color(130, 140, 175);
                bottomBg = new Color(105, 115, 150);
                borderColor = Color.BLACK;
                highlightColor = new Color(165, 175, 210);
                shadowColor = new Color(70, 75, 100);
            } else {
                topBg = new Color(110, 110, 110);
                bottomBg = new Color(88, 88, 88);
                borderColor = Color.BLACK;
                highlightColor = new Color(145, 145, 145);
                shadowColor = new Color(55, 55, 55);
            }

            // Fill gradient body
            g2.setPaint(new GradientPaint(0, 0, topBg, 0, h, bottomBg));
            g2.fillRect(2, 2, w - 4, h - 4);

            // Bevel highlights
            g2.setColor(highlightColor);
            g2.drawLine(2, 2, w - 3, 2); // Top inner
            g2.drawLine(2, 2, 2, h - 3); // Left inner

            // Bevel shadows
            g2.setColor(shadowColor);
            g2.drawLine(w - 3, 2, w - 3, h - 3); // Right inner
            g2.drawLine(2, h - 3, w - 3, h - 3); // Bottom inner

            // Outer dark border
            g2.setColor(borderColor);
            g2.drawRect(0, 0, w - 1, h - 1);

            // Draw text with shadow
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(getText())) / 2;
            int textY = (h + fm.getAscent() - fm.getDescent()) / 2;

            if (pressed) {
                textX += 1;
                textY += 1;
            }

            // Text Drop Shadow
            g2.setColor(new Color(30, 30, 30));
            g2.drawString(getText(), textX + 1, textY + 1);

            // Text Fore color
            if (!enabled) {
                g2.setColor(new Color(140, 140, 140));
            } else if (hover) {
                g2.setColor(new Color(255, 255, 160)); // Classic hover yellow
            } else {
                g2.setColor(new Color(224, 224, 224));
            }
            g2.drawString(getText(), textX, textY);

            g2.dispose();
        }
    }
    // ClassicLauncher

    private static java.io.File resolveMcDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty())
                appData = home;
            return new java.io.File(appData, ".minecraftclassicreborn/client/");
        } else if (os.contains("mac")) {
            return new java.io.File(home, "Library/Application Support/.minecraftclassicreborn/client/");
        } else {
            // linux/unix
            return new java.io.File(home, ".minecraftclassicreborn/client/");
        }
    }

    private static int[] parseRes(String s) {
        if (s == null)
            return new int[] { 854, 480 };
        String[] parts = s.split("x");
        return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
    }

    /* ================= Launch ================= */

    private void launchGame(int width, int height, boolean fullscreen) {
        frame.setVisible(false);

        JFrame win = new JFrame("Minecraft Classic " + Constants.MINECRAFT_VERSION);
        this.gameWindow = win;

        // Set game window icon(s)
        try {
            var icons = loadIcons();
            if (!icons.isEmpty())
                win.setIconImages(icons);
        } catch (Throwable ignored) {
        }

        win.setLayout(new BorderLayout());
        win.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        System.setProperty("minecraft.ramMb", String.valueOf(cfg.ramMb));
        System.setProperty("minecraft.multithreaded", String.valueOf(cfg.multithreaded));

        final MCraftApplet applet = new MCraftApplet();
        final MinecraftCanvas canvas = new MinecraftCanvas();
        final Minecraft mc = new Minecraft(canvas, applet, width, height, fullscreen);
        final boolean[] askedToQuit = { false }; // simple mutable flag

        this.runningMc = mc;
        this.askedToQuitRef = askedToQuit;

        // >>> BOOTSTRAP MOD LOADER RIGHT AFTER MC IS CONSTRUCTED <<<
     // >>> BOOTSTRAP MOD LOADER RIGHT AFTER MC IS CONSTRUCTED <<<
        try {
            // ensure mcDir is set BEFORE bootstrapping mods
            if (Minecraft.mcDir == null) {
                Minecraft.mcDir = resolveMcDir();
                if (!Minecraft.mcDir.exists()) Minecraft.mcDir.mkdirs();
            }
            System.out.println("[Launcher] mcDir=" + Minecraft.mcDir.getAbsolutePath());
            ModLoader.bootstrap(mc);
        } catch (Throwable t) { t.printStackTrace(); }


        // Ensure the canvas dictates the window size
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.setMinimumSize(new Dimension(width, height));
        canvas.setMinecraft(mc);
        win.add(canvas, BorderLayout.CENTER);

        // === Window close hooks ===
        win.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                askedToQuit[0] = true;
                mc.requestQuit();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                runningMc = null;
                gameWindow = null;
                askedToQuitRef = null;
                SwingUtilities.invokeLater(() -> {
                    try {
                        btnPlay.setEnabled(true);
                    } catch (Throwable ignored) {
                    }
                    try {
                        frame.setVisible(true);
                        frame.toFront();
                    } catch (Throwable ignored) {
                    }
                });
            }
        });

        win.pack();
        win.setLocationRelativeTo(null);
        win.setResizable(false);
        win.setVisible(true);
        win.toFront();
        win.requestFocus();
        canvas.requestFocusInWindow();

        SwingUtilities.invokeLater(canvas::startThread);

        // === Watcher thread to dispose when game loop ends ===
        new Thread(() -> {
            while (true) {
                if (askedToQuit[0] && !mc.running) {
                    try {
                        mc.shutdown();
                    } catch (Throwable ignored) {
                    }
                    // >>> ENSURE MOD LOADER SHUTS DOWN CLEANLY <<<
                    try {
                        ModLoader.shutdownIfAlive();
                    } catch (Throwable ignored) {
                    }
                    try {
                        win.dispose();
                    } catch (Throwable ignored) {
                    }
                    try {
                        if (singleInstance != null)
                            singleInstance.close();
                    } catch (Throwable ignored) {
                    }
                    return;
                }
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {
                }
            }
        }, "Launcher-Watcher").start();
    }

    // Load PNG icons from classpath; tries a few common locations.
    private static java.util.List<Image> loadIcons() {
        java.util.List<Image> out = new java.util.ArrayList<>(2);
        String[] paths = { "/cube32.png", "/icons/cube32.png" };
        for (String p : paths) {
            try {
                java.net.URL u = ClassicLauncher.class.getResource(p);
                if (u != null) {
                    java.awt.Image img = javax.imageio.ImageIO.read(u);
                    if (img != null)
                        out.add(img);
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /* ================= Config ================= */

    private void loadConfig() {
        try {
            cfg.load();
        } catch (IOException ignored) {
        }
    }

    private static int parseRam(String s) {
        if (s == null) return 4096;
        if (s.startsWith("1024")) return 1024;
        if (s.startsWith("2048")) return 2048;
        if (s.startsWith("4096")) return 4096;
        if (s.startsWith("6144")) return 6144;
        if (s.startsWith("8192")) return 8192;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 4096;
        }
    }

    private void saveConfig() {
        String res = (String) cbResolution.getSelectedItem();
        cfg.width = parseRes(res)[0];
        cfg.height = parseRes(res)[1];
        cfg.fullscreen = cbFullscreen.isSelected();
        cfg.ramMb = parseRam((String) cbMemory.getSelectedItem());
        cfg.multithreaded = cbMultithreaded.isSelected();
        try {
            cfg.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ================= Inner classes ================= */

    private static final class MCraftApplet extends MinecraftApplet {
        @Override
        public String getParameter(String name) {
            return null;
        }

        @Override
        public URL getDocumentBase() {
            try {
                return new URL("http://minecraft.net:80/play.jsp");
            } catch (MalformedURLException e) {
                return null;
            }
        }

        @Override
        public URL getCodeBase() {
            try {
                return new URL("http://minecraft.net:80/");
            } catch (MalformedURLException e) {
                return null;
            }
        }
    }

    private static final class MinecraftCanvas extends Canvas {
        private transient Minecraft minecraft;
        private transient Thread thread;

        void setMinecraft(Minecraft m) {
            this.minecraft = m;
        }

        void startThread() {
            if (thread != null)
                return;

            // If the Canvas isn't realized yet, try again on the next EDT tick.
            if (!isDisplayable() || !isShowing()) {
                SwingUtilities.invokeLater(this::startThread);
                return;
            }

            thread = new Thread(minecraft, "Client");
            thread.start();
        }

        @Override
        public synchronized void addNotify() {
            super.addNotify();
            // When AWT creates the native peer, try to start again
            SwingUtilities.invokeLater(this::startThread);
        }

        @Override
        public synchronized void removeNotify() {
            if (thread != null) {
                try {
                    minecraft.requestQuit();
                } catch (Throwable ignored) {
                }
                try {
                    thread.join(2000);
                } catch (InterruptedException ignored) {
                }
                thread = null;
            }
            super.removeNotify();
        }
    }

    // ================= Single-instance support =================
    private static final class SingleInstance {
        private static final int PORT = 45731; // any free localhost port, keep stable
        private ServerSocket server;
        private Thread acceptor;

        /** Try to become primary; if already running, request that instance to quit. */
        static SingleInstance claimOrTakeover(ClassicLauncher app) {
            SingleInstance si = new SingleInstance();
            if (si.tryBind(app)) {
                // We are the primary now.
                return si;
            }
            // Another instance is running → request it to close, then wait and retry.
            System.out.println("[SingleInstance] Port " + PORT + " is busy. Requesting takeover...");
            requestTakeover();
            long deadline = System.currentTimeMillis() + 3000; // 3s
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                if (si.tryBind(app))
                    return si;
            }
            
            // If we still didn't get it, it might be a real port conflict or the other instance is hanging.
            int choice = JOptionPane.showConfirmDialog(null,
                "Another instance of Minecraft Classic appears to be running, or port " + PORT + " is blocked.\n" +
                "Do you want to try starting anyway? (This may cause issues if another instance is active)",
                "Single Instance Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            
            if (choice == JOptionPane.YES_OPTION) {
                System.out.println("[SingleInstance] Proceeding despite bind failure.");
                return null; 
            } else {
                System.exit(0);
                return null;
            }
        }

        /** Ask the running instance to close itself. */
        private static void requestTakeover() {
            try (Socket s = new Socket("127.0.0.1", PORT); OutputStream out = s.getOutputStream()) {
                out.write("TAKEOVER\n".getBytes("UTF-8"));
                out.flush();
            } catch (IOException ignored) {
                // No listener — maybe it closed in the meantime; new bind will succeed.
            }
        }

        /**
         * Try binding the control port; on success, start acceptor that listens for
         * TAKEOVER.
         */
        private boolean tryBind(ClassicLauncher app) {
            try {
                server = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"));
                acceptor = new Thread(() -> runServer(app), "SingleInstance-Acceptor");
                acceptor.setDaemon(true);
                acceptor.start();
                return true;
            } catch (IOException bindFailed) {
                return false;
            }
        }

        private void runServer(ClassicLauncher app) {
            while (!server.isClosed()) {
                try (Socket s = server.accept();
                        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"))) {
                    String line = r.readLine();
                    if (line != null && line.startsWith("TAKEOVER")) {
                        // Politely shut down current launcher + game, then exit process.
                        app.forceCloseFromExternalRequest();
                    }
                } catch (IOException ignored) {
                }
            }
        }

        void close() {
            try {
                if (server != null)
                    server.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Called by SingleInstance server thread when another instance asks to
     * TAKEOVER.
     */
    void forceCloseFromExternalRequest() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (runningMc != null) {
                    if (askedToQuitRef != null)
                        askedToQuitRef[0] = true;
                    try {
                        runningMc.requestQuit();
                    } catch (Throwable ignored) {
                    }
                }
                if (gameWindow != null) {
                    try {
                        gameWindow.dispose();
                    } catch (Throwable ignored) {
                    }
                    gameWindow = null;
                }
                try {
                    frame.dispose();
                } catch (Throwable ignored) {
                }
                // ensure modloader shutdown as well
                try {
                    ModLoader.shutdownIfAlive();
                } catch (Throwable ignored) {
                }
            } finally {
                // Ensure process exit so the new instance can bind the port
                System.exit(0);
            }
        });
    }

    // ===========================================================
    // Track currently running game so we can close it on takeover
    private volatile JFrame gameWindow = null;
    private volatile Minecraft runningMc = null;
    private volatile boolean[] askedToQuitRef = null;

    // Keep a reference to the single-instance guard to release on normal exit
    private SingleInstance singleInstance = null;

    /** Config persistence */
    private static final class LauncherConfig {
        int width = 854, height = 480;
        boolean fullscreen = false;
        int ramMb = 4096;
        boolean multithreaded = true;

        private static Path propsPath() {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty())
                appData = System.getProperty("user.home");
            return Paths.get(appData, ".minecraftclassicreborn", "client", "launcher.properties");
        }

        String resString() {
            return width + " x " + height;
        }

        String ramString() {
            if (ramMb == 1024) return "1024 MB (1 GB)";
            if (ramMb == 2048) return "2048 MB (2 GB)";
            if (ramMb == 4096) return "4096 MB (4 GB - Recommended)";
            if (ramMb == 6144) return "6144 MB (6 GB)";
            if (ramMb == 8192) return "8192 MB (8 GB)";
            return ramMb + " MB";
        }

        void load() throws IOException {
            Properties p = new Properties();
            Path path = propsPath();
            if (!Files.exists(path))
                return;
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            }
            width = parseInt(p.getProperty("width"), width);
            height = parseInt(p.getProperty("height"), height);
            fullscreen = Boolean.parseBoolean(p.getProperty("fullscreen", String.valueOf(fullscreen)));
            ramMb = parseInt(p.getProperty("ramMb"), ramMb);
            multithreaded = Boolean.parseBoolean(p.getProperty("multithreaded", String.valueOf(multithreaded)));
        }

        void save() throws IOException {
            Properties p = new Properties();
            p.setProperty("width", String.valueOf(width));
            p.setProperty("height", String.valueOf(height));
            p.setProperty("fullscreen", String.valueOf(fullscreen));
            p.setProperty("ramMb", String.valueOf(ramMb));
            p.setProperty("multithreaded", String.valueOf(multithreaded));
            Path path = propsPath();
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "Minecraft Classic Launcher");
            }
        }

        private static int parseInt(String s, int def) {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                return def;
            }
        }
    }
}
