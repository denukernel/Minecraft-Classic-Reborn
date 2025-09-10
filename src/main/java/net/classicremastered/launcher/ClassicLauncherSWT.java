package net.classicremastered.launcher;

import com.mcraft.api.loader.ModLoader;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.MinecraftApplet;
import net.classicremastered.minecraft.util.JInputBootstrap;
import net.classicremastered.util.LwjglNativesDownloader;

import org.eclipse.swt.*;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import java.awt.Canvas;
import java.awt.Frame;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Properties;

public final class ClassicLauncherSWT {

    private Display display;
    private Shell shell;

    private Combo cbResolution;
    private Button cbFullscreen;
    private Button btnPlay;
    private Button btnQuit;

    private LauncherConfig cfg = new LauncherConfig();

    // Track currently running game
    private volatile Minecraft runningMc = null;
    private volatile boolean[] askedToQuitRef = null;
    private volatile Shell gameShell = null;

    // Single-instance guard
    private SingleInstance singleInstance = null;

    public static void main(String[] args) {
        LwjglNativesDownloader.setupWindowsNatives();
        JInputBootstrap.setupWindows();

        ClassicLauncherSWT app = new ClassicLauncherSWT();
        app.singleInstance = SingleInstance.claimOrTakeover(app);
        app.run();
    }

