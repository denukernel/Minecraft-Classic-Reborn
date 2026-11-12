package net.classicremastered.minecraft;

import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.gamemode.GameMode;
import net.classicremastered.minecraft.gamemode.SpectatorGameMode;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.gui.*;
import net.classicremastered.minecraft.lang.LanguageManager;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelIO;
import net.classicremastered.minecraft.level.generator.LevelGenerator;
import net.classicremastered.minecraft.level.itemstack.BowItem;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.FakeHerobrine;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.net.NetworkManager;
import net.classicremastered.minecraft.particle.ParticleManager;
import net.classicremastered.minecraft.particle.WaterDropParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.InputHandlerImpl;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.*;
import net.classicremastered.minecraft.render.Renderer;
import net.classicremastered.minecraft.render.texture.TextureFX;
import net.classicremastered.minecraft.sound.PaulsCodeSoundManager;
import net.classicremastered.minecraft.sound.SoundManager;
import net.classicremastered.minecraft.sound.SoundPlayer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.io.*;
import java.io.File;

import javax.swing.*;

public final class Minecraft implements Runnable {

    public GameMode gamemode = new SurvivalGameMode(this);
    private GameMode previousGameMode;
    public boolean debugOverlay;
    boolean hudHidden;
    public MovingObjectPosition debugBlockTarget;
    boolean fullscreen = false;
    public int width;
    public int height;
    public Timer timer = new Timer(20.0F);
    public Level level;
    public LevelRenderer levelRenderer;
    public Player player;
    public ParticleManager particleManager;
    public CreativeInventoryBlocks session = null;
    public String host;
    public LanguageManager lang;
    SkyRenderer skyRenderer;

    public Canvas canvas;
    public boolean levelLoaded = false;
    public volatile boolean waiting = false;
    Cursor cursor;
    public TextureManager textureManager;
    public FontRenderer fontRenderer;
    public GuiScreen currentScreen = null;
    private float pausedRenderPartial;
    final FabulousTransparency transparency = new FabulousTransparency();

    public boolean isLocalWorldPaused() {
        return level != null
                && !isOnline()
                && !level.isNetworkMode()
                && currentScreen != null
                && currentScreen.pausesLocalWorld();
    }

    public float worldRenderPartial() {
        return isLocalWorldPaused() ? pausedRenderPartial : timer.delta;
    }

    public ProgressBarDisplay progressBar = new ProgressBarDisplay(this);
    public Renderer renderer = new Renderer(this);
    public LevelIO levelIo;
    public SoundManager sound;
    ResourceDownloadThread resourceThread;
    public int ticks;
    // Minecraft.java
    public int cameraMode = 0;
    // 0 = first person, 1 = third person back, 2 = third person front

    private int blockHitTime;
    public String levelName;
    public int levelId;
    public Robot robot;
    public HUDScreen hud;
    public boolean online;
    public NetworkManager networkManager;
    public SoundPlayer soundPlayer;
    public MovingObjectPosition selected;
    public GameSettings settings;
    private MinecraftApplet applet;
    public String server;
    public int port;
    public volatile boolean running;
    public String debug;
    public boolean hasMouse;
    private boolean parsecMouseCaptured;
    private int parsecMouseX;
    private int parsecMouseY;
    public boolean raining;
    volatile boolean pendingScreenshot = false;
    boolean fastDebugMode = false;
    public InputManager imput;
    public static File mcDir;
    public PaulsCodeSoundManager soundPC;
    public boolean debugHitboxes = false;

    public Minecraft(Canvas var1, MinecraftApplet var2, int var3, int var4, boolean var5) {
        this.levelIo = new LevelIO(this.progressBar);
        this.ticks = 0;
        this.blockHitTime = 0;

        this.levelName = null;
        this.levelId = 0;
        this.online = false;
        new HumanoidModel(0.0F);
        this.selected = null;
        this.server = null;
        this.port = 0;
        this.running = false;
        this.debug = "";
        this.hasMouse = false;
        this.raining = false;

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception var7) {
            var7.printStackTrace();
        }

