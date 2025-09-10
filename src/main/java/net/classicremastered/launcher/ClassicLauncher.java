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
        LwjglNativesDownloader.setupWindowsNatives();
        JInputBootstrap.setupWindows();
        ClassicLauncher app = new ClassicLauncher();

        // Claim single instance (or ask existing to close, then become primary)
        app.singleInstance = SingleInstance.claimOrTakeover(app);

        SwingUtilities.invokeLater(app::show);
    }

    private final JFrame frame = new JFrame("Minecraft Classic Launcher");

    private final JComboBox<String> cbResolution = new JComboBox<>(new String[] { "854 x 480", "1280 x 720" });
    private final JCheckBox cbFullscreen = new JCheckBox("Fullscreen", false);

    private final JButton btnPlay = new JButton("Play");
    private final JButton btnQuit = new JButton("Quit");

    private final LauncherConfig cfg = new LauncherConfig();

    private void show() {
        loadConfig();

        // Set launcher icon(s)
        try {
            var icons = loadIcons();
            if (!icons.isEmpty())
                frame.setIconImages(icons);
        } catch (Throwable ignored) {
        }

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Minecraft 0.30 IMPROVED");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        root.add(title, BorderLayout.NORTH);

        JPanel pVideo = new JPanel(new GridLayout(2, 1, 6, 6));
        pVideo.setBorder(BorderFactory.createTitledBorder("Video"));
        pVideo.add(cbResolution);
        pVideo.add(cbFullscreen);
        root.add(pVideo, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
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
        cbFullscreen.setSelected(cfg.fullscreen);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.toFront();
        frame.requestFocus();
    }
    // ClassicLauncher

    private static java.io.File resolveMcDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty())
                appData = home;
            return new java.io.File(appData, ".mcraft/client/");
        } else if (os.contains("mac")) {
            return new java.io.File(home, "Library/Application Support/.mcraft/client/");
        } else {
            // linux/unix
            return new java.io.File(home, ".mcraft/client/");
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

    private void saveConfig() {
        String res = (String) cbResolution.getSelectedItem();
        cfg.width = parseRes(res)[0];
        cfg.height = parseRes(res)[1];
        cfg.fullscreen = cbFullscreen.isSelected();
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
            requestTakeover();
            long deadline = System.currentTimeMillis() + 4000; // up to 4s
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }
                if (si.tryBind(app))
                    return si;
            }
            // If we didn’t get the lock, just exit quietly.
            System.exit(0);
            return null; // unreachable
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

        private static Path propsPath() {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty())
                appData = System.getProperty("user.home");
            return Paths.get(appData, ".mcraft", "client", "launcher.properties");
        }

        String resString() {
            if (width == 1280 && height == 720)
                return "1280 x 720";
            return "854 x 480";
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
        }

        void save() throws IOException {
            Properties p = new Properties();
            p.setProperty("width", String.valueOf(width));
            p.setProperty("height", String.valueOf(height));
            p.setProperty("fullscreen", String.valueOf(fullscreen));
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
