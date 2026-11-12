package net.classicremastered.launcher;

import com.mcraft.api.loader.ModLoader; // <<< ADDED

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.MinecraftApplet;
import net.classicremastered.minecraft.util.JInputBootstrap;
import net.classicremastered.util.LwjglNativesDownloader;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import javax.swing.*;

public final class ClassicLauncher {
    public static void main(String[] args) {
        try {
            if (args.length == 0
                    && LauncherUpdater.handOff(resolveMcDir().toPath().resolve("updates"))) {
                return;
            }
            LwjglNativesDownloader.setupWindowsNatives();
            JInputBootstrap.setupWindows();
            ClassicLauncher app = new ClassicLauncher();

            if (args.length == 1 && "--game".equals(args[0])) {
                SwingUtilities.invokeAndWait(
                        () -> {
                            app.loadConfig();
                            app.launchGame(app.cfg.width, app.cfg.height, app.cfg.fullscreen);
                        });
                return;
            }

            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        Process process = app.gameProcess;
                                        if (process != null && process.isAlive()) {
                                            process.destroy();
                                        }
                                    },
                                    "Launcher-Shutdown"));

            // Claim single instance (or ask existing to close, then become primary)
            app.singleInstance = SingleInstance.claimOrTakeover(app);

            SwingUtilities.invokeLater(app::show);
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "An error occurred during startup:\n" + t.toString(),
                    "Minecraft Classic - Startup Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private final JFrame frame = new JFrame("Minecraft Classic Reborn Launcher");

    private final JComboBox<String> cbResolution =
            new JComboBox<>(new String[] {"854 x 480", "1280 x 720", "1600 x 900", "1920 x 1080"});
    private final JComboBox<String> cbMemory =
            new JComboBox<>(new String[] {"1024", "2048", "4096", "6144", "8192"});
    private final JCheckBox cbDeveloper = new JCheckBox("Developer mode", false);
    private final JButton btnAdvanced = new MinecraftButton("Advanced settings");
    private final JPanel advancedSettings = new JPanel(new GridLayout(0, 1, 0, 6));
    private Process gameProcess;

    private final JCheckBox cbFullscreen = new JCheckBox("Fullscreen", false);
    private final JCheckBox cbMultithreaded = new JCheckBox("Multithreaded Core Mode", true);

    private final JButton btnPlay = new MinecraftButton("Play Classic Reborn");
    private final JButton btnQuit = new MinecraftButton("Quit");

    private final JLabel launchSummary = new JLabel();

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

        java.awt.image.BufferedImage bgTexture = null;
        try (InputStream in = ClassicLauncher.class.getResourceAsStream("/dirt.png")) {
            if (in != null) {
                bgTexture = javax.imageio.ImageIO.read(in);
            }
        } catch (IOException ignored) {
        }
        final java.awt.image.BufferedImage finalBgTexture = bgTexture;
        final java.awt.Image finalLogoIcon = logoIcon;

        // Custom Background Root Panel
        JPanel root =
                new JPanel(new BorderLayout(20, 20)) {
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
                            g2.setPaint(
                                    new GradientPaint(
                                            0,
                                            0,
                                            new Color(42, 42, 46),
                                            0,
                                            h,
                                            new Color(20, 20, 24)));
                            g2.fillRect(0, 0, w, h);
                        }
                        g2.dispose();
                    }
                };
        root.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));

        // --- Header / Title Area ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setOpaque(false);

        if (finalLogoIcon != null) {
            JLabel iconLabel =
                    new JLabel(
                            new ImageIcon(
                                    finalLogoIcon.getScaledInstance(36, 36, Image.SCALE_FAST)));
            headerPanel.add(iconLabel, BorderLayout.WEST);
        }

        JPanel titleTextPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titleTextPanel.setOpaque(false);

        JLabel title = new JLabel("Minecraft Classic Reborn");
        title.setFont(new Font("Segoe UI", Font.BOLD, 23));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Build. Explore. Make it your world.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(195, 216, 172));
        titleTextPanel.add(title);
        titleTextPanel.add(subtitle);
        headerPanel.add(titleTextPanel, BorderLayout.CENTER);

        root.add(headerPanel, BorderLayout.NORTH);

        // --- Settings Panel (Dark translucent card) ---
        JPanel pSettings =
                new JPanel(new GridBagLayout()) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(
                                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(0, 0, 0, 140));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                        g2.setColor(new Color(255, 255, 255, 30));
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                        g2.dispose();
                        super.paintComponent(g);
                    }
                };
        pSettings.setOpaque(false);
        pSettings.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 4, 8, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Resolution
        JLabel lblRes = new JLabel("Game resolution");
        lblRes.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRes.setForeground(new Color(220, 220, 220));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.35;
        pSettings.add(lblRes, gbc);

        styleComboBox(cbResolution);
        lblRes.setLabelFor(cbResolution);
        cbResolution.setToolTipText(
                "Choose the game window size. Fullscreen uses this resolution.");
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.65;
        pSettings.add(cbResolution, gbc);

        JLabel lblMem = new JLabel("Java memory limit");
        lblMem.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblMem.setForeground(new Color(220, 220, 220));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.35;
        pSettings.add(lblMem, gbc);

        styleComboBox(cbMemory);
        cbMemory.setToolTipText("Maximum game memory in MB. Applied when you press Play.");
        cbMemory.getAccessibleContext().setAccessibleName("Maximum game memory in MB");
        lblMem.setText("Game memory (MB)");
        lblMem.setLabelFor(cbMemory);
        gbc.gridx = 1;
        gbc.weightx = 0.65;
        pSettings.add(cbMemory, gbc);
        // Row 2: Fullscreen CheckBox
        styleCheckBox(cbFullscreen);
        cbFullscreen.setMnemonic('F');
        cbFullscreen.setToolTipText("Start the game in fullscreen mode.");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(6, 4, 1, 4);
        pSettings.add(cbFullscreen, gbc);

        styleCheckBox(cbMultithreaded);
        cbMultithreaded.setMnemonic('M');
        cbMultithreaded.setToolTipText("Allow the game to use its multithreaded core.");
        styleCheckBox(cbDeveloper);
        cbDeveloper.setMnemonic('D');
        cbDeveloper.setToolTipText("Enable developer commands and tools in the game.");
        advancedSettings.setOpaque(false);
        advancedSettings.add(cbMultithreaded);
        advancedSettings.add(cbDeveloper);
        advancedSettings.setVisible(false);

        gbc.gridy = 3;
        gbc.insets = new Insets(12, 4, 4, 4);
        btnAdvanced.setPreferredSize(new Dimension(235, 32));
        btnAdvanced.setMnemonic('A');
        btnAdvanced.addActionListener(
                e -> {
                    boolean expanded = !advancedSettings.isVisible();
                    advancedSettings.setVisible(expanded);
                    btnAdvanced.setText(expanded ? "Hide advanced settings" : "Advanced settings");
                    frame.setMinimumSize(new Dimension(0, 0));
                    frame.pack();
                    frame.setMinimumSize(frame.getSize());
                });
        pSettings.add(btnAdvanced, gbc);
        gbc.gridy = 4;
        gbc.insets = new Insets(4, 4, 4, 4);
        pSettings.add(advancedSettings, gbc);
        root.add(pSettings, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 14));
        footer.setOpaque(false);
        launchSummary.setForeground(new Color(215, 222, 210));
        launchSummary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel utilities = new JPanel(new BorderLayout(0, 8));
        utilities.setOpaque(false);
        utilities.add(launchSummary, BorderLayout.NORTH);
        JButton toolsButton = new MinecraftButton("Tools and utilities");
        toolsButton.addActionListener(e -> showTools());
        utilities.add(toolsButton, BorderLayout.CENTER);
        footer.add(utilities, BorderLayout.NORTH);

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);
        btnQuit.setPreferredSize(new Dimension(90, 42));
        btnPlay.setPreferredSize(new Dimension(260, 42));
        btnPlay.setMnemonic('P');
        btnQuit.setMnemonic('Q');
        actions.add(btnQuit, BorderLayout.WEST);
        actions.add(btnPlay, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        // Quit: stop any running game first
        btnQuit.addActionListener(
                e -> {
                    try {
                        if (runningMc != null) {
                            if (askedToQuitRef != null) askedToQuitRef[0] = true;
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
                        if (singleInstance != null) singleInstance.close();
                    } catch (Throwable ignored) {
                    }
                    System.exit(0);
                });

        // Play: if already running, just focus the game window; otherwise launch
        btnPlay.addActionListener(
                e -> {
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

                    if (saveConfig()) {
                        startGameProcess();
                    }
                });

        if (((DefaultComboBoxModel<String>) cbResolution.getModel()).getIndexOf(cfg.resString())
                < 0) {
            cbResolution.addItem(cfg.resString());
        }
        cbResolution.setSelectedItem(cfg.resString());

        String memory = String.valueOf(cfg.ramMb);
        if (((DefaultComboBoxModel<String>) cbMemory.getModel()).getIndexOf(memory) < 0) {
            cbMemory.addItem(memory);
        }
        cbMemory.setSelectedItem(memory);
        cbDeveloper.setSelected(cfg.developerMode);
        cbFullscreen.setSelected(cfg.fullscreen);
        cbMultithreaded.setSelected(cfg.multithreaded);

        cbMemory.addActionListener(e -> updateLaunchSummary());
        cbDeveloper.addItemListener(e -> updateLaunchSummary());
        cbResolution.addActionListener(e -> updateLaunchSummary());
        cbFullscreen.addItemListener(e -> updateLaunchSummary());
        cbMultithreaded.addItemListener(e -> updateLaunchSummary());
        updateLaunchSummary();
        frame.getRootPane().setDefaultButton(btnPlay);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setResizable(true);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    private void updateLaunchSummary() {
        launchSummary.setText(
                (String) cbResolution.getSelectedItem()
                        + "  |  "
                        + (cbFullscreen.isSelected() ? "Fullscreen" : "Windowed")
                        + "  |  "
                        + cbMemory.getSelectedItem()
                        + " MB"
                        + (cbDeveloper.isSelected() ? "  |  Developer" : ""));
    }

    private static void styleComboBox(JComboBox<String> combo) {
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        combo.setBackground(new Color(36, 36, 40));
        combo.setForeground(Color.WHITE);
        combo.setFocusable(true);
        combo.setPreferredSize(new Dimension(235, 34));
        combo.setRenderer(
                new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(
                            JList<?> list,
                            Object value,
                            int index,
                            boolean isSelected,
                            boolean cellHasFocus) {
                        super.getListCellRendererComponent(
                                list, value, index, isSelected, cellHasFocus);
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
        cb.setFocusPainted(true);
        cb.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    /** Custom styled Minecraft button matching classic stone button aesthetics */
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
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            int w = getWidth();
            int h = getHeight();
            boolean pressed = getModel().isPressed();
            boolean hover = getModel().isRollover() || isFocusOwner();
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

            if (isFocusOwner()) {
                g2.setColor(new Color(255, 255, 160));
                g2.drawRect(4, 4, w - 9, h - 9);
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
            if (appData == null || appData.isEmpty()) appData = home;
            return new java.io.File(appData, ".minecraftclassicreborn/client/");
        } else if (os.contains("mac")) {
            return new java.io.File(
                    home, "Library/Application Support/.minecraftclassicreborn/client/");
        } else {
            // linux/unix
            return new java.io.File(home, ".minecraftclassicreborn/client/");
        }
    }

    private static int[] parseRes(String s) {
        if (s == null) return new int[] {854, 480};
        String[] parts = s.split("x");
        return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    }

    /* ================= Launch ================= */

    private java.util.List<String> gameCommand() {
        String executable =
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe"
                        : "java";
        return java.util.Arrays.asList(
                Paths.get(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx" + cfg.ramMb + "m",
                "-Dminecraft.developerMode=" + cfg.developerMode,
                "-cp",
                System.getProperty("java.class.path"),
                ClassicLauncher.class.getName(),
                "--game");
    }

    private void showTools() {
        JDialog dialog = new JDialog(frame, "Tools and utilities", false);
        JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        addTool(
                panel,
                "World editor",
                "Textured 3D worlds, blocks, mobs and simulation",
                "net.classicremastered.toolkit.mapedit3d.Classic3DMapEditorApp");
        addTool(
                panel,
                "Mob model editor",
                "Parts, textures, UVs and animation previews",
                "net.classicremastered.toolkit.modeleditor.ModelEditorApp");
        addTool(
                panel,
                "MD3 viewer and converter",
                "Animated surfaces and OBJ conversion",
                "net.classicremastered.toolkit.md3studio.MD3Studio");
        addTool(
                panel,
                "Server",
                "Host and manage a Classic Reborn server",
                "net.classicremastered.toolkit.server.ClassicServer");
        for (String name : new String[] {"Saves", "Resources", "Logs"}) {
            JButton button = new JButton("Open " + name.toLowerCase());
            button.addActionListener(
                    e -> {
                        try {
                            File directory =
                                    name.equals("Resources")
                                            ? new File(resolveMcDir(), "texturepacks")
                                            : resolveMcDir();
                            Files.createDirectories(directory.toPath());
                            Desktop.getDesktop().open(directory);
                        } catch (Exception error) {
                            JOptionPane.showMessageDialog(
                                    dialog,
                                    error.getMessage(),
                                    "Cannot open folder",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
            panel.add(button);
        }
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void addTool(JPanel panel, String title, String description, String entryPoint) {
        JButton button = new JButton(title + " - " + description);
        button.addActionListener(
                e -> {
                    Path log =
                            resolveMcDir()
                                    .toPath()
                                    .resolve(
                                            "tool-"
                                                    + entryPoint.substring(
                                                            entryPoint.lastIndexOf('.') + 1)
                                                    + ".log");
                    try {
                        Files.createDirectories(log.getParent());
                        String executable =
                                System.getProperty("os.name", "").toLowerCase().contains("win")
                                        ? "java.exe"
                                        : "java";
                        Process process =
                                new ProcessBuilder(
                                                Paths.get(
                                                                System.getProperty("java.home"),
                                                                "bin",
                                                                executable)
                                                        .toString(),
                                                "-Xmx" + cfg.ramMb + "m",
                                                "-cp",
                                                System.getProperty("java.class.path"),
                                                entryPoint)
                                        .redirectErrorStream(true)
                                        .redirectOutput(log.toFile())
                                        .start();
                        button.setEnabled(false);
                        new SwingWorker<Integer, Void>() {
                            protected Integer doInBackground() throws InterruptedException {
                                return process.waitFor();
                            }

                            protected void done() {
                                button.setEnabled(true);
                                try {
                                    if (get() != 0)
                                        JOptionPane.showMessageDialog(
                                                frame,
                                                "Tool stopped. Details: " + log,
                                                "Tool error",
                                                JOptionPane.ERROR_MESSAGE);
                                } catch (Exception error) {
                                    JOptionPane.showMessageDialog(
                                            frame,
                                            error.getMessage(),
                                            "Tool error",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }.execute();
                    } catch (IOException error) {
                        JOptionPane.showMessageDialog(
                                frame,
                                error.getMessage() + "\nLog: " + log,
                                "Tool launch failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                });
        panel.add(button);
    }

    private void startGameProcess() {
        Path log = resolveMcDir().toPath().resolve("launcher-game.log");
        try {
            Files.createDirectories(log.getParent());
            gameProcess =
                    new ProcessBuilder(gameCommand())
                            .redirectErrorStream(true)
                            .redirectOutput(log.toFile())
                            .start();
        } catch (IOException error) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Could not start Java:\n" + error.getMessage(),
                    "Launch failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        btnPlay.setEnabled(false);
        frame.setVisible(false);
        Process process = gameProcess;
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws InterruptedException {
                return process.waitFor();
            }

            @Override
            protected void done() {
                gameProcess = null;
                btnPlay.setEnabled(true);
                frame.setVisible(true);
                frame.toFront();
                try {
                    if (get() != 0) {
                        JOptionPane.showMessageDialog(
                                frame,
                                "The game exited with an error. Try a lower memory setting.\n"
                                        + "Details: "
                                        + log,
                                "Game stopped",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(
                            frame, error.getMessage(), "Launch failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void launchGame(int width, int height, boolean fullscreen) {
        frame.setVisible(false);

        JFrame win = new JFrame("Minecraft Classic " + Constants.MINECRAFT_VERSION);
        this.gameWindow = win;

        // Set game window icon(s)
        try {
            var icons = loadIcons();
            if (!icons.isEmpty()) win.setIconImages(icons);
        } catch (Throwable ignored) {
        }

        win.setLayout(new BorderLayout());
        win.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        System.setProperty("minecraft.ramMb", String.valueOf(cfg.ramMb));
        System.setProperty("minecraft.multithreaded", String.valueOf(cfg.multithreaded));

        final MCraftApplet applet = new MCraftApplet();
        final MinecraftCanvas canvas = new MinecraftCanvas();
        final Minecraft mc = new Minecraft(canvas, applet, width, height, fullscreen);
        mc.developer = Boolean.getBoolean("minecraft.developerMode");
        final boolean[] askedToQuit = {false}; // simple mutable flag

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
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Ensure the canvas dictates the window size
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.setMinimumSize(new Dimension(width, height));
        canvas.setMinecraft(mc);
        win.add(canvas, BorderLayout.CENTER);

        // === Window close hooks ===
        win.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        askedToQuit[0] = true;
                        mc.requestQuit();
                    }

                    @Override
                    public void windowClosed(WindowEvent e) {
                        System.exit(0);
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
        new Thread(
                        () -> {
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
                                        if (singleInstance != null) singleInstance.close();
                                    } catch (Throwable ignored) {
                                    }
                                    return;
                                }
                                try {
                                    Thread.sleep(16);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        },
                        "Launcher-Watcher")
                .start();
    }

    // Load PNG icons from classpath; tries a few common locations.
    private static java.util.List<Image> loadIcons() {
        java.util.List<Image> out = new java.util.ArrayList<>(2);
        String[] paths = {"/cube32.png", "/icons/cube32.png"};
        for (String p : paths) {
            try {
                java.net.URL u = ClassicLauncher.class.getResource(p);
                if (u != null) {
                    java.awt.Image img = javax.imageio.ImageIO.read(u);
                    if (img != null) out.add(img);
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

    private boolean saveConfig() {
        String res = (String) cbResolution.getSelectedItem();
        cfg.width = parseRes(res)[0];
        cfg.height = parseRes(res)[1];
        cfg.fullscreen = cbFullscreen.isSelected();
        cfg.ramMb = Integer.parseInt((String) cbMemory.getSelectedItem());
        cfg.developerMode = cbDeveloper.isSelected();
        cfg.multithreaded = cbMultithreaded.isSelected();
        try {
            cfg.save();
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Could not save launch settings. " + e.getMessage(),
                    "Settings error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
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
                return new URL("https://classicremastered.net/");
            } catch (MalformedURLException e) {
                return null;
            }
        }

        @Override
        public URL getCodeBase() {
            try {
                return new URL("https://classicremastered.net/");
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
            if (thread != null) return;

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
            System.out.println(
                    "[SingleInstance] Port " + PORT + " is busy. Requesting takeover...");
            requestTakeover();
            long deadline = System.currentTimeMillis() + 3000; // 3s
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                if (si.tryBind(app)) return si;
            }

            // If we still didn't get it, it might be a real port conflict or the other instance is
            // hanging.
            int choice =
                    JOptionPane.showConfirmDialog(
                            null,
                            "Another instance of Minecraft Classic appears to be running, or port "
                                    + PORT
                                    + " is blocked.\n"
                                    + "Do you want to try starting anyway? (This may cause issues"
                                    + " if another instance is active)",
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
            try (Socket s = new Socket("127.0.0.1", PORT);
                    OutputStream out = s.getOutputStream()) {
                out.write("TAKEOVER\n".getBytes("UTF-8"));
                out.flush();
            } catch (IOException ignored) {
                // No listener — maybe it closed in the meantime; new bind will succeed.
            }
        }

        /** Try binding the control port; on success, start acceptor that listens for TAKEOVER. */
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
                        BufferedReader r =
                                new BufferedReader(
                                        new InputStreamReader(s.getInputStream(), "UTF-8"))) {
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
                if (server != null) server.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Called by SingleInstance server thread when another instance asks to TAKEOVER. */
    void forceCloseFromExternalRequest() {
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        if (runningMc != null) {
                            if (askedToQuitRef != null) askedToQuitRef[0] = true;
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
        boolean developerMode = false;

        private static Path propsPath() {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty()) appData = System.getProperty("user.home");
            return Paths.get(appData, ".minecraftclassicreborn", "client", "launcher.properties");
        }

        String resString() {
            return width + " x " + height;
        }

        void load() throws IOException {
            Properties p = new Properties();
            Path path = propsPath();
            if (!Files.exists(path)) return;
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            }
            width = Math.max(320, Math.min(7680, parseInt(p.getProperty("width"), width)));
            height = Math.max(240, Math.min(4320, parseInt(p.getProperty("height"), height)));
            fullscreen =
                    Boolean.parseBoolean(p.getProperty("fullscreen", String.valueOf(fullscreen)));
            ramMb = Math.max(512, Math.min(32768, parseInt(p.getProperty("ramMb"), ramMb)));
            developerMode = Boolean.parseBoolean(p.getProperty("developerMode", "false"));
            multithreaded =
                    Boolean.parseBoolean(
                            p.getProperty("multithreaded", String.valueOf(multithreaded)));
        }

        void save() throws IOException {
            Properties p = new Properties();
            p.setProperty("width", String.valueOf(width));
            p.setProperty("height", String.valueOf(height));
            p.setProperty("fullscreen", String.valueOf(fullscreen));
            p.setProperty("ramMb", String.valueOf(ramMb));
            p.setProperty("developerMode", String.valueOf(developerMode));
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
