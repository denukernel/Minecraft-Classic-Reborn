package net.classicremastered.minecraft;

import net.classicremastered.minecraft.chat.CommandRegistry;
import net.classicremastered.minecraft.gui.*;
import net.classicremastered.minecraft.lang.LanguageManager;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.model.ModelManager;
import net.classicremastered.minecraft.net.NetworkManager;
import net.classicremastered.minecraft.particle.ParticleManager;
import net.classicremastered.minecraft.render.*;
import net.classicremastered.minecraft.render.texture.TextureFireFX;
import net.classicremastered.minecraft.render.texture.TextureLavaFX;
import net.classicremastered.minecraft.render.texture.TextureMagmaFX;
import net.classicremastered.minecraft.render.texture.TextureWaterFX;
import net.classicremastered.minecraft.sound.PaulsCodeSoundManager;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Controllers;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.*;
import java.io.File;
import java.nio.IntBuffer;

import javax.swing.*;

/** Initializes the game directory, resources, display, and client services. */
final class ClientBootstrap {
    private ClientBootstrap() {}

    static File prepareGameDirectory() {
        String folder = "minecraftclassicreborn/client";
        String home = System.getProperty("user.home", ".");
        String osName = System.getProperty("os.name").toLowerCase();
        File minecraftFolder;
        switch (OperatingSystemLookup.lookup[
                (osName.contains("win")
                                ? Minecraft$OS.windows
                                : (osName.contains("mac")
                                        ? Minecraft$OS.macos
                                        : (osName.contains("solaris")
                                                ? Minecraft$OS.solaris
                                                : (osName.contains("sunos")
                                                        ? Minecraft$OS.solaris
                                                        : (osName.contains("linux")
                                                                ? Minecraft$OS.linux
                                                                : (osName.contains("unix")
                                                                        ? Minecraft$OS.linux
                                                                        : Minecraft$OS.unknown))))))
                        .ordinal()]) {
            case 1:
                System.out.println("UNKNOWN OS!!!");
                return null;
            case 2:
                minecraftFolder = new File(home, '.' + folder + '/');
                break;
            case 3:
                String appData = System.getenv("APPDATA");
                if (appData != null) {
                    minecraftFolder = new File(appData, "." + folder + '/');
                } else {
                    minecraftFolder = new File(home, '.' + folder + '/');
                }
                break;
            case 4:
                minecraftFolder = new File(home, "Library/Application Support/" + folder);
                break;
            default:
                minecraftFolder = new File(home, folder + '/');
        }

        if (!minecraftFolder.exists() && !minecraftFolder.mkdirs()) {
            throw new RuntimeException(
                    "The working directory could not be created: " + minecraftFolder);
        }

        return minecraftFolder;
    }