        this.applet = var2;
        Thread t = new SleepForeverThread(this);
        t.setDaemon(true);
        t.start();
        this.canvas = var1;
        this.width = var3;
        this.height = var4;
        this.fullscreen = var5;
        if (var1 != null) {
            try {
                this.robot = new Robot();
                return;
            } catch (AWTException var8) {
                var8.printStackTrace();
            }
        }
    }

    public final void setCurrentScreen(GuiScreen next) {
        if (next == null && this.level == null) next = new MainMenuScreen();
        if (this.currentScreen instanceof ErrorScreen) {
            ErrorScreen err = (ErrorScreen) this.currentScreen;
            if (!err.hasParent()) {
                return;
            }
        }

        if (this.currentScreen != null) this.currentScreen.onClose();
        if (next instanceof MainMenuScreen && this.soundPC != null) {
            this.soundPC.resetAudioState();
        }

        if (next == null && this.player != null && this.player.health <= 0)
            next = new GameOverScreen();

        boolean wasPaused = isLocalWorldPaused();
        this.currentScreen = next;
        if (!wasPaused && isLocalWorldPaused()) pausedRenderPartial = timer.delta;
        if (next != null) {
            BowItem.cancelDraw(this.player);
            if (this.hasMouse) {
                if (this.player != null) this.player.releaseAllKeys();
                this.hasMouse = false;
                try {
                    if (this.parsecMouseCaptured || this.levelLoaded) Mouse.setNativeCursor(null);
                    else Mouse.setGrabbed(false);
                } catch (LWJGLException e) {
                    e.printStackTrace();
                } finally {
                    this.parsecMouseCaptured = false;
                }
            }
            int uiW = this.width * 240 / this.height;
            int uiH = this.height * 240 / this.height;
            next.open(this, uiW, uiH);
            this.online = false;
        } else {
            this.grabMouse();
        }
    }

    static void checkGLError(String var0) {
        int var1;
        if ((var1 = GL11.glGetError()) != 0) {
            String var2 = GLU.gluErrorString(var1);
            System.out.println("########## GL ERROR ##########");
            System.out.println("@ " + var0);
            System.out.println(var1 + ": " + var2);
            System.exit(0);
        }
    }

    // REMOVE calls to this if possible. If you must keep it:
    private boolean isSystemShuttingDown() {
        // Don’t reflect into private JRE internals; just say “no”.
        return false;
    }

    // Add near other fields/methods:
    public static File getMinecraftDir() {
        return mcDir != null ? mcDir : new File(System.getProperty("user.dir", ".")); // fallback
    }

    public void startSingleplayerWith(Level L) {
        this.gamemode.prepareLevel(L);
        this.setLevel(L);
        this.setCurrentScreen(null);
        this.grabMouse();
    }

    public final void shutdown() {
        // stop main loop so no more ticks render while tearing down
        this.running = false;

        // 1) Networking: disconnect if connected
        try {
            if (this.networkManager != null) {
                try {
                    this.networkManager.netHandler.close();
                } catch (Throwable ignored) {
                }
                this.networkManager = null;
                this.online = false;
            }
        } catch (Throwable ignored) {
        }

        // 2) Sound: stop thread + close line
        try {
            if (this.soundPlayer != null) {
                this.soundPlayer.running = false;
                try {
                    if (this.soundPlayer.dataLine != null) {
                        this.soundPlayer.dataLine.stop();
                        this.soundPlayer.dataLine.flush();
                        this.soundPlayer.dataLine.close();
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        // 2b) PaulsCode SoundSystem cleanup
        try {
            if (this.soundPC != null) {
                this.soundPC.shutdown();
                this.soundPC = null;
            }
        } catch (Throwable ignored) {
        }

        // 3) Background/resource threads: mark not running
        try {
            if (this.resourceThread != null) {
                this.resourceThread.running = false; // was true by mistake
            }
        } catch (Throwable ignored) {
        }

        // 4) Save singleplayer world if present (non-fatal)
        // 4) Save singleplayer world if present (non-fatal)
        try {
            saveCurrentLevel();
        } catch (Throwable ignored) {
        }

        // 5) Unload level & renderer state
        try {
            // setLevel(null) already hard-resets renderer lists and particles safely
            this.setLevel(null);
        } catch (Throwable ignored) {
        }

        // 6) Tear down LWJGL input safely
        try {
            if (org.lwjgl.input.Mouse.isCreated()) org.lwjgl.input.Mouse.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Keyboard.isCreated()) org.lwjgl.input.Keyboard.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Controllers.isCreated()) org.lwjgl.input.Controllers.destroy();
        } catch (Throwable ignored) {
        }

        // 7) Tear down display safely
        try {
            if (org.lwjgl.opengl.Display.isCreated()) {
                transparency.release();
                org.lwjgl.opengl.Display.destroy();
            }
        } catch (Throwable ignored) {
        }

        // 8) Dispose host window if embedded (prevents AWT thread from keeping JVM
        // alive)
        try {
            if (this.canvas != null) {
                java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this.canvas);
                if (w != null) w.dispose();
            }
        } catch (Throwable ignored) {
        }

        // 9) Clear current screen to avoid accidental mouse grab later
        try {
            this.currentScreen = null;
        } catch (Throwable ignored) {
        }

        // 10) Encourage cleanup
        try {
            System.gc();
        } catch (Throwable ignored) {
        }
    }

    private volatile boolean quitRequested = false;

    public void requestQuit() {
        // signal main loop to end; DO NOT destroy Display here
        quitRequested = true;
        running = false;

        // stop background helpers so loop doesn't keep scheduling work
        try {
            if (soundPlayer != null) soundPlayer.running = false;
        } catch (Throwable ignored) {
        }
        try {
            if (resourceThread != null) resourceThread.running = false;
        } catch (Throwable ignored) {
        }
        // If you have any custom non-daemon threads, interrupt them here.
    }

    public void quitToDesktop() {
        shutdown();
        if (this.soundPC != null) {
            this.soundPC.resetAudioState();
        }
        if (this.applet == null) {
            try {
                System.exit(0);
            } catch (Throwable ignored) {
            }
        }
    }

    // --- Flight double-tap SPACE toggle state ---
    int lastJumpTapTick = -10000;
    int jumpTapWindow = 7; // ticks (~0.35s at 20 TPS)
    boolean jumpTapArmed = false; // first tap armed
    public boolean developer = false;
    public InputManager input;

    public final void run() {
        this.running = true;

        File minecraftFolder = ClientBootstrap.prepareGameDirectory();
        if (minecraftFolder == null) return;
        mcDir = minecraftFolder;
        this.soundPlayer = new SoundPlayer(this.settings);
        this.soundPlayer.startAudio(); // opens 44.1k/16/Stereo big-endian with a large buffer
        // optional sanity tone:
        this.soundPlayer.debugBeep();
        try {
            ClientBootstrap.initialize(this, minecraftFolder);
        } catch (Exception bootError) {
            bootError.printStackTrace();
            JOptionPane.showMessageDialog(
                    (Component) null, bootError.toString(), "Failed to start Minecraft", 0);
            return;
        }

        long fpsTimerStartMs = System.currentTimeMillis();
        int framesThisSecond = 0;

        try {
            while (this.running) {
                while (this.running) {
                    if (this.waiting) {
                        try {
                            if (org.lwjgl.opengl.Display.isCreated()) {
                                // minimal GUI-only frame so ErrorScreen is visible
                                GL11.glViewport(0, 0, this.width, this.height);
                                GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                                GL11.glClear(16640); // GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT
                                this.renderer.enableGuiMode();
                                if (this.currentScreen != null) {
                                    this.currentScreen.render(0, 0);
                                }
                                org.lwjgl.opengl.Display.update();
                                if (this.settings != null && this.settings.limitFramerate) {
                                    org.lwjgl.opengl.Display.sync(60);
                                }
                            }
                        } catch (Throwable t) {
                            handleCrash(t);
                        }
                        Thread.sleep(50L);
                        continue; // IMPORTANT: skip the tick path below
                    } else {
                        if (this.canvas == null && Display.isCloseRequested()) {
                            this.running = false;
                        }
                        if (quitRequested) break;
                        if (this.canvas != null && !Display.isFullscreen()) {
                            final int cw = this.canvas.getWidth();
                            final int ch = this.canvas.getHeight();
                            if (cw > 0 && ch > 0 && (cw != this.width || ch != this.height)) {
                                resizeTo(cw, ch);
                            }
                        }

                        try {
                            Timer t = this.timer;
                            long nowMs;
                            long sysDeltaMs = (nowMs = System.currentTimeMillis()) - t.lastSysClock;
                            long hrNowMs = System.nanoTime() / 1000000L;
                            double frameDelta;
                            if (sysDeltaMs > 1000L) {
                                long hrDeltaMs = hrNowMs - t.lastHRClock;
                                frameDelta = (double) sysDeltaMs / (double) hrDeltaMs;
                                t.adjustment += (frameDelta - t.adjustment) * 0.20000000298023224D;
                                t.lastSysClock = nowMs;
                                t.lastHRClock = hrNowMs;
                            }

                            if (sysDeltaMs < 0L) {
                                t.lastSysClock = nowMs;
                                t.lastHRClock = hrNowMs;
                            }

                            double hrNowS;
                            frameDelta =
                                    ((hrNowS = (double) hrNowMs / 1000.0D) - t.lastHR)
                                            * t.adjustment;
                            t.lastHR = hrNowS;
                            if (frameDelta < 0.0D) frameDelta = 0.0D;
                            if (frameDelta > 1.0D) frameDelta = 1.0D;

                            t.elapsedDelta =
                                    (float)
                                            ((double) t.elapsedDelta
                                                    + frameDelta
                                                            * (double) t.speed
                                                            * (double) t.tps);
                            t.elapsedTicks = (int) t.elapsedDelta;
                            int limit = !isOnline()
                                    && (level instanceof net.classicremastered.minecraft.level.LevelInfiniteTerrain
                                            || level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat)
                                                    ? 5
                                                    : 100;
                            if (t.elapsedTicks > limit) {
                                t.elapsedDelta -= (float) (t.elapsedTicks - limit);
                                t.elapsedTicks = limit;
                            }
                            t.elapsedDelta -= (float) t.elapsedTicks;
                            t.delta = t.elapsedDelta;

                            for (int i = 0; i < this.timer.elapsedTicks; ++i) {
                                boolean wasPaused = isLocalWorldPaused();
                                if (!wasPaused) ++this.ticks;
                                this.tick();
                                if (wasPaused && !isLocalWorldPaused()) break;
                            }

                            // Let mods tick once per frame (after LWJGL is ready)
                            com.mcraft.api.hooks.Hooks.onClientTick(this);
                            if (this.soundPC != null && this.player != null) {
                                this.soundPC.setListener(this.player, worldRenderPartial());

                                // Update distance for Far Lands checks
                                long dist =
                                        (long)
                                                Math.max(
                                                        Math.abs(this.player.x),
                                                        Math.abs(this.player.z));
                                this.soundPC.playerDist = dist;
                                this.soundPC.farlandsActive = (dist >= 1_000_000);

                                // Tick audio corruption
                                this.soundPC.tickFarlandsAudio();
                                String msg = this.soundPC.maybeGetHerobrineMessage();
                                if (msg != null && this.hud != null) {
                                    this.hud.addChat(msg);
                                }
                            }
                            if (this.soundPC.shouldSpawnHerobrineFigure()
                                    && this.level != null
                                    && this.player != null) {
                                FakeHerobrine ghost = new FakeHerobrine(this.level);

                                // Pick random position ~20–40 blocks away
                                double angle = this.soundPC.rng.nextDouble() * Math.PI * 2;
                                double dist = 20 + this.soundPC.rng.nextDouble() * 20;
                                float x = (float) (this.player.x + Math.cos(angle) * dist);
                                float z = (float) (this.player.z + Math.sin(angle) * dist);
                                int y = this.level.getHighestTile((int) x, (int) z);

                                ghost.setPos(x, y, z);
                                this.level.addEntity(ghost);
                            }
                            ClientFrameRenderer.render(this);

                            if (this.settings.limitFramerate) {
                                Display.sync(144); // changed: accurate cap, no fake FPS
                            }

                            checkGLError("Post render");
                            ++framesThisSecond;
                            while (System.currentTimeMillis() >= fpsTimerStartMs + 1000L) {
                                net.classicremastered.minecraft.util.Debug.flushProfiler(
                                        framesThisSecond);
                                this.debug =
                                        framesThisSecond
                                                + " fps, "
                                                + Chunk.chunkUpdates
                                                + " chunk updates";
                                Chunk.chunkUpdates = 0;
                                fpsTimerStartMs += 1000L;
                                framesThisSecond = 0;
                            }
                        } catch (Throwable loopError) {
                            handleCrash(loopError);
                        }
                    }
                }
                return;
            }
        } catch (StopGameException ignored) {
            // ignored
        } catch (Exception fatalLoopError) {
            fatalLoopError.printStackTrace();
            return;
        } finally {
            try {
                safeShutdownOnRenderThread();
            } catch (Throwable ignored) {
            }
        }
    }

    private void safeShutdownOnRenderThread() {
        // Networking
        try {
            if (networkManager != null) {
                try {
                    networkManager.netHandler.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        networkManager = null;
        online = false;

        // Save world if present
        try {
            saveCurrentLevel();
        } catch (Throwable ignored) {
        }

        // Unload level & renderer
        try {
            setLevel(null);
        } catch (Throwable ignored) {
        }

        // Stop helpers (already signaled, but repeat safely)
        try {
            if (soundPlayer != null) {
                soundPlayer.running = false;
                try {
                    if (soundPlayer.dataLine != null) {
                        soundPlayer.dataLine.stop();
                        soundPlayer.dataLine.flush();
                        soundPlayer.dataLine.close();
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (resourceThread != null) resourceThread.running = false;
        } catch (Throwable ignored) {
        }

        // Input/Window — only from this thread
        try {
            if (org.lwjgl.input.Mouse.isCreated()) org.lwjgl.input.Mouse.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Keyboard.isCreated()) org.lwjgl.input.Keyboard.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Controllers.isCreated()) org.lwjgl.input.Controllers.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.opengl.Display.isCreated()) {
                transparency.release();
                org.lwjgl.opengl.Display.destroy();
            }
        } catch (Throwable ignored) {
        }

        // Dispose AWT wrapper window if present
        try {
            if (this.canvas != null) {
                this.canvas.setIgnoreRepaint(true);
                java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this.canvas);
                if (w instanceof java.awt.Frame) ((java.awt.Frame) w).setIgnoreRepaint(true);
            }
        } catch (Throwable ignored) {
        }
    }

    public final void grabMouse() {
        if (this.level == null || this.player == null) {
            if (this.currentScreen == null) this.setCurrentScreen(new MainMenuScreen());
            return;
        }
        if (!this.hasMouse) {
            this.hasMouse = true;
            if (this.settings != null && this.settings.parsecMode) {
                this.captureParsecMouse();
            } else if (this.levelLoaded) {
                try {
                    Mouse.setNativeCursor(this.cursor);
                    Mouse.setCursorPosition(this.width / 2, this.height / 2);
                } catch (LWJGLException var2) {
                    var2.printStackTrace();
                }

                if (this.canvas != null) {
                    this.canvas.requestFocus();
                }
            } else {
                Mouse.setGrabbed(true);
            }

            this.setCurrentScreen((GuiScreen) null);
        }
    }

    private void captureParsecMouse() {
        Mouse.setGrabbed(false);
        this.parsecMouseCaptured = true;
        try {
            if (this.cursor == null) {
                this.cursor = new Cursor(16, 16, 0, 0, 1, BufferUtils.createIntBuffer(256), null);
            }
            Mouse.setNativeCursor(this.cursor);
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        if (this.canvas != null) this.canvas.requestFocus();
        this.centerParsecMouse(Display.getWidth(), Display.getHeight());
    }

    private void centerParsecMouse(int width, int height) {
        this.parsecMouseX = width / 2;
        this.parsecMouseY = height / 2;
        Mouse.setCursorPosition(this.parsecMouseX, this.parsecMouseY);
    }

    boolean updateParsecMouseLook() {
        if (!this.parsecMouseCaptured) return false;
        int x = Mouse.getX();
        int y = Mouse.getY();
        int dx = x - this.parsecMouseX;
        int dy = y - this.parsecMouseY;
        this.parsecMouseX = x;
        this.parsecMouseY = y;

        // Leave room for more motion before the cursor can reach the window edge.
        int width = Display.getWidth();
        int height = Display.getHeight();
        int margin = Math.min(width, height) / 4;
        if (x < margin || y < margin || x >= width - margin || y >= height - margin) {
            this.centerParsecMouse(width, height);
        }
        this.player.turn(dx, this.settings.invertMouse ? -dy : dy);
        return true;
    }

    public final void pause() {
        if (this.currentScreen == null) {
            this.setCurrentScreen(this.level == null ? new MainMenuScreen() : new PauseScreen());
        }
    }

    private void waitForCanvasPeer() {
        if (this.canvas == null) return;
        // Wait until AWT has created the native peer and the component is showing
        int spins = 0;
        while (!this.canvas.isDisplayable() || !this.canvas.isShowing()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            if (++spins > 500) break; // ~5s safety
        }
    }

    // Minecraft.java
    // Minecraft.java
    // Minecraft.java
    public void onMouseClick(int button) {
        if (this.input != null) {
            this.input.onMouseClick(button);
        }
    }

    private volatile boolean crashLatched = false;

    private void handleCrash(Throwable loopError) {
        if (crashLatched) return;
        crashLatched = true;

        if (!(loopError instanceof net.classicremastered.minecraft.errors.CrashReportException)) {
            loopError =
                    new net.classicremastered.minecraft.errors.CrashReportException(
                            "Unhandled exception in main loop", loopError);
        }

        loopError.printStackTrace();

        try {
            System.out.println("[Crash] Unhandled Exception Caught! Freezing the client...");
            File logs = new File(Minecraft.mcDir, "crash_log.txt");
            java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(logs, true));
            pw.println("---- Minecraft Classic Crash Report ----");
            pw.println(new java.util.Date());
            loopError.printStackTrace(pw);
            pw.println("----------------------------------------");
            pw.close();
            System.out.println("[Crash] Written to " + logs.getAbsolutePath());
        } catch (Throwable ignored) {
        }

        try {
            net.classicremastered.minecraft.util.Screenshot.take(Minecraft.mcDir);
            System.out.println("[Crash] Screenshot captured for debugging.");
        } catch (Throwable ignored) {
        }

        try {
            this.setCurrentScreen(new ErrorScreen("Client error", loopError));
        } catch (Throwable ignored) {
        }

        this.waiting = true;
        this.levelLoaded = false;
        this.online = false;

        try {
            if (this.gamemode == null
                    || !(this.gamemode
                            instanceof net.classicremastered.minecraft.gamemode.CreativeGameMode)) {
                this.gamemode = new net.classicremastered.minecraft.gamemode.CreativeGameMode(this);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (this.soundPlayer != null) this.soundPlayer.running = false;
            if (this.soundPC != null) {
                this.soundPC.stopFarLandsSoundtrack();
                this.soundPC.stopMusic();
            }
        } catch (Throwable ignored) {
        }
    }

    boolean wouldCollideWithMob(int x, int y, int z) {
        final float eps = 0.001f; // allow face-touching
        AABB blockBox = new AABB(x + eps, y + eps, z + eps, x + 1 - eps, y + 1 - eps, z + 1 - eps);

        // Exclude the player by passing them as the "except" entity
        java.util.List list = this.level.findEntities(this.player, blockBox);
        if (list == null || list.isEmpty()) return false;

        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!(o instanceof Mob)) continue; // only care about mobs
            Entity e = (Entity) o;
            if (e.bb != null && e.bb.intersects(blockBox)) {
                return true;
            }
        }
        return false;
    }

    // --- SAFE PLACEMENT HELPERS ---

    /** True if placing a unit block at (x,y,z) would intersect the player's AABB. */
    boolean wouldCollideWithPlayer(int x, int y, int z) {
        if (this.player == null || this.player.bb == null) return false;
        final float eps = 0.001f; // allow face-touching
        AABB blockBox = new AABB(x + eps, y + eps, z + eps, x + 1 - eps, y + 1 - eps, z + 1 - eps);
        return this.player.bb.intersects(blockBox);
    }

    /** Returns true only if a block was actually placed into empty space without colliding. */
    private boolean placeBlockSafely(Level level, int x, int y, int z, int blockId) {
        if (x < 0 || y < 0 || z < 0 || x >= level.width || y >= level.height || z >= level.depth)
            return false;

        // Only place into air (0). If you support replaceables, expand this check.
        if (level.getTile(x, y, z) != 0) return false;

        if (wouldCollideWithPlayer(x, y, z)) return false;

        level.setTile(x, y, z, blockId);
        return true;
    }

    private void tick() {
        if (this.soundPlayer != null) {
            SoundPlayer var1 = this.soundPlayer;
            SoundManager var2 = this.sound;
        }

        HUDScreen var17 = this.hud;
        ++this.hud.ticks;

        int var16;
        for (var16 = 0; var16 < var17.chat.size(); ++var16) {
            ++((ChatLine) var17.chat.get(var16)).time;
        }

        if (isLocalWorldPaused()) {
            tickCurrentScreen();
            return;
        }

        GL11.glBindTexture(
                3553,
                this.textureManager.load(
                        net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE));
        TextureManager var19 = this.textureManager;

        for (var16 = 0; var16 < var19.animations.size(); ++var16) {
            TextureFX var3;
            (var3 = var19.animations.get(var16)).anaglyph = var19.settings.anaglyph;
            var3.animate();
            var19.textureBuffer.clear();
            var19.textureBuffer.put(var3.textureData);
            var19.textureBuffer.position(0).limit(var3.textureData.length);
            GL11.glTexSubImage2D(
                    3553,
                    0,
                    var3.textureId % 16 << 4,
                    var3.textureId / 16 << 4,
                    16,
                    16,
                    6408,
                    5121,
                    var19.textureBuffer);
        }

        int var4;
        int var8;
        int var40;
        int var46;
        int var45;
        ClientNetworkHandler.tick(this);

        if (this.currentScreen == null && this.player != null && this.player.health <= 0) {
            this.setCurrentScreen((GuiScreen) null);
        }

        if (this.currentScreen == null || this.currentScreen.grabsMouse) {
            int var25;
            while (Mouse.next()) {
                if ((var25 = Mouse.getEventDWheel()) != 0 && !this.gamemode.isSpectator()) {
                    this.player.inventory.swapPaint(var25);
                    BowItem.cancelIfNotSelected(this.player);
                }

                if (this.currentScreen == null) {
                    if (!this.hasMouse && Mouse.getEventButtonState()) {
                        this.grabMouse();
                    } else if (!this.gamemode.isSpectator()) {
                        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                            this.input.pressAttack();
                        }

                        if (Mouse.getEventButton() == 1) {
                            if (Mouse.getEventButtonState()) this.input.pressUse();
                            else this.input.releaseUse();
                        }
                        if (!this.hudHidden
                                && this.hud != null
                                && (this.currentScreen == null
                                        || this.currentScreen
                                                instanceof
                                                net.classicremastered.minecraft.chat
                                                        .ChatInputScreen)) {
                            if (Mouse.getEventButtonState()) {
                                int mouseX = Mouse.getEventX() * this.width / Display.getWidth();
                                int mouseY =
                                        this.height
                                                - Mouse.getEventY()
                                                        * this.height
                                                        / Display.getHeight()
                                                - 1;
                                this.hud.onMouseClick(mouseX, mouseY, Mouse.getEventButton());
                            }
                        }
                        if (Mouse.getEventButton() == 2
                                && Mouse.getEventButtonState()
                                && this.selected != null) {
                            if ((var16 =
                                            this.level.getTile(
                                                    this.selected.x,
                                                    this.selected.y,
                                                    this.selected.z))
                                    == Block.GRASS.id) {
                                var16 = Block.DIRT.id;
                            }

                            if (var16 == Block.DOUBLE_SLAB.id) {
                                var16 = Block.SLAB.id;
                            }

                            if (var16 == Block.BEDROCK.id) {
                                var16 = Block.STONE.id;
                            }

                            this.player.inventory.grabTexture(
                                    var16, this.gamemode instanceof CreativeGameMode);
                        }
                    }
                }

                if (this.currentScreen != null) {
                    this.currentScreen.mouseEvent();
                }
            }

            if (this.blockHitTime > 0) {
                --this.blockHitTime;
            }

            while (Keyboard.next()) {
                this.input.handleKeyboardEvent();
            }

            boolean var26 =
                    this.currentScreen == null
                            && !this.gamemode.isSpectator()
                            && Mouse.isButtonDown(0)
                            && this.hasMouse
                            && this.player.inventory.getSelected() != 266
                            && this.player.inventory.getSelected() != GravityGunItem.RAW_ID;
            boolean var35 = false;
            if (!this.gamemode.instantBreak && this.blockHitTime <= 0) {
                if (var26 && this.selected != null && this.selected.entityPos == 0) {
                    var4 = this.selected.x;
                    var40 = this.selected.y;
                    var46 = this.selected.z;
                    this.gamemode.hitBlock(var4, var40, var46, this.selected.face);
                } else {
                    this.gamemode.resetHits();
                }
            }
        }

        boolean gameplayMouse = this.currentScreen == null && this.hasMouse;
        this.input.tickAttack(gameplayMouse && Mouse.isButtonDown(0));
        this.input.tickUse(gameplayMouse && Mouse.isButtonDown(1));

        tickCurrentScreen();
        if (isLocalWorldPaused()) return;

        this.gamemode.spawnMob();
        if (this.level != null) {
            Renderer var29 = this.renderer;
            ++this.renderer.levelTicks;
            HeldBlock var41 = var29.heldBlock;

            if (var41.moving) {
                ++var41.offset;
                if (var41.offset == 7) {
                    var41.offset = 0;
                    var41.moving = false;
                }
            }

            Player var27 = var41.minecraft.player;
            var4 = var41.minecraft.player.inventory.getSelected();
            Block var43 = null;

            var41.tickEquip(var4);

            if (var29.minecraft.raining) {
                Renderer var39 = var29;
                var27 = var29.minecraft.player;
                Level var32 = var29.minecraft.level;
                var40 = (int) var27.x;
                var46 = (int) var27.y;
                var45 = (int) var27.z;
                int drops = isDebugger() ? 10 : 50;
                for (var8 = 0; var8 < drops; ++var8) {
                    int var60 = var40 + var39.random.nextInt(9) - 4;
                    int var52 = var45 + var39.random.nextInt(9) - 4;
                    int var57;
                    if ((var57 = var32.getHighestTile(var60, var52)) <= var46 + 4
                            && var57 >= var46 - 4) {
                        float var56 = var39.random.nextFloat();
                        float var62 = var39.random.nextFloat();
                        var39.minecraft.particleManager.spawnParticle(
                                new WaterDropParticle(
                                        var32,
                                        (float) var60 + var56,
                                        (float) var57 + 0.1F,
                                        (float) var52 + var62));
                    }
                }
            }

            LevelRenderer var31 = this.levelRenderer;
            ++this.levelRenderer.ticks;
            if (skyRenderer != null) skyRenderer.tick();
            net.classicremastered.minecraft.chat.commands.SpawnMobCommand.tick(this);
            this.level.tickEntities();
            net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.processArmedImpacts(
                    this.level);
            net.classicremastered.minecraft.level.itemstack.GravityGunItem.cleanup(
                    this.level); // added
            if (this.player != null && (this.player.inventory.getSelected() - 256 != 11)) {
                if (net.classicremastered.minecraft.level.itemstack.GravityGunItem.grabbedMobs
                        .containsKey(this.player)) {
                    net.classicremastered.minecraft.level.itemstack.GravityGunItem.dropGently(
                            this.player);
                }
            }
            tickLevel();

            this.particleManager.tick();
        }
    }

    private void tickCurrentScreen() {
        if (currentScreen == null) return;
        currentScreen.doInput();
        if (currentScreen != null) currentScreen.tick();
    }

    /** Advance the world clock exactly once per game tick. */
    private void tickLevel() {
        if (this.level == null) return;
        if (!this.isOnline()) this.level.tick();
        else this.level.tickDayNightCycle();
        if (this.soundPC != null && this.player != null) {
            boolean atWorldEdge =
                    this.level instanceof net.classicremastered.minecraft.level.LevelInfiniteTerrain
                            && Math.max(Math.abs(this.player.x), Math.abs(this.player.z))
                                    >= 30_000_000;
            this.soundPC.tickFarLandsSoundtrack(atWorldEdge);
        }
    }

    public final boolean isOnline() {
        return this.networkManager != null;
    }

    public final void generateLevel(int var1) {
        String var2 = this.session != null ? this.session.username : "anonymous";
        Level var4 =
                (new LevelGenerator(this.progressBar)).generate(var2, 128 << var1, 128 << var1, 64);
        this.gamemode.prepareLevel(var4);
        this.setLevel(var4);
    }

    public final boolean loadOnlineLevel(String var1, int var2) {
        Level var3;
        if ((var3 = this.levelIo.loadOnline(this.host, var1, var2)) == null) {
            return false;
        } else {
            this.setLevel(var3);
            return true;
        }
    }

    static boolean isDebugger() {
        try {
            for (String a :
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (a.startsWith("-agentlib:jdwp")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isSupportedAppletBase(java.net.URL base) {
        if (base == null) return false;
        String host = base.getHost();
        return host.equalsIgnoreCase("minecraft.net")
                || host.equalsIgnoreCase("www.minecraft.net")
                || host.equalsIgnoreCase("classicremastered.net")
                || host.equalsIgnoreCase("www.classicremastered.net");
    }

    public void changeGameMode(GameMode next) {
        if (next.isSpectator() && isOnline()) {
            hud.addChat("&cSpectator mode is available in local worlds.");
            return;
        }
        String name = next.isSpectator() ? "Spectator"
                : next.isSurvival()      ? "Survival"
                                         : "Creative";
        if (gamemode.getClass() == next.getClass()) {
            hud.addChat("&eAlready in " + name + ".");
            return;
        }
        if (next.isSpectator())
            previousGameMode = gamemode;
        BowItem.cancelDraw(player);
        selected = null;
        gamemode = next;
        if (level != null)
            next.apply(level);
        if (player != null)
            next.apply(player);
        hud.addChat("&aGame mode set to &f" + name + "&a.");
    }

    void toggleSpectatorMode() {
        if (gamemode.isSpectator()) {
            changeGameMode(
                    previousGameMode != null ? previousGameMode : new SurvivalGameMode(this));
        } else {
            changeGameMode(new SpectatorGameMode(this));
        }
    }

    public final void setLevel(Level var1) {
        net.classicremastered.minecraft.chat.commands.SpawnMobCommand.worldChanged(this);
        previousGameMode = null;
        debugBlockTarget = null;
        BowItem.cancelDraw(this.player);
        if (this.soundPC != null) this.soundPC.stopFarLandsSoundtrack();
        if (isDebugger()) System.out.println("[setLevel] JDWP detected (debugger).");

        // Both desktop launchers use an applet with the project website as its base.
        if (this.applet != null
                && (!isSupportedAppletBase(this.applet.getDocumentBase())
                        || !isSupportedAppletBase(this.applet.getCodeBase()))) {
            var1 = null;
        }

        this.level = var1;

        if (var1 != null) {
            try {
                var1.initTransient();
            } catch (Throwable ignored) {
            }

            if (!this.isOnline())
                this.gamemode = GameMode.forLevel(this, var1);
            else if (this.gamemode.isSpectator())
                this.gamemode = new SurvivalGameMode(this);
            this.gamemode.apply(var1);
            var1.font = this.fontRenderer;
            var1.minecraft = this;

            if (!this.isOnline()) {
                this.player = (Player) var1.findSubclassOf(Player.class);
                if (this.player != null) {
                    var1.player = this.player;
                }
            } else if (this.player != null) {
                this.player.resetPos();
                this.gamemode.preparePlayer(this.player);
                var1.player = this.player;
                var1.addEntity(this.player);
            }
        }

        // --- Particle engine setup ---
        if (var1 != null) {
            if (this.particleManager == null) {
                this.particleManager = new ParticleManager(var1, this.textureManager);
            } else {
                var1.particleEngine = this.particleManager;
                try {
                    this.particleManager.setLevel(var1);
                } catch (Throwable ignored) {
                }
            }
        }

        // --- Player object creation if missing ---
        if (this.player == null) {
            this.player = new Player(var1);
            this.player.resetPos();
            this.gamemode.preparePlayer(this.player);
            if (var1 != null) var1.player = this.player;
        } else {
            if (var1 != null) {
                var1.player = this.player;
            }
        }
        this.player.minecraft = this;
        this.player.input = new InputHandlerImpl(this.settings);
        this.gamemode.apply(this.player);

        // --- LevelRenderer hard reset ---
        if (this.levelRenderer != null) {
            LevelRenderer lr = this.levelRenderer;

            if (lr.level != null) lr.level.removeListener(lr);

            try {
                if (lr.listId != 0) org.lwjgl.opengl.GL11.glDeleteLists(lr.listId, 2);
            } catch (Throwable ignored) {
            }

            try {
                lr.listId = org.lwjgl.opengl.GL11.glGenLists(2);
            } catch (Throwable ignored) {
                lr.listId = 0;
            }

            try {
                lr.buffer.clear();
            } catch (Throwable ignored) {
            }
            try {
                lr.chunks.clear();
            } catch (Throwable ignored) {
            }

            try {
                lr.getClass().getField("waterReady").setBoolean(lr, false);
            } catch (Throwable ignored) {
            }

            lr.level = var1;
            if (var1 != null) {
                var1.addListener(lr);
                try {
                    lr.refresh();
                    if (var1 != null) {
                        this.skyRenderer = new SkyRenderer(var1, this.textureManager);
                    } else {
                        this.skyRenderer = null;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // --- Clear particles and rebind ---
        if (this.particleManager != null) {
            if (var1 != null) var1.particleEngine = this.particleManager;
            for (int i = 0; i < 2; ++i) this.particleManager.particles[i].clear();
        }

        // === Restore serialized mobs after renderer is ready ===
        if (var1 != null && var1.pendingEntities != null && !var1.pendingEntities.isEmpty()) {
            try {
                net.classicremastered.minecraft.mob.MobRegistry.bootstrapDefaults();
                for (Object o : var1.pendingEntities) {
                    if (!(o instanceof net.classicremastered.minecraft.level.Level.SavedMob))
                        continue;
                    net.classicremastered.minecraft.level.Level.SavedMob s =
                            (net.classicremastered.minecraft.level.Level.SavedMob) o;

                    net.classicremastered.minecraft.mob.Mob mob =
                            net.classicremastered.minecraft.mob.MobRegistry.create(
                                    s.id, var1, s.x, s.y, s.z);
                    if (mob == null) continue;

                    mob.yRot = s.yRot;
                    mob.xRot = s.xRot;
                    mob.health = s.health;
                    var1.addEntity(mob);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            var1.pendingEntities = null;
        }

        System.gc();
    }

    public boolean saveCurrentLevel() {
        if (level == null || isOnline() || level.isNetworkMode() || level.saveFile == null) {
            return true;
        }
        returnInventoryBeforeSave();
        return levelIo.save(level, level.saveFile);
    }

    private void returnInventoryBeforeSave() {
        if (currentScreen instanceof InventoryScreen || currentScreen instanceof GuiCrafting) {
            currentScreen.onClose();
        }
    }

    /** True if the target entity is within attack reach of the player. */
    boolean entityWithinReach(Entity e) {
        if (e == null || this.player == null) return false;

        // Distance from player position to entity position
        float dx = (float) (e.x - this.player.x);
        float dy = (float) (e.y - this.player.y);
        float dz = (float) (e.z - this.player.z);
        float dist2 = dx * dx + dy * dy + dz * dz;

        // Use the game mode’s reach; add a small fudge so bounding boxes still count
        float reach = this.gamemode != null ? this.gamemode.getReachDistance() : 4.0F;
        float fudge = 0.75F; // allow for entity half-width / selection rounding
        float max = reach + fudge;

        return dist2 <= (max * max);
    }

    private void resizeTo(int w, int h) {
        this.width = w;
        this.height = h;

        try {
            GL11.glViewport(0, 0, w, h);
        } catch (Throwable ignored) {
        }

        if (hud != null) {
            hud.width = w * 240 / h;
            hud.height = h * 240 / h;
        }
        if (currentScreen != null) {
            currentScreen.width = w * 240 / h;
            currentScreen.height = h * 240 / h;
            currentScreen.onOpen();
        }
    }

    public void resize() {
        int w, h;
        if (this.canvas != null && !Display.isFullscreen()) {
            w = this.canvas.getWidth();
            h = this.canvas.getHeight();
        } else {
            w = Display.getWidth();
            h = Display.getHeight();
        }
        if (w > 0 && h > 0) resizeTo(w, h);
    }
}