    private void run() {
        display = new Display();
        shell = new Shell(display);
        shell.setText("Minecraft Classic Launcher");
        shell.setLayout(new GridLayout(1, false));

        loadConfig();

        // Title
        Label title = new Label(shell, SWT.NONE);
        title.setText("Minecraft 0.30 IMPROVED");
        title.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // Video group
        Group videoGroup = new Group(shell, SWT.NONE);
        videoGroup.setText("Video");
        videoGroup.setLayout(new GridLayout(1, false));
        videoGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        cbResolution = new Combo(videoGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        cbResolution.setItems(new String[]{"854 x 480", "1280 x 720"});
        cbResolution.setText(cfg.resString());

        cbFullscreen = new Button(videoGroup, SWT.CHECK);
        cbFullscreen.setText("Fullscreen");
        cbFullscreen.setSelection(cfg.fullscreen);

        // Actions row
        Composite row = new Composite(shell, SWT.NONE);
        row.setLayout(new RowLayout(SWT.HORIZONTAL));
        row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        btnQuit = new Button(row, SWT.PUSH);
        btnQuit.setText("Quit");
        btnQuit.addListener(SWT.Selection, e -> quitLauncher());

        btnPlay = new Button(row, SWT.PUSH);
        btnPlay.setText("Play");
        btnPlay.addListener(SWT.Selection, e -> {
            if (runningMc != null && runningMc.running) {
                if (gameShell != null) {
                    gameShell.forceActive();
                }
                return;
            }

            saveConfig();
            int[] res = parseRes(cbResolution.getText());
            btnPlay.setEnabled(false);
            launchGame(res[0], res[1], cbFullscreen.getSelection());
        });

        shell.pack();
        shell.setSize(320, 200);
        shell.open();

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        display.dispose();
    }

    private void quitLauncher() {
        try {
            if (runningMc != null) {
                if (askedToQuitRef != null)
                    askedToQuitRef[0] = true;
                runningMc.requestQuit();
            }
        } catch (Throwable ignored) {}
        try {
            ModLoader.shutdownIfAlive();
        } catch (Throwable ignored) {}
        try {
            if (singleInstance != null) singleInstance.close();
        } catch (Throwable ignored) {}
        System.exit(0);
    }

    private static int[] parseRes(String s) {
        if (s == null) return new int[]{854, 480};
        String[] parts = s.split("x");
        return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    }

    private static File resolveMcDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty()) appData = home;
            return new File(appData, ".mcraft/client/");
        } else if (os.contains("mac")) {
            return new File(home, "Library/Application Support/.mcraft/client/");
        } else {
            return new File(home, ".mcraft/client/");
        }
    }

    private void launchGame(int width, int height, boolean fullscreen) {
        shell.setVisible(false);

        gameShell = new Shell(display);
        gameShell.setText("Minecraft Classic " + Constants.MINECRAFT_VERSION);
        gameShell.setLayout(new FillLayout());

        // SWT_AWT bridge to embed AWT Canvas
        Composite comp = new Composite(gameShell, SWT.EMBEDDED);
        Frame frame = SWT_AWT.new_Frame(comp);

        final MCraftApplet applet = new MCraftApplet();
        final MinecraftCanvas canvas = new MinecraftCanvas();
        frame.add(canvas);

        final Minecraft mc = new Minecraft(canvas, applet, width, height, fullscreen);
        final boolean[] askedToQuit = {false};

        runningMc = mc;
        askedToQuitRef = askedToQuit;

        try {
            if (Minecraft.mcDir == null) {
                Minecraft.mcDir = resolveMcDir();
                if (!Minecraft.mcDir.exists()) Minecraft.mcDir.mkdirs();
            }
            System.out.println("[Launcher] mcDir=" + Minecraft.mcDir.getAbsolutePath());
            ModLoader.bootstrap(mc);
        } catch (Throwable t) { t.printStackTrace(); }

        gameShell.addListener(SWT.Close, e -> {
            askedToQuit[0] = true;
            mc.requestQuit();
        });

        gameShell.pack();
        gameShell.setSize(width + 16, height + 39); // account for borders
        gameShell.open();

        canvas.startThread();

        // Watcher thread
        new Thread(() -> {
            while (true) {
                if (askedToQuit[0] && !mc.running) {
                    try { mc.shutdown(); } catch (Throwable ignored) {}
                    try { ModLoader.shutdownIfAlive(); } catch (Throwable ignored) {}
                    display.asyncExec(() -> {
                        if (gameShell != null && !gameShell.isDisposed()) gameShell.dispose();
                        runningMc = null;
                        askedToQuitRef = null;
                        btnPlay.setEnabled(true);
                        shell.setVisible(true);
                        shell.forceActive();
                    });
                    return;
                }
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
        }, "Launcher-Watcher").start();
    }

    private void loadConfig() {
        try { cfg.load(); } catch (IOException ignored) {}
    }

    private void saveConfig() {
        String res = cbResolution.getText();
        cfg.width = parseRes(res)[0];
        cfg.height = parseRes(res)[1];
        cfg.fullscreen = cbFullscreen.getSelection();
        try { cfg.save(); } catch (IOException e) { e.printStackTrace(); }
    }

    /* ======= Inner classes ======= */

    private static final class MCraftApplet extends MinecraftApplet {
        @Override public String getParameter(String name) { return null; }
        @Override public URL getDocumentBase() {
            try { return new URL("http://minecraft.net:80/play.jsp"); }
            catch (MalformedURLException e) { return null; }
        }
        @Override public URL getCodeBase() {
            try { return new URL("http://minecraft.net:80/"); }
            catch (MalformedURLException e) { return null; }
        }
    }

    private static final class MinecraftCanvas extends Canvas {
        private transient Minecraft minecraft;
        private transient Thread thread;

        void setMinecraft(Minecraft m) { this.minecraft = m; }

        void startThread() {
            if (thread != null) return;
            thread = new Thread(minecraft, "Client");
            thread.start();
        }

        @Override public synchronized void removeNotify() {
            if (thread != null) {
                try { minecraft.requestQuit(); } catch (Throwable ignored) {}
                try { thread.join(2000); } catch (InterruptedException ignored) {}
                thread = null;
            }
            super.removeNotify();
        }
    }

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
            if (width == 1280 && height == 720) return "1280 x 720";
            return "854 x 480";
        }

        void load() throws IOException {
            Properties p = new Properties();
            Path path = propsPath();
            if (!Files.exists(path)) return;
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
            try { return Integer.parseInt(s); }
            catch (Exception e) { return def; }
        }
    }

    private static final class SingleInstance {
        private static final int PORT = 45731;
        private ServerSocket server;

        static SingleInstance claimOrTakeover(ClassicLauncherSWT app) {
            SingleInstance si = new SingleInstance();
            if (si.tryBind(app)) return si;
            requestTakeover();
            long deadline = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < deadline) {
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                if (si.tryBind(app)) return si;
            }
            System.exit(0);
            return null;
        }

        private static void requestTakeover() {
            try (Socket s = new Socket("127.0.0.1", PORT);
                 OutputStream out = s.getOutputStream()) {
                out.write("TAKEOVER\n".getBytes("UTF-8"));
                out.flush();
            } catch (IOException ignored) {}
        }

        private boolean tryBind(ClassicLauncherSWT app) {
            try {
                server = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"));
                new Thread(() -> runServer(app), "SingleInstance-Acceptor").start();
                return true;
            } catch (IOException bindFailed) {
                return false;
            }
        }

        private void runServer(ClassicLauncherSWT app) {
            while (!server.isClosed()) {
                try (Socket s = server.accept();
                     BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"))) {
                    String line = r.readLine();
                    if (line != null && line.startsWith("TAKEOVER")) {
                        app.quitLauncher();
                    }
                } catch (IOException ignored) {}
            }
        }

        void close() {
            try { if (server != null) server.close(); }
            catch (IOException ignored) {}
        }
    }
}