    static void initialize(Minecraft mc, File minecraftFolder) throws Exception {

        if (!minecraftFolder.exists()) {
            JOptionPane.showMessageDialog(
                    null,
                    "Welcome to the MCraft Client Alpha 1!"
                            + "\nPleave give MCraft some time to download required files."
                            + "\nTHIS CAN TAKE A LONG TIME DEPENDING ON YOUR INTERNET SPEED.",
                    "Welcome to MCraft Client",
                    JOptionPane.INFORMATION_MESSAGE);
        }

        // ===== RESOURCES: start downloader and BLOCK until finished =====

        mc.resourceThread = new ResourceDownloadThread(Minecraft.mcDir, mc);
        mc.resourceThread.start();

        System.out.println("[Resources] Downloading sounds & music (first run only)...");
        long lastResourceLogMs = 0L;

        // Null-safe wait loop
        while (mc.resourceThread != null && !mc.resourceThread.isFinished()) {
            long now = System.currentTimeMillis();
            if (now - lastResourceLogMs > 2000L) {
                System.out.println("[Resources] Still downloading...");
                lastResourceLogMs = now;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ignored) {
            }
        }
        System.out.println("[Resources] Download complete.");
        // ===============================================================

        // =========================================================
        mc.input = new InputManager(mc);

        // Window / display setup AFTER resources are ready
        if (mc.canvas != null) {
            while (!mc.canvas.isDisplayable()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            Display.setParent(mc.canvas);
        } else if (mc.fullscreen) {
            Display.setFullscreen(true);
            mc.width = Display.getDisplayMode().getWidth();
            mc.height = Display.getDisplayMode().getHeight();
        } else {
            Display.setDisplayMode(new DisplayMode(mc.width, mc.height));
        }

        Display.setTitle("Minecraft 0.30");

        try {
            Display.create();

            // Set initial size from the actual surface
            if (mc.canvas != null && !Display.isFullscreen()) {
                int cw = mc.canvas.getWidth();
                int ch = mc.canvas.getHeight();
                if (cw > 0 && ch > 0) {
                    mc.width = cw;
                    mc.height = ch;
                }
            } else {
                mc.width = Display.getWidth();
                mc.height = Display.getHeight();
            }

            mc.progressBar.setTitle("Starting…");
            mc.progressBar.setText("");

            // GL viewport for the chosen size
            GL11.glViewport(0, 0, mc.width, mc.height);
        } catch (LWJGLException createFail) {
            createFail.printStackTrace();
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
            }
            Display.create();
        }

        Keyboard.create();
        Mouse.create();

        try {
            Controllers.create();
        } catch (Exception controllersCreateError) {
            controllersCreateError.printStackTrace();
        }

        Minecraft.checkGLError("Pre startup");
        GL11.glEnable(3553);
        GL11.glShadeModel(7425);
        GL11.glClearDepth(1.0D);
        GL11.glEnable(2929);
        GL11.glDepthFunc(515);
        GL11.glEnable(3008);
        GL11.glAlphaFunc(516, 0.0F);
        GL11.glCullFace(1029);
        GL11.glMatrixMode(5889);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(5888);
        Minecraft.checkGLError("Startup");

        mc.settings = new GameSettings(mc, minecraftFolder);
        // after mc.settings = new GameSettings(mc, minecraftFolder);
        mc.lang = new LanguageManager(minecraftFolder);
        mc.lang.load("english"); // load default
        // keep any prior value (field default, launcher flag, etc.)
        mc.developer =
                System.getProperty("minecraft.developerMode") != null
                        ? Boolean.getBoolean("minecraft.developerMode")
                        : mc.developer
                                || Minecraft.isDebugger()
                                || Boolean.getBoolean("mc.dev")
                                || "1".equals(System.getenv("MC_DEV"));
        // System.out.println("[dev] developer=" + mc.developer);

        mc.fastDebugMode = Minecraft.isDebugger();
        if (mc.fastDebugMode) {
            mc.settings.limitFramerate = false;
            try {
                org.lwjgl.opengl.Display.setVSyncEnabled(false);
            } catch (Throwable ignored) {
            }
        }
        mc.soundPC = new PaulsCodeSoundManager(mc.settings);
        mc.textureManager = new TextureManager(mc.settings);

        if (mc.resourceThread == null || !mc.resourceThread.isAlive()) {
            try {
                if (mc.resourceThread != null && mc.resourceThread.isAlive()) {
                    mc.resourceThread.running = false;
                    mc.resourceThread.join(5000);
                }
            } catch (InterruptedException ignored) {
            }

            mc.resourceThread = new ResourceDownloadThread(Minecraft.mcDir, mc);
            mc.resourceThread.start();
        }

        mc.textureManager.load(
                net.classicremastered.minecraft.render.BlockTextureAtlas
                        .RESOURCE); // ensure atlas exists
        mc.textureManager.applyBlockTextures(); // stitch per-block tiles

        mc.textureManager.registerAnimation(new TextureLavaFX());
        mc.textureManager.registerAnimation(new TextureMagmaFX());
        mc.textureManager.registerAnimation(new TextureWaterFX());
        mc.textureManager.registerAnimation(new TextureFireFX(Block.FIRE.textureId));
        mc.fontRenderer = new FontRenderer(mc.settings, "/default.png", mc.textureManager);
        IntBuffer cursorBuffer;
        (cursorBuffer = BufferUtils.createIntBuffer(256)).clear().limit(256);
        mc.levelRenderer = new LevelRenderer(mc, mc.textureManager);
        net.classicremastered.minecraft.level.itemstack.Item
                .initItems(); // moved here for startup-only
        // registration

        MobRegistry.bootstrapDefaults();
        Mob.modelCache = new ModelManager();
        net.classicremastered.minecraft.render.entity.EntityRenderBootstrap.init(Mob.modelCache);
        GL11.glViewport(0, 0, mc.width, mc.height);

        if (mc.server != null && mc.session != null) {
            Level tinyNetLevel;
            (tinyNetLevel = new Level()).setData(8, 8, 8, new byte[512]);
            mc.setLevel(tinyNetLevel);
        } else {
            try {
                if (mc.levelName != null) {
                    // still allow online levels if explicitly set
                    mc.loadOnlineLevel(mc.levelName, mc.levelId);
                }
                // else: do nothing → no auto-load from disk
            } catch (Exception loadError) {
                loadError.printStackTrace();
            }

            // if still no level, send player to menu
            if (mc.level == null) {
                mc.setCurrentScreen(new MainMenuScreen());
            }
        }

        if (mc.level != null) {
            mc.skyRenderer = new SkyRenderer(mc.level, mc.textureManager);
            mc.particleManager = new ParticleManager(mc.level, mc.textureManager);
        }
        if (mc.levelLoaded) {
            try {
                mc.cursor = new Cursor(16, 16, 0, 0, 1, cursorBuffer, (IntBuffer) null);
            } catch (LWJGLException cursorFail) {
                cursorFail.printStackTrace();
            }
        }

        try {

            // mc.resourceThread = new ResourceDownloadThread(Minecraft.mcDir, mc);
            // mc.resourceThread.start();
        } catch (Exception ignoreSoundInit) {
            // ignore
        }

        Minecraft.checkGLError("Post startup");
        mc.hud = new HUDScreen(mc, mc.width, mc.height);
        if (mc.developer && mc.hud != null) {
            mc.hud.addChat("&7Developer mode enabled");
        }
        (new SkinDownloadThread(mc)).start();
        CommandRegistry.bootstrap(mc);
        if (mc.server != null && mc.session != null) {
            mc.networkManager =
                    new NetworkManager(
                            mc, mc.server, mc.port, mc.session.username, mc.session.mppass);
        }
    }
}
