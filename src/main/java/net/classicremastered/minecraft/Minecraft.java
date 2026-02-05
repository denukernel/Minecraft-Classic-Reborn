package net.classicremastered.minecraft;

import org.lwjgl.input.Keyboard;
import java.io.File;

import net.classicremastered.minecraft.chat.ChatInputScreen;
import net.classicremastered.minecraft.chat.CommandRegistry;
import net.classicremastered.minecraft.chat.commands.CommandManager;
import net.classicremastered.minecraft.chat.commands.GiveCommand;
import net.classicremastered.minecraft.chat.commands.HelpCommand;
import net.classicremastered.minecraft.chat.commands.TimeCommand;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.gamemode.GameMode;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.gui.*;
import net.classicremastered.minecraft.lang.LanguageManager;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelIO;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.generator.LevelGenerator;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.FakeHerobrine;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.ModelManager;
import net.classicremastered.minecraft.model.ModelPart;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.net.NetworkManager;
import net.classicremastered.minecraft.net.NetworkPlayer;
import net.classicremastered.minecraft.net.PacketType;
import net.classicremastered.minecraft.particle.Particle;
import net.classicremastered.minecraft.particle.ParticleManager;
import net.classicremastered.minecraft.particle.WaterDropParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.InputHandlerImpl;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.*;
import net.classicremastered.minecraft.render.Renderer;
import net.classicremastered.minecraft.render.texture.TextureFX;
import net.classicremastered.minecraft.render.texture.TextureFireFX;
import net.classicremastered.minecraft.render.texture.TextureLavaFX;
import net.classicremastered.minecraft.render.texture.TextureWaterFX;
import net.classicremastered.minecraft.sound.PaulsCodeSoundManager;
import net.classicremastered.minecraft.sound.SoundManager;
import net.classicremastered.minecraft.sound.SoundPlayer;
import net.classicremastered.minecraft.util.Screenshot;
import net.classicremastered.net.NetworkHandler;
import net.classicremastered.util.MathHelper;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Controllers;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.List;

public final class Minecraft implements Runnable {

    public GameMode gamemode = new SurvivalGameMode(this);
    private boolean fullscreen = false;
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
    private SkyRenderer skyRenderer;

    public Canvas canvas;
    public boolean levelLoaded = false;
    public volatile boolean waiting = false;
    private Cursor cursor;
    public TextureManager textureManager;
    public FontRenderer fontRenderer;
    public GuiScreen currentScreen = null;
    public ProgressBarDisplay progressBar = new ProgressBarDisplay(this);
    public Renderer renderer = new Renderer(this);
    public LevelIO levelIo;
    public SoundManager sound;
    private ResourceDownloadThread resourceThread;
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
    String server;
    int port;
    public volatile boolean running;
    public String debug;
    public boolean hasMouse;
    private int lastClick;
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
        this.lastClick = 0;
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
        if (this.currentScreen instanceof ErrorScreen)
            return;

        if (this.currentScreen != null)
            this.currentScreen.onClose();
        if (next instanceof MainMenuScreen && this.soundPC != null) {
            this.soundPC.resetAudioState();
        }

        if (next == null && this.player.health <= 0)
            next = new GameOverScreen();

        this.currentScreen = next;
        if (next != null) {
            if (this.hasMouse) {
                this.player.releaseAllKeys();
                this.hasMouse = false;
                try {
                    if (this.levelLoaded)
                        Mouse.setNativeCursor(null);
                    else
                        Mouse.setGrabbed(false);
                } catch (LWJGLException e) {
                    e.printStackTrace();
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

    private static void checkGLError(String var0) {
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
            if (this.level != null) {
                File dir = new File(mcDir, "levels");
                if (!dir.exists())
                    dir.mkdirs();
                File autos = new File(dir, "slot1.lvl.gz");
                this.levelIo.save(this.level, autos);
            }
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
            if (org.lwjgl.input.Mouse.isCreated())
                org.lwjgl.input.Mouse.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Keyboard.isCreated())
                org.lwjgl.input.Keyboard.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Controllers.isCreated())
                org.lwjgl.input.Controllers.destroy();
        } catch (Throwable ignored) {
        }

        // 7) Tear down display safely
        try {
            if (org.lwjgl.opengl.Display.isCreated())
                org.lwjgl.opengl.Display.destroy();
        } catch (Throwable ignored) {
        }

        // 8) Dispose host window if embedded (prevents AWT thread from keeping JVM
        // alive)
        try {
            if (this.canvas != null) {
                java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this.canvas);
                if (w != null)
                    w.dispose();
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
            if (soundPlayer != null)
                soundPlayer.running = false;
        } catch (Throwable ignored) {
        }
        try {
            if (resourceThread != null)
                resourceThread.running = false;
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
    private boolean wasRightDown = false;
    public boolean developer = false;
    public InputManager input;

    public final void run() {
        this.running = true;

        String folder = "mcraft/client";
        String home = System.getProperty("user.home", ".");
        String osName = System.getProperty("os.name").toLowerCase();
        File minecraftFolder;
        switch (OperatingSystemLookup.lookup[(osName
                .contains("win")
                        ? Minecraft$OS.windows
                        : (osName
                                .contains("mac")
                                        ? Minecraft$OS.macos
                                        : (osName
                                                .contains("solaris")
                                                        ? Minecraft$OS.solaris
                                                        : (osName.contains("sunos") ? Minecraft$OS.solaris
                                                                : (osName.contains("linux") ? Minecraft$OS.linux
                                                                        : (osName.contains("unix") ? Minecraft$OS.linux
                                                                                : Minecraft$OS.unknown))))))
                .ordinal()]) {
            case 1:
                System.out.println("UNKNOWN OS!!!");
                return;
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
            throw new RuntimeException("The working directory could not be created: " + minecraftFolder);
        }

        mcDir = minecraftFolder;
        this.soundPlayer = new SoundPlayer(this.settings);
        this.soundPlayer.startAudio(); // opens 44.1k/16/Stereo big-endian with a large buffer
        // optional sanity tone:
        this.soundPlayer.debugBeep();
        try {
            Minecraft mc = this;

            if (!minecraftFolder.exists()) {
                JOptionPane.showMessageDialog(null,
                        "Welcome to the MCraft Client Alpha 1!"
                                + "\nPleave give MCraft some time to download required files."
                                + "\nTHIS CAN TAKE A LONG TIME DEPENDING ON YOUR INTERNET SPEED.",
                        "Welcome to MCraft Client", JOptionPane.INFORMATION_MESSAGE);
            }

            // ===== RESOURCES: start downloader and BLOCK until finished =====

            this.resourceThread = new ResourceDownloadThread(mcDir, this);
            this.resourceThread.start();

            System.out.println("[Resources] Downloading sounds & music (first run only)...");
            long lastResourceLogMs = 0L;

            // Null-safe wait loop
            while (this.resourceThread != null && !this.resourceThread.isFinished()) {
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
            this.input = new InputManager(this);

            // Window / display setup AFTER resources are ready
            if (this.canvas != null) {
                while (!this.canvas.isDisplayable()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
                Display.setParent(this.canvas);
            } else if (this.fullscreen) {
                Display.setFullscreen(true);
                this.width = Display.getDisplayMode().getWidth();
                this.height = Display.getDisplayMode().getHeight();
            } else {
                Display.setDisplayMode(new DisplayMode(this.width, this.height));
            }

            Display.setTitle("Minecraft 0.30");

            try {
                Display.create();

                // Set initial size from the actual surface
                if (this.canvas != null && !Display.isFullscreen()) {
                    int cw = this.canvas.getWidth();
                    int ch = this.canvas.getHeight();
                    if (cw > 0 && ch > 0) {
                        this.width = cw;
                        this.height = ch;
                    }
                } else {
                    this.width = Display.getWidth();
                    this.height = Display.getHeight();
                }

                this.progressBar.setTitle("Starting…");
                this.progressBar.setText("");

                // GL viewport for the chosen size
                GL11.glViewport(0, 0, this.width, this.height);
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

            checkGLError("Pre startup");
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
            checkGLError("Startup");

            this.settings = new GameSettings(this, minecraftFolder);
            // after this.settings = new GameSettings(this, minecraftFolder);
            this.lang = new LanguageManager(minecraftFolder);
            this.lang.load("english"); // load default
            // keep any prior value (field default, launcher flag, etc.)
            this.developer = this.developer || isDebugger() || Boolean.getBoolean("mc.dev")
                    || "1".equals(System.getenv("MC_DEV"));
            // System.out.println("[dev] developer=" + this.developer);

            fastDebugMode = isDebugger();
            if (fastDebugMode) {
                this.settings.limitFramerate = false;
                try {
                    org.lwjgl.opengl.Display.setVSyncEnabled(false);
                } catch (Throwable ignored) {
                }
            }
            this.soundPC = new PaulsCodeSoundManager(this.settings);
            this.textureManager = new TextureManager(this.settings);

            if (resourceThread == null || !resourceThread.isAlive()) {
                try {
                    if (resourceThread != null && resourceThread.isAlive()) {
                        resourceThread.running = false;
                        resourceThread.join(5000);
                    }
                } catch (InterruptedException ignored) {
                }

                resourceThread = new ResourceDownloadThread(mcDir, this);
                resourceThread.start();
            }

            this.textureManager.load("/terrain.png"); // ensure atlas exists
            this.textureManager.applyBlockTilesToTerrainAtlas(); // stitch per-block tiles

            this.textureManager.registerAnimation(new TextureLavaFX());
            this.textureManager.registerAnimation(new TextureWaterFX());
            this.textureManager.registerAnimation(new TextureFireFX(Block.FIRE.textureId));
            this.fontRenderer = new FontRenderer(this.settings, "/default.png", this.textureManager);
            IntBuffer cursorBuffer;
            (cursorBuffer = BufferUtils.createIntBuffer(256)).clear().limit(256);
            this.levelRenderer = new LevelRenderer(this, this.textureManager);
            DroppedBlock.initModels();
            net.classicremastered.minecraft.level.itemstack.Item.initItems(); // moved here for startup-only
                                                                              // registration

            MobRegistry.bootstrapDefaults();
            Mob.modelCache = new ModelManager();
            net.classicremastered.minecraft.render.entity.EntityRenderBootstrap.init(Mob.modelCache);
            GL11.glViewport(0, 0, this.width, this.height);

            if (this.server != null && this.session != null) {
                Level tinyNetLevel;
                (tinyNetLevel = new Level()).setData(8, 8, 8, new byte[512]);
                this.setLevel(tinyNetLevel);
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
                if (this.level == null) {
                    this.setCurrentScreen(new MainMenuScreen());
                }
            }

            if (this.level != null) {
                this.skyRenderer = new SkyRenderer(this.level, this.textureManager);
                this.particleManager = new ParticleManager(this.level, this.textureManager);
            }
            if (this.levelLoaded) {
                try {
                    mc.cursor = new Cursor(16, 16, 0, 0, 1, cursorBuffer, (IntBuffer) null);
                } catch (LWJGLException cursorFail) {
                    cursorFail.printStackTrace();
                }
            }

            try {

                // mc.resourceThread = new ResourceDownloadThread(mcDir, mc);
                // mc.resourceThread.start();
            } catch (Exception ignoreSoundInit) {
                // ignore
            }

            checkGLError("Post startup");
            this.hud = new HUDScreen(this, this.width, this.height);
            if (this.developer && this.hud != null) {
                this.hud.addChat("&7Developer mode enabled");
            }
            (new SkinDownloadThread(this)).start();
            CommandRegistry.bootstrap(this);
            if (this.server != null && this.session != null) {
                this.networkManager = new NetworkManager(this, this.server, this.port, this.session.username,
                        this.session.mppass);
            }
        } catch (Exception bootError) {
            bootError.printStackTrace();
            JOptionPane.showMessageDialog((Component) null, bootError.toString(), "Failed to start Minecraft", 0);
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
                        if (quitRequested)
                            break;
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
                            frameDelta = ((hrNowS = (double) hrNowMs / 1000.0D) - t.lastHR) * t.adjustment;
                            t.lastHR = hrNowS;
                            if (frameDelta < 0.0D)
                                frameDelta = 0.0D;
                            if (frameDelta > 1.0D)
                                frameDelta = 1.0D;

                            t.elapsedDelta = (float) ((double) t.elapsedDelta
                                    + frameDelta * (double) t.speed * (double) t.tps);
                            t.elapsedTicks = (int) t.elapsedDelta;
                            if (t.elapsedTicks > 100)
                                t.elapsedTicks = 100;

                            t.elapsedDelta -= (float) t.elapsedTicks;
                            t.delta = t.elapsedDelta;

                            for (int i = 0; i < this.timer.elapsedTicks; ++i) {
                                ++this.ticks;
                                this.tick();
                            }
                            if (this.levelRenderer != null) {
                                this.levelRenderer.updateSomeChunks();
                            }

                            // Let mods tick once per frame (after LWJGL is ready)
                            com.mcraft.api.hooks.Hooks.onClientTick(this);
                            if (this.soundPC != null && this.player != null) {
                                this.soundPC.setListener(this.player, this.timer.delta);

                                // Update distance for Far Lands checks
                                long dist = (long) Math.max(Math.abs(this.player.x), Math.abs(this.player.z));
                                this.soundPC.playerDist = dist;
                                this.soundPC.farlandsActive = (dist >= 1_000_000);

                                // Tick audio corruption
                                this.soundPC.tickFarlandsAudio();
                                String msg = this.soundPC.maybeGetHerobrineMessage();
                                if (msg != null && this.hud != null) {
                                    this.hud.addChat(msg);
                                }
                            }
                            if (this.soundPC.shouldSpawnHerobrineFigure() && this.level != null
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
                            checkGLError("Pre render");
                            GL11.glEnable(3553);
                            if (!this.online) {
                                this.gamemode.applyCracks(this.timer.delta);
                                float partial = this.timer.delta;
                                if (this.level != null) {
                                    this.level.setRenderPartial(this.timer.delta);
                                    this.level.updateDayNightColorsSmooth();
                                }
                                Renderer r = this.renderer;
                                if (this.renderer.displayActive && !Display.isActive()) {
                                    r.minecraft.pause();
                                }

                                r.displayActive = Display.isActive();
                                int uiW;
                                int uiH;
                                int mouseDY;
                                int mouseDX;
                                if (r.minecraft.hasMouse) {
                                    mouseDX = 0;
                                    mouseDY = 0;
                                    if (r.minecraft.levelLoaded) {
                                        if (r.minecraft.canvas != null) {
                                            Point canvasLoc;
                                            uiH = (canvasLoc = r.minecraft.canvas.getLocationOnScreen()).x
                                                    + r.minecraft.width / 2;
                                            uiW = canvasLoc.y + r.minecraft.height / 2;
                                            Point pointer;
                                            mouseDX = (pointer = MouseInfo.getPointerInfo().getLocation()).x - uiH;
                                            mouseDY = -(pointer.y - uiW);
                                            r.minecraft.robot.mouseMove(uiH, uiW);
                                        } else {
                                            Mouse.setCursorPosition(r.minecraft.width / 2, r.minecraft.height / 2);
                                        }
                                    } else {
                                        mouseDX = Mouse.getDX();
                                        mouseDY = Mouse.getDY();
                                    }

                                    byte invert = 1;
                                    if (r.minecraft.settings.invertMouse)
                                        invert = -1;
                                    r.minecraft.player.turn((float) mouseDX, (float) (mouseDY * invert));
                                }

                                if (!r.minecraft.online) {
                                    mouseDX = r.minecraft.width * 240 / r.minecraft.height;
                                    mouseDY = r.minecraft.height * 240 / r.minecraft.height;
                                    int mouseUiX = Mouse.getX() * mouseDX / r.minecraft.width;
                                    uiH = mouseDY - Mouse.getY() * mouseDY / r.minecraft.height - 1;
                                    if (r.minecraft.level != null) {
                                        float renderPartial = partial;
                                        Renderer r2 = r;
                                        Renderer rPick = r;
                                        Player player = r.minecraft.player;
                                        float xRotInterp = player.xRotO + (player.xRot - player.xRotO) * partial;
                                        float yRotInterp = player.yRotO + (player.yRot - player.yRotO) * partial;
                                        Vec3D rayStart = r.getPlayerVector(partial);
                                        float cosYaw = MathHelper.cos(-yRotInterp * 0.017453292F - 3.1415927F);
                                        float sinYaw = MathHelper.sin(-yRotInterp * 0.017453292F - 3.1415927F);
                                        float cosPitch = MathHelper.cos(-xRotInterp * 0.017453292F);
                                        float sinPitch = MathHelper.sin(-xRotInterp * 0.017453292F);
                                        float lookX = sinYaw * cosPitch;
                                        float lookZ = cosYaw * cosPitch;
                                        float reach = r.minecraft.gamemode.getReachDistance();
                                        Vec3D rayEnd = rayStart.add(lookX * reach, sinPitch * reach, lookZ * reach);
                                        r.minecraft.selected = r.minecraft.level.clip(rayStart, rayEnd);
                                        cosPitch = reach;
                                        if (r.minecraft.selected != null) {
                                            cosPitch = r.minecraft.selected.vec.distance(r.getPlayerVector(partial));
                                        }

                                        rayStart = r.getPlayerVector(partial);
                                        if (r.minecraft.gamemode instanceof CreativeGameMode)
                                            reach = 32.0F;
                                        else
                                            reach = cosPitch;
                                        rayEnd = rayStart.add(lookX * reach, sinPitch * reach, lookZ * reach);
                                        r.entity = null;
                                        List entitiesInPath = r.minecraft.level.blockMap.getEntities(player,
                                                player.bb.expand(lookX * reach, sinPitch * reach, lookZ * reach));
                                        float closestHitDist = 0.0F;

                                        for (mouseDX = 0; mouseDX < entitiesInPath.size(); ++mouseDX) {
                                            Entity e;
                                            if ((e = (Entity) entitiesInPath.get(mouseDX)).isPickable()) {
                                                cosPitch = 0.1F;
                                                MovingObjectPosition hit;
                                                if ((hit = e.bb.grow(cosPitch, cosPitch, cosPitch).clip(rayStart,
                                                        rayEnd)) != null
                                                        && ((cosPitch = rayStart.distance(hit.vec)) < closestHitDist
                                                                || closestHitDist == 0.0F)) {
                                                    rPick.entity = e;
                                                    closestHitDist = cosPitch;
                                                }
                                            }
                                        }

                                        if (rPick.entity != null) {
                                            rPick.minecraft.selected = new MovingObjectPosition(rPick.entity);
                                        }

                                        int stereoEye = 0;
                                        while (true) {
                                            if (stereoEye >= 2) {
                                                GL11.glColorMask(true, true, true, false);
                                                break;
                                            }
                                            if (r2.minecraft.settings.anaglyph) {
                                                if (stereoEye == 0)
                                                    GL11.glColorMask(false, true, true, false);
                                                else
                                                    GL11.glColorMask(true, false, false, false);
                                            }

                                            Player camPlayer = r2.minecraft.player;
                                            Level camLevel = r2.minecraft.level;
                                            LevelRenderer lr = r2.minecraft.levelRenderer;
                                            ParticleManager pm = r2.minecraft.particleManager;
                                            GL11.glViewport(0, 0, r2.minecraft.width, r2.minecraft.height);
                                            Level levelRef = r2.minecraft.level;
                                            player = r2.minecraft.player;
                                            float invViewDist = 1.0F / (float) (4 - r2.minecraft.settings.viewDistance);
                                            invViewDist = 1.0F - (float) Math.pow((double) invViewDist, 0.25D);
                                            float skyR = (float) (levelRef.skyColor >> 16 & 255) / 255.0F;
                                            float skyG = (float) (levelRef.skyColor >> 8 & 255) / 255.0F;
                                            float skyB = (float) (levelRef.skyColor & 255) / 255.0F;
                                            r2.fogRed = (float) (levelRef.fogColor >> 16 & 255) / 255.0F;
                                            r2.fogBlue = (float) (levelRef.fogColor >> 8 & 255) / 255.0F;
                                            r2.fogGreen = (float) (levelRef.fogColor & 255) / 255.0F;
                                            r2.fogRed += (skyR - r2.fogRed) * invViewDist;
                                            r2.fogBlue += (skyG - r2.fogBlue) * invViewDist;
                                            r2.fogGreen += (skyB - r2.fogGreen) * invViewDist;
                                            r2.fogRed *= r2.fogColorMultiplier;
                                            r2.fogBlue *= r2.fogColorMultiplier;
                                            r2.fogGreen *= r2.fogColorMultiplier;

                                            Block feetBlock;
                                            if ((feetBlock = Block.blocks[levelRef.getTile((int) player.x,
                                                    (int) (player.y + 0.12F), (int) player.z)]) != null
                                                    && feetBlock.getLiquidType() != LiquidType.NOT_LIQUID) {
                                                LiquidType lt;
                                                if ((lt = feetBlock.getLiquidType()) == LiquidType.WATER) {
                                                    r2.fogRed = 0.02F;
                                                    r2.fogBlue = 0.02F;
                                                    r2.fogGreen = 0.2F;
                                                } else if (lt == LiquidType.LAVA) {
                                                    r2.fogRed = 0.6F;
                                                    r2.fogBlue = 0.1F;
                                                    r2.fogGreen = 0.0F;
                                                }
                                            }

                                            if (r2.minecraft.settings.anaglyph) {
                                                float rGray = (r2.fogRed * 30.0F + r2.fogBlue * 59.0F
                                                        + r2.fogGreen * 11.0F) / 100.0F;
                                                float gGray = (r2.fogRed * 30.0F + r2.fogBlue * 70.0F) / 100.0F;
                                                float bGray = (r2.fogRed * 30.0F + r2.fogGreen * 70.0F) / 100.0F;
                                                r2.fogRed = rGray;
                                                r2.fogBlue = gGray;
                                                r2.fogGreen = bGray;
                                            }

                                            GL11.glClearColor(r2.fogRed, r2.fogBlue, r2.fogGreen, 0.0F);
                                            GL11.glClear(16640);
                                            r2.fogColorMultiplier = 1.0F;
                                            GL11.glEnable(2884);
                                            r2.fogEnd = (float) (512 >> (r2.minecraft.settings.viewDistance << 1));
                                            GL11.glMatrixMode(5889);
                                            GL11.glLoadIdentity();
                                            float eyeShift = 0.07F;
                                            if (r2.minecraft.settings.anaglyph) {
                                                GL11.glTranslatef((float) (-((stereoEye << 1) - 1)) * eyeShift, 0.0F,
                                                        0.0F);
                                            }

                                            camPlayer = r2.minecraft.player;
                                            float fov = 70.0F;
                                            if (camPlayer.health <= 0) {
                                                float deathTimeInterp = (float) camPlayer.deathTime + renderPartial;
                                                fov /= (1.0F - 500.0F / (deathTimeInterp + 500.0F)) * 2.0F + 1.0F;
                                            }

                                            GLU.gluPerspective(fov,
                                                    (float) r2.minecraft.width / (float) r2.minecraft.height, 0.05F,
                                                    r2.fogEnd);
                                            GL11.glMatrixMode(5888);
                                            GL11.glLoadIdentity();
                                            if (r2.minecraft.settings.anaglyph) {
                                                GL11.glTranslatef((float) ((stereoEye << 1) - 1) * 0.1F, 0.0F, 0.0F);
                                            }

                                            r2.hurtEffect(renderPartial);
                                            if (r2.minecraft.settings.viewBobbing) {
                                                r2.applyBobbing(renderPartial);
                                            }

                                            camPlayer = r2.minecraft.player;
                                            GL11.glTranslatef(0.0F, 0.0F, -0.1F);
                                            // Replace vanilla camera placement with mode-aware setup
                                            this.renderer.setupCamera(this.timer.delta);

                                            Frustrum fr = FrustrumImpl.update();
                                            Frustrum frRef = fr;
                                            LevelRenderer levelRendererRef = r2.minecraft.levelRenderer;

                                            // Handle finite vs infinite worlds safely
                                            boolean isInfinite = (this.level instanceof LevelInfiniteFlat);

                                            // Finite worlds: clip visible chunks and rebuild a small budget each frame
                                            if (!isInfinite && levelRendererRef != null
                                                    && levelRendererRef.chunkCache != null) {
                                                for (int ic = 0; ic < levelRendererRef.chunkCache.length; ++ic) {
                                                    levelRendererRef.chunkCache[ic].clip(frRef);
                                                }

                                                // Rebuild a few dirty chunks (closest last in list)
                                                Collections.sort(levelRendererRef.chunks,
                                                        new ChunkDirtyDistanceComparator(camPlayer));
                                                int toRebuild = Math.min(3, levelRendererRef.chunks.size());
                                                for (int kk = 0; kk < toRebuild; ++kk) {
                                                    int idxLast = levelRendererRef.chunks.size() - 1;
                                                    if (idxLast < 0)
                                                        break;
                                                    Chunk ch = (Chunk) levelRendererRef.chunks.remove(idxLast);
                                                    ch.update();
                                                    ch.loaded = false;
                                                }
                                            }

                                            // Common path: this dispatches to your LevelRenderer logic.
                                            // For finite: uses chunkCache. For infinite: uses your SimpleChunkManager
                                            // path.
                                            lr.sortChunks(camPlayer, 0);

                                            r2.updateFog();
                                            GL11.glEnable(2912);
                                            int pass;
                                            int texId;
                                            ShapeRenderer shape;
                                            int f;
                                            int zTile;
                                            int yTile;
                                            int xTile;
                                            if (camLevel.isSolid(camPlayer.x, camPlayer.y, camPlayer.z, 0.1F)) {
                                                // Integer camera cell
                                                final int cx = (int) camPlayer.x;
                                                final int cy = (int) camPlayer.y;
                                                final int cz = (int) camPlayer.z;

                                                // Render the “inside block” dark overlay around the camera
                                                for (int nx = cx - 1; nx <= cx + 1; nx++) {
                                                    for (int ny = cy - 1; ny <= cy + 1; ny++) {
                                                        for (int nz = cz - 1; nz <= cz + 1; nz++) {
                                                            int id = lr.level.getTile(nx, ny, nz);
                                                            if (id <= 0)
                                                                continue;

                                                            Block b = Block.blocks[id];
                                                            if (b == null || !b.isSolid())
                                                                continue;

                                                            // Darken interior a bit
                                                            GL11.glColor4f(0.2F, 0.2F, 0.2F, 1.0F);

                                                            // First pass: render inside faces with depth EQUAL
                                                            GL11.glDepthFunc(513); // GL_EQUAL
                                                            ShapeRenderer sr = ShapeRenderer.instance;
                                                            sr.begin();
                                                            for (int face = 0; face < 6; face++) {
                                                                b.renderInside(sr, nx, ny, nz, face);
                                                            }
                                                            sr.end();

                                                            // Second pass: cull swap for proper interior look
                                                            GL11.glCullFace(1028); // GL_FRONT
                                                            sr.begin();
                                                            for (int face = 0; face < 6; face++) {
                                                                b.renderInside(sr, nx, ny, nz, face);
                                                            }
                                                            sr.end();
                                                            GL11.glCullFace(1029); // GL_BACK

                                                            // Restore default depth func
                                                            GL11.glDepthFunc(515); // GL_LEQUAL
                                                        }
                                                    }
                                                }
                                            }

                                            r2.setLighting(true);
                                            Vec3D camVec = r2.getPlayerVector(renderPartial);
                                            lr.level.blockMap.render(camVec, fr, lr.textureManager, renderPartial);
                                            r2.setLighting(false);
                                            r2.updateFog();
                                            float particlesPartial = renderPartial;
                                            ParticleManager pmRef = pm;
                                            float cosYaw2 = -MathHelper.cos(camPlayer.yRot * 3.1415927F / 180.0F);
                                            float sinYawNeg = -(skyR = -MathHelper
                                                    .sin(camPlayer.yRot * 3.1415927F / 180.0F))
                                                    * MathHelper.sin(camPlayer.xRot * 3.1415927F / 180.0F);
                                            float sinYaw2 = cosYaw2
                                                    * MathHelper.sin(camPlayer.xRot * 3.1415927F / 180.0F);
                                            float cosPitch2 = MathHelper.cos(camPlayer.xRot * 3.1415927F / 180.0F);

                                            for (pass = 0; pass < 2; ++pass) {
                                                if (pmRef.particles[pass].size() != 0) {
                                                    texId = 0;
                                                    if (pass == 0)
                                                        texId = pmRef.textureManager.load("/particles.png");
                                                    if (pass == 1)
                                                        texId = pmRef.textureManager.load("/terrain.png");
                                                    GL11.glBindTexture(3553, texId);
                                                    ShapeRenderer ps = ShapeRenderer.instance;
                                                    ShapeRenderer.instance.begin();
                                                    for (xTile = 0; xTile < pmRef.particles[pass].size(); ++xTile) {
                                                        ((Particle) pmRef.particles[pass].get(xTile)).render(ps,
                                                                particlesPartial, cosYaw2, cosPitch2, skyR, sinYawNeg,
                                                                sinYaw2);
                                                    }
                                                    ps.end();
                                                }
                                            }
                                            GL11.glDisable(3553);
                                            r2.updateFog();
                                            GL11.glDisable(3553);
                                            GL11.glDepthMask(false);
                                            GL11.glDepthMask(true);
                                            GL11.glEnable(3553);

                                            LevelRenderer lrRef = lr;
                                            // sky: stars + sun/moon + clouds (infinite, not culled by view distance)
                                            if (this.skyRenderer != null) {
                                                this.skyRenderer.renderStars(this.player, renderPartial);
                                                this.skyRenderer.renderSunMoon(this.player, renderPartial);

                                                float cloudR = (float) ((levelRef.cloudColor >> 16) & 255) / 255f;
                                                float cloudG = (float) ((levelRef.cloudColor >> 8) & 255) / 255f;
                                                float cloudB = (float) ((levelRef.cloudColor) & 255) / 255f;
                                                this.skyRenderer.renderClouds(renderPartial, cloudR, cloudG, cloudB);
                                            }

                                            GL11.glEnable(3553);
                                            r2.updateFog();

                                            if (r2.minecraft.selected != null) {
                                                GL11.glDisable(3008);
                                                MovingObjectPosition pick = r2.minecraft.selected;
                                                LevelRenderer lrPick = lr;
                                                ShapeRenderer sr1 = ShapeRenderer.instance;
                                                GL11.glEnable(3042);
                                                GL11.glEnable(3008);
                                                GL11.glBlendFunc(770, 1);
                                                GL11.glColor4f(1, 1, 1,
                                                        (MathHelper.sin((float) System.currentTimeMillis() / 100.0F)
                                                                * 0.2F + 0.4F) * 0.5F);
                                                if (lr.cracks > 0.0F) {
                                                    GL11.glBlendFunc(774, 768);
                                                    int terrainTex = lr.textureManager.load("/terrain.png");
                                                    GL11.glBindTexture(3553, terrainTex);
                                                    GL11.glColor4f(1, 1, 1, 0.5F);
                                                    GL11.glPushMatrix();
                                                    int pickId = lr.level.getTile(pick.x, pick.y, pick.z);
                                                    Block pickBlock = pickId > 0 ? Block.blocks[pickId] : null;
                                                    if (pickBlock == null)
                                                        pickBlock = Block.STONE;
                                                    float cx = (pickBlock.x1 + pickBlock.x2) / 2.0F;
                                                    float cy = (pickBlock.y1 + pickBlock.y2) / 2.0F;
                                                    float cz = (pickBlock.z1 + pickBlock.z2) / 2.0F;
                                                    GL11.glTranslatef(pick.x + cx, pick.y + cy, pick.z + cz);
                                                    GL11.glScalef(1.01F, 1.01F, 1.01F);
                                                    GL11.glTranslatef(-(pick.x + cx), -(pick.y + cy), -(pick.z + cz));
                                                    sr1.begin();
                                                    sr1.noColor();
                                                    GL11.glDepthMask(false);
                                                    for (int face = 0; face < 6; ++face)
                                                        pickBlock.renderSide(sr1, pick.x, pick.y, pick.z, face,
                                                                240 + (int) (lrPick.cracks * 10.0F));
                                                    sr1.end();
                                                    GL11.glDepthMask(true);
                                                    GL11.glPopMatrix();
                                                }
                                                GL11.glDisable(3042);
                                                GL11.glDisable(3008);

                                                GL11.glEnable(3042);
                                                GL11.glBlendFunc(770, 771);
                                                GL11.glColor4f(0, 0, 0, 0.4F);
                                                GL11.glLineWidth(2.0F);
                                                GL11.glDisable(3553);
                                                GL11.glDepthMask(false);
                                                float eps = 0.002F;
                                                int id = lr.level.getTile(pick.x, pick.y, pick.z);
                                                if (id > 0) {
                                                    AABB bb = Block.blocks[id].getSelectionBox(pick.x, pick.y, pick.z)
                                                            .grow(eps, eps, eps);
                                                    GL11.glBegin(3);
                                                    GL11.glVertex3f(bb.x0, bb.y0, bb.z0);
                                                    GL11.glVertex3f(bb.x1, bb.y0, bb.z0);
                                                    GL11.glVertex3f(bb.x1, bb.y0, bb.z1);
                                                    GL11.glVertex3f(bb.x0, bb.y0, bb.z1);
                                                    GL11.glVertex3f(bb.x0, bb.y0, bb.z0);
                                                    GL11.glEnd();
                                                    GL11.glBegin(3);
                                                    GL11.glVertex3f(bb.x0, bb.y1, bb.z0);
                                                    GL11.glVertex3f(bb.x1, bb.y1, bb.z0);
                                                    GL11.glVertex3f(bb.x1, bb.y1, bb.z1);
                                                    GL11.glVertex3f(bb.x0, bb.y1, bb.z1);
                                                    GL11.glVertex3f(bb.x0, bb.y1, bb.z0);
                                                    GL11.glEnd();
                                                    GL11.glBegin(1);
                                                    GL11.glVertex3f(bb.x0, bb.y0, bb.z0);
                                                    GL11.glVertex3f(bb.x0, bb.y1, bb.z0);
                                                    GL11.glVertex3f(bb.x1, bb.y0, bb.z0);
                                                    GL11.glVertex3f(bb.x1, bb.y1, bb.z0);
                                                    GL11.glVertex3f(bb.x1, bb.y0, bb.z1);
                                                    GL11.glVertex3f(bb.x1, bb.y1, bb.z1);
                                                    GL11.glVertex3f(bb.x0, bb.y0, bb.z1);
                                                    GL11.glVertex3f(bb.x0, bb.y1, bb.z1);
                                                    GL11.glEnd();
                                                }
                                                GL11.glDepthMask(true);
                                                GL11.glEnable(3553);
                                                GL11.glDisable(3042);
                                                GL11.glEnable(3008);
                                            }

                                            GL11.glBlendFunc(770, 771);
                                            r2.updateFog();
                                            GL11.glEnable(3553);
                                            GL11.glEnable(3042);
                                            if (lr.waterReady) {
                                                GL11.glBindTexture(3553, lr.textureManager.load("/water.png"));
                                                GL11.glCallList(lr.listId + 1);
                                            }
                                            GL11.glDisable(3042);
                                            GL11.glEnable(3042);
                                            GL11.glColorMask(false, false, false, false);
                                            xTile = lr.sortChunks(camPlayer, 1);
                                            GL11.glColorMask(true, true, true, true);
                                            if (r2.minecraft.settings.anaglyph) {
                                                if (stereoEye == 0)
                                                    GL11.glColorMask(false, true, true, false);
                                                else
                                                    GL11.glColorMask(true, false, false, false);
                                            }
                                            if (xTile > 0) {
                                                GL11.glBindTexture(3553, lr.textureManager.load("/terrain.png"));
                                                GL11.glCallLists(lr.buffer);
                                            }
                                            GL11.glDepthMask(true);
                                            GL11.glDisable(3042);
                                            GL11.glDisable(2912);

                                            if (r2.minecraft.raining) {
                                                float rainDelta = renderPartial;
                                                Renderer rr = r2;
                                                player = r2.minecraft.player;
                                                Level lvl = r2.minecraft.level;
                                                int px = (int) player.x;
                                                int py = (int) player.y;
                                                int pz = (int) player.z;
                                                ShapeRenderer sr1 = ShapeRenderer.instance;
                                                GL11.glDisable(2884);
                                                GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                                                GL11.glEnable(3042);
                                                GL11.glBlendFunc(770, 771);
                                                GL11.glBindTexture(3553, r2.minecraft.textureManager.load("/rain.png"));

                                                for (int rx = px - 5; rx <= px + 5; ++rx) {
                                                    for (int rz = pz - 5; rz <= pz + 5; ++rz) {
                                                        int highestY = lvl.getHighestTile(rx, rz);
                                                        int yMin = py - 5;
                                                        int yMax = py + 5;
                                                        if (yMin < highestY)
                                                            yMin = highestY;
                                                        if (yMax < highestY)
                                                            yMax = highestY;
                                                        if (yMin != yMax) {
                                                            float anim = ((float) ((rr.levelTicks + rx * 3121
                                                                    + rz * 418711) % 32) + rainDelta) / 32.0F;
                                                            float dx = (float) rx + 0.5F - player.x;
                                                            float dz = (float) rz + 0.5F - player.z;
                                                            float fade = MathHelper.sqrt(dx * dx + dz * dz) / 5.0F;
                                                            GL11.glColor4f(1.0F, 1.0F, 1.0F,
                                                                    (1.0F - fade * fade) * 0.7F);
                                                            sr1.begin();
                                                            sr1.vertexUV((float) rx, (float) yMin, (float) rz, 0.0F,
                                                                    (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.vertexUV((float) (rx + 1), (float) yMin,
                                                                    (float) (rz + 1), 2.0F,
                                                                    (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.vertexUV((float) (rx + 1), (float) yMax,
                                                                    (float) (rz + 1), 2.0F,
                                                                    (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.vertexUV((float) rx, (float) yMax, (float) rz, 0.0F,
                                                                    (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.vertexUV((float) rx, (float) yMin, (float) (rz + 1),
                                                                    0.0F, (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.vertexUV((float) (rx + 1), (float) yMin, (float) rz,
                                                                    2.0F, (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.vertexUV((float) (rx + 1), (float) yMax, (float) rz,
                                                                    2.0F, (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.vertexUV((float) rx, (float) yMax, (float) (rz + 1),
                                                                    0.0F, (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                                                            sr1.end();
                                                        }
                                                    }
                                                }

                                                GL11.glEnable(2884);
                                                GL11.glDisable(3042);
                                            }

                                            if (r2.entity != null) {
                                                r2.entity.renderHover(r2.minecraft.textureManager, renderPartial);
                                            }

                                            GL11.glClear(256);
                                            GL11.glLoadIdentity();
                                            if (r2.minecraft.settings.anaglyph) {
                                                GL11.glTranslatef((float) ((stereoEye << 1) - 1) * 0.1F, 0.0F, 0.0F);
                                            }

                                            r2.hurtEffect(renderPartial);
                                            if (r2.minecraft.settings.viewBobbing) {
                                                r2.applyBobbing(renderPartial);
                                            }

                                            HeldBlock hb = r2.heldBlock;
                                            float heldInterp = r2.heldBlock.lastPos
                                                    + (hb.pos - hb.lastPos) * renderPartial;
                                            camPlayer = hb.minecraft.player;
                                            GL11.glPushMatrix();
                                            GL11.glRotatef(
                                                    camPlayer.xRotO
                                                            + (camPlayer.xRot - camPlayer.xRotO) * renderPartial,
                                                    1.0F, 0.0F, 0.0F);
                                            GL11.glRotatef(
                                                    camPlayer.yRotO
                                                            + (camPlayer.yRot - camPlayer.yRotO) * renderPartial,
                                                    0.0F, 1.0F, 0.0F);
                                            hb.minecraft.renderer.setLighting(true);
                                            GL11.glPopMatrix();
                                            GL11.glPushMatrix();
                                            float heldScale = 0.8F;
                                            if (hb.moving) {
                                                float tSwing = ((float) hb.offset + renderPartial) / 7.0F;
                                                float swingSin = MathHelper.sin(tSwing * 3.1415927F);
                                                GL11.glTranslatef(
                                                        -MathHelper.sin(MathHelper.sqrt(tSwing) * 3.1415927F) * 0.4F,
                                                        MathHelper.sin(MathHelper.sqrt(tSwing) * 3.1415927F * 2.0F)
                                                                * 0.2F,
                                                        -swingSin * 0.2F);
                                            }

                                            GL11.glTranslatef(0.7F * heldScale,
                                                    -0.65F * heldScale - (1.0F - heldInterp) * 0.6F, -0.9F * heldScale);
                                            GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
                                            GL11.glEnable(2977);
                                            if (hb.moving) {
                                                float tSwing = ((float) hb.offset + renderPartial) / 7.0F;
                                                float swingSin2 = MathHelper.sin(tSwing * tSwing * 3.1415927F);
                                                GL11.glRotatef(
                                                        MathHelper.sin(MathHelper.sqrt(tSwing) * 3.1415927F) * 80.0F,
                                                        0.0F, 1.0F, 0.0F);
                                                GL11.glRotatef(-swingSin2 * 20.0F, 1.0F, 0.0F, 0.0F);
                                            }

                                            GL11.glColor4f(
                                                    heldInterp = hb.minecraft.level.getBrightness((int) camPlayer.x,
                                                            (int) camPlayer.y, (int) camPlayer.z),
                                                    heldInterp, heldInterp, 1.0F);
                                            ShapeRenderer heldShape = ShapeRenderer.instance;
                                            if (hb.block != null) {
                                                float s = 0.4F;
                                                GL11.glScalef(s, s, s);
                                                GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                                                GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                                                        hb.minecraft.textureManager.load("/terrain.png"));
                                                hb.block.renderPreview(heldShape);
                                            } else {
                                                int selId = camPlayer.inventory.getSelected();
                                                if (selId >= 256) {
                                                    // hide arm: item rendered by renderHeldItem()
                                                } else {
                                                    camPlayer.bindTexture(hb.minecraft.textureManager);
                                                    GL11.glScalef(1.0F, -1.0F, -1.0F);
                                                    GL11.glTranslatef(0.0F, 0.2F, 0.0F);
                                                    GL11.glRotatef(-120.0F, 0.0F, 0.0F, 1.0F);
                                                    float armScale = 0.0625F;
                                                    ModelPart arm = camPlayer.getModel().leftArm;
                                                    if (!arm.hasList)
                                                        arm.generateList(armScale);
                                                    GL11.glCallList(arm.list);
                                                }
                                            }

                                            GL11.glDisable(2977);
                                            GL11.glPopMatrix();
                                            hb.minecraft.renderer.renderHeldItem();
                                            hb.minecraft.renderer.setLighting(false);
                                            if (!r2.minecraft.settings.anaglyph)
                                                break;
                                            ++stereoEye;
                                        }
                                        // Hide HUD hotbar when inventory/crafting/furnace GUI is open
                                        boolean hideHUD = (this.currentScreen instanceof net.classicremastered.minecraft.gui.InventoryScreen)
                                                || (this.currentScreen instanceof net.classicremastered.minecraft.gui.GuiCrafting);

                                        this.renderer.renderUnderwaterOverlay(partial);
                                        r.minecraft.hud.render(partial, hideHUD, mouseUiX, uiH);

                                    } else {
                                        GL11.glViewport(0, 0, r.minecraft.width, r.minecraft.height);
                                        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                                        GL11.glClear(16640);
                                        GL11.glMatrixMode(5889);
                                        GL11.glLoadIdentity();
                                        GL11.glMatrixMode(5888);
                                        GL11.glLoadIdentity();
                                        r.enableGuiMode();
                                    }

                                    if (r.minecraft.currentScreen != null) {
                                        r.minecraft.currentScreen.render(mouseUiX, uiH);
                                    }

                                    // Take pending screenshot AFTER rendering, BEFORE the swap
                                    if (this.pendingScreenshot) {
                                        Screenshot.take(mcDir);
                                        this.pendingScreenshot = false;
                                    }

                                    Thread.yield();
                                    Display.update();
                                }
                            }

                            if (this.settings.limitFramerate) {
                                Display.sync(144); // changed: accurate cap, no fake FPS
                            }

                            checkGLError("Post render");
                            ++framesThisSecond;
                            while (System.currentTimeMillis() >= fpsTimerStartMs + 1000L) {
                                this.debug = framesThisSecond + " fps, " + Chunk.chunkUpdates + " chunk updates";
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
            if (level != null) {
                java.io.File out = new java.io.File(mcDir, level.creativeMode ? "levelc.dat" : "levels.dat");
                this.levelIo.save(level, out);
            }
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
            if (resourceThread != null)
                resourceThread.running = false;
        } catch (Throwable ignored) {
        }

        // Input/Window — only from this thread
        try {
            if (org.lwjgl.input.Mouse.isCreated())
                org.lwjgl.input.Mouse.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Keyboard.isCreated())
                org.lwjgl.input.Keyboard.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.input.Controllers.isCreated())
                org.lwjgl.input.Controllers.destroy();
        } catch (Throwable ignored) {
        }
        try {
            if (org.lwjgl.opengl.Display.isCreated())
                org.lwjgl.opengl.Display.destroy();
        } catch (Throwable ignored) {
        }

        // Dispose AWT wrapper window if present
        try {
            if (this.canvas != null) {
                this.canvas.setIgnoreRepaint(true);
                java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this.canvas);
                if (w instanceof java.awt.Frame)
                    ((java.awt.Frame) w).setIgnoreRepaint(true);
            }
        } catch (Throwable ignored) {
        }
    }

    public final void grabMouse() {
        if (!this.hasMouse) {
            this.hasMouse = true;
            if (this.levelLoaded) {
                try {
                    Mouse.setNativeCursor(this.cursor);
                    Mouse.setCursorPosition(this.width / 2, this.height / 2);
                } catch (LWJGLException var2) {
                    var2.printStackTrace();
                }

                if (this.canvas == null) {
                    this.canvas.requestFocus();
                }
            } else {
                Mouse.setGrabbed(true);
            }

            this.setCurrentScreen((GuiScreen) null);
            this.lastClick = this.ticks + 10000;
        }
    }

    public final void pause() {
        if (this.currentScreen == null) {
            this.setCurrentScreen(new PauseScreen());
        }
    }

    private void waitForCanvasPeer() {
        if (this.canvas == null)
            return;
        // Wait until AWT has created the native peer and the component is showing
        int spins = 0;
        while (!this.canvas.isDisplayable() || !this.canvas.isShowing()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            if (++spins > 500)
                break; // ~5s safety
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
        if (crashLatched)
            return;
        crashLatched = true;

        if (!(loopError instanceof net.classicremastered.minecraft.errors.CrashReportException)) {
            loopError = new net.classicremastered.minecraft.errors.CrashReportException(
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
                    || !(this.gamemode instanceof net.classicremastered.minecraft.gamemode.CreativeGameMode)) {
                this.gamemode = new net.classicremastered.minecraft.gamemode.CreativeGameMode(this);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (this.soundPlayer != null)
                this.soundPlayer.running = false;
            if (this.soundPC != null)
                this.soundPC.stopMusic();
        } catch (Throwable ignored) {
        }
    }

    // Fire attachment check mirroring FireBlock.canStay (side support or solid
    // below)
    boolean fireCanStayAt(int x, int y, int z) {
        if (!this.level.isInBounds(x, y, z))
            return false;
        // solid below
        if (this.level.isSolidTile(x, y - 1, z))
            return true;
        // side attachments
        if (this.level.isSolidTile(x + 1, y, z))
            return true;
        if (this.level.isSolidTile(x - 1, y, z))
            return true;
        if (this.level.isSolidTile(x, y, z + 1))
            return true;
        if (this.level.isSolidTile(x, y, z - 1))
            return true;
        return false;
    }

    // Minimal flammability test for placement (matches your fire block’s burnables)
    private static boolean isFlammable(int id) {
        if (id <= 0 || id >= Block.blocks.length)
            return false;
        Block b = Block.blocks[id];
        if (b == null)
            return false;

        return b == Block.WOOD || b == Block.LOG || b == Block.LEAVES || b == Block.BOOKSHELF || b == Block.TNT
        // wool shades 21..36 in your Classic registry
                || (id >= Block.RED_WOOL.id && id <= Block.WHITE_WOOL.id);
    }

    boolean wouldCollideWithMob(int x, int y, int z) {
        final float eps = 0.001f; // allow face-touching
        AABB blockBox = new AABB(x + eps, y + eps, z + eps, x + 1 - eps, y + 1 - eps, z + 1 - eps);

        // Exclude the player by passing them as the "except" entity
        java.util.List list = this.level.findEntities(this.player, blockBox);
        if (list == null || list.isEmpty())
            return false;

        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!(o instanceof Mob))
                continue; // only care about mobs
            Entity e = (Entity) o;
            if (e.bb != null && e.bb.intersects(blockBox)) {
                return true;
            }
        }
        return false;
    }
    // --- SAFE PLACEMENT HELPERS ---

    /**
     * True if placing a unit block at (x,y,z) would intersect the player's AABB.
     */
    boolean wouldCollideWithPlayer(int x, int y, int z) {
        if (this.player == null || this.player.bb == null)
            return false;
        final float eps = 0.001f; // allow face-touching
        AABB blockBox = new AABB(x + eps, y + eps, z + eps, x + 1 - eps, y + 1 - eps, z + 1 - eps);
        return this.player.bb.intersects(blockBox);
    }

    /**
     * Returns true only if a block was actually placed into empty space without
     * colliding.
     */
    private boolean placeBlockSafely(Level level, int x, int y, int z, int blockId) {
        if (x < 0 || y < 0 || z < 0 || x >= level.width || y >= level.height || z >= level.depth)
            return false;

        // Only place into air (0). If you support replaceables, expand this check.
        if (level.getTile(x, y, z) != 0)
            return false;

        if (wouldCollideWithPlayer(x, y, z))
            return false;

        level.setTile(x, y, z, blockId);
        return true;
    }

    private void tick() {
        if (this.soundPlayer != null) {
            SoundPlayer var1 = this.soundPlayer;
            SoundManager var2 = this.sound;

        }

        this.gamemode.spawnMob();
        HUDScreen var17 = this.hud;
        ++this.hud.ticks;

        int var16;
        for (var16 = 0; var16 < var17.chat.size(); ++var16) {
            ++((ChatLine) var17.chat.get(var16)).time;
        }

        GL11.glBindTexture(3553, this.textureManager.load("/terrain.png"));
        TextureManager var19 = this.textureManager;

        for (var16 = 0; var16 < var19.animations.size(); ++var16) {
            TextureFX var3;
            (var3 = var19.animations.get(var16)).anaglyph = var19.settings.anaglyph;
            var3.animate();
            var19.textureBuffer.clear();
            var19.textureBuffer.put(var3.textureData);
            var19.textureBuffer.position(0).limit(var3.textureData.length);
            GL11.glTexSubImage2D(3553, 0, var3.textureId % 16 << 4, var3.textureId / 16 << 4, 16, 16, 6408, 5121,
                    var19.textureBuffer);
        }

        int var4;
        int var8;
        int var40;
        int var46;
        int var45;
        if (this.networkManager != null && !(this.currentScreen instanceof ErrorScreen)) {
            if (!this.networkManager.isConnected()) {
                this.progressBar.setTitle("Connecting..");
                this.progressBar.setProgress(0);
            } else {
                NetworkManager var20 = this.networkManager;
                if (this.networkManager.successful) {
                    NetworkHandler var18 = var20.netHandler;
                    if (var20.netHandler.connected) {
                        try {
                            NetworkHandler var22 = var20.netHandler;
                            var20.netHandler.channel.read(var22.in);
                            var4 = 0;

                            while (var22.in.position() > 0 && var4++ != 100) {
                                var22.in.flip();
                                byte var5 = var22.in.get(0);
                                PacketType var6;
                                if ((var6 = PacketType.packets[var5]) == null) {
                                    throw new IOException("Bad command: " + var5);
                                }

                                if (var22.in.remaining() < var6.length + 1) {
                                    var22.in.compact();
                                    break;
                                }

                                var22.in.get();
                                Object[] var7 = new Object[var6.params.length];

                                for (var8 = 0; var8 < var7.length; ++var8) {
                                    var7[var8] = var22.readObject(var6.params[var8]);
                                }

                                NetworkManager var42 = var22.netManager;
                                if (var22.netManager.successful) {
                                    if (var6 == PacketType.IDENTIFICATION) {
                                        var42.minecraft.progressBar.setTitle(var7[1].toString());
                                        var42.minecraft.progressBar.setText(var7[2].toString());
                                        var42.minecraft.player.userType = ((Byte) var7[3]).byteValue();
                                    } else if (var6 == PacketType.LEVEL_INIT) {
                                        var42.minecraft.setLevel((Level) null);
                                        var42.levelData = new ByteArrayOutputStream();
                                    } else if (var6 == PacketType.LEVEL_DATA) {
                                        short var11 = ((Short) var7[0]).shortValue();
                                        byte[] var12 = (byte[]) ((byte[]) var7[1]);
                                        byte var13 = ((Byte) var7[2]).byteValue();
                                        var42.minecraft.progressBar.setProgress(var13);
                                        var42.levelData.write(var12, 0, var11);
                                    } else if (var6 == PacketType.LEVEL_FINALIZE) {
                                        try {
                                            var42.levelData.close();
                                        } catch (IOException var14) {
                                            var14.printStackTrace();
                                        }

                                        byte[] var51 = LevelIO
                                                .decompress(new ByteArrayInputStream(var42.levelData.toByteArray()));
                                        var42.levelData = null;
                                        short var55 = ((Short) var7[0]).shortValue();
                                        short var63 = ((Short) var7[1]).shortValue();
                                        short var21 = ((Short) var7[2]).shortValue();
                                        Level var30;
                                        (var30 = new Level()).setNetworkMode(true);
                                        var30.setData(var55, var63, var21, var51);
                                        var42.minecraft.setLevel(var30);
                                        var42.minecraft.online = false;
                                        var42.levelLoaded = true;
                                    } else if (var6 == PacketType.BLOCK_CHANGE) {
                                        if (var42.minecraft.level != null) {
                                            var42.minecraft.level.netSetTile(((Short) var7[0]).shortValue(),
                                                    ((Short) var7[1]).shortValue(), ((Short) var7[2]).shortValue(),
                                                    ((Byte) var7[3]).byteValue());
                                        }
                                    } else {
                                        byte var9;
                                        String var34;
                                        NetworkPlayer var33;
                                        short var36;
                                        short var10004;
                                        byte var10001;
                                        short var47;
                                        short var10003;
                                        if (var6 == PacketType.SPAWN_PLAYER) {
                                            var10001 = ((Byte) var7[0]).byteValue();
                                            String var10002 = (String) var7[1];
                                            var10003 = ((Short) var7[2]).shortValue();
                                            var10004 = ((Short) var7[3]).shortValue();
                                            short var10005 = ((Short) var7[4]).shortValue();
                                            byte var10006 = ((Byte) var7[5]).byteValue();
                                            byte var58 = ((Byte) var7[6]).byteValue();
                                            var9 = var10006;
                                            short var10 = var10005;
                                            var47 = var10004;
                                            var36 = var10003;
                                            var34 = var10002;
                                            var5 = var10001;
                                            if (var5 >= 0) {
                                                var9 = (byte) (var9 + 128);
                                                var47 = (short) (var47 - 22);
                                                var33 = new NetworkPlayer(var42.minecraft, var5, var34, var36, var47,
                                                        var10, (float) (var9 * 360) / 256.0F,
                                                        (float) (var58 * 360) / 256.0F);
                                                var42.players.put(Byte.valueOf(var5), var33);
                                                var42.minecraft.level.addEntity(var33);
                                            } else {
                                                var42.minecraft.level.setSpawnPos(var36 / 32, var47 / 32, var10 / 32,
                                                        (float) (var9 * 320 / 256));
                                                var42.minecraft.player.moveTo((float) var36 / 32.0F,
                                                        (float) var47 / 32.0F, (float) var10 / 32.0F,
                                                        (float) (var9 * 360) / 256.0F, (float) (var58 * 360) / 256.0F);
                                            }
                                        } else {
                                            byte var53;
                                            NetworkPlayer var61;
                                            byte var69;
                                            if (var6 == PacketType.POSITION_ROTATION) {
                                                var10001 = ((Byte) var7[0]).byteValue();
                                                short var66 = ((Short) var7[1]).shortValue();
                                                var10003 = ((Short) var7[2]).shortValue();
                                                var10004 = ((Short) var7[3]).shortValue();
                                                var69 = ((Byte) var7[4]).byteValue();
                                                var9 = ((Byte) var7[5]).byteValue();
                                                var53 = var69;
                                                var47 = var10004;
                                                var36 = var10003;
                                                short var38 = var66;
                                                var5 = var10001;
                                                if (var5 < 0) {
                                                    var42.minecraft.player.moveTo((float) var38 / 32.0F,
                                                            (float) var36 / 32.0F, (float) var47 / 32.0F,
                                                            (float) (var53 * 360) / 256.0F,
                                                            (float) (var9 * 360) / 256.0F);
                                                } else {
                                                    var53 = (byte) (var53 + 128);
                                                    var36 = (short) (var36 - 22);
                                                    if ((var61 = (NetworkPlayer) var42.players
                                                            .get(Byte.valueOf(var5))) != null) {
                                                        var61.teleport(var38, var36, var47,
                                                                (float) (var53 * 360) / 256.0F,
                                                                (float) (var9 * 360) / 256.0F);
                                                    }
                                                }
                                            } else {
                                                byte var37;
                                                byte var44;
                                                byte var49;
                                                byte var65;
                                                byte var67;
                                                if (var6 == PacketType.POSITION_ROTATION_UPDATE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var67 = ((Byte) var7[1]).byteValue();
                                                    var65 = ((Byte) var7[2]).byteValue();
                                                    byte var64 = ((Byte) var7[3]).byteValue();
                                                    var69 = ((Byte) var7[4]).byteValue();
                                                    var9 = ((Byte) var7[5]).byteValue();
                                                    var53 = var69;
                                                    var49 = var64;
                                                    var44 = var65;
                                                    var37 = var67;
                                                    var5 = var10001;
                                                    if (var5 >= 0) {
                                                        var53 = (byte) (var53 + 128);
                                                        if ((var61 = (NetworkPlayer) var42.players
                                                                .get(Byte.valueOf(var5))) != null) {
                                                            var61.queue(var37, var44, var49,
                                                                    (float) (var53 * 360) / 256.0F,
                                                                    (float) (var9 * 360) / 256.0F);
                                                        }
                                                    }
                                                } else if (var6 == PacketType.ROTATION_UPDATE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var67 = ((Byte) var7[1]).byteValue();
                                                    var44 = ((Byte) var7[2]).byteValue();
                                                    var37 = var67;
                                                    var5 = var10001;
                                                    if (var5 >= 0) {
                                                        var37 = (byte) (var37 + 128);
                                                        NetworkPlayer var54;
                                                        if ((var54 = (NetworkPlayer) var42.players
                                                                .get(Byte.valueOf(var5))) != null) {
                                                            var54.queue((float) (var37 * 360) / 256.0F,
                                                                    (float) (var44 * 360) / 256.0F);
                                                        }
                                                    }
                                                } else if (var6 == PacketType.POSITION_UPDATE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var67 = ((Byte) var7[1]).byteValue();
                                                    var65 = ((Byte) var7[2]).byteValue();
                                                    var49 = ((Byte) var7[3]).byteValue();
                                                    var44 = var65;
                                                    var37 = var67;
                                                    var5 = var10001;
                                                    NetworkPlayer var59;
                                                    if (var5 >= 0 && (var59 = (NetworkPlayer) var42.players
                                                            .get(Byte.valueOf(var5))) != null) {
                                                        var59.queue(var37, var44, var49);
                                                    }
                                                } else if (var6 == PacketType.DESPAWN_PLAYER) {
                                                    var5 = ((Byte) var7[0]).byteValue();
                                                    if (var5 >= 0 && (var33 = (NetworkPlayer) var42.players
                                                            .remove(Byte.valueOf(var5))) != null) {
                                                        var33.clear();
                                                        var42.minecraft.level.removeEntity(var33);
                                                    }
                                                } else if (var6 == PacketType.CHAT_MESSAGE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var34 = (String) var7[1];
                                                    var5 = var10001;
                                                    if (var5 < 0) {
                                                        var42.minecraft.hud.addChat("&e" + var34);
                                                    } else {
                                                        var42.players.get(Byte.valueOf(var5));
                                                        var42.minecraft.hud.addChat(var34);
                                                    }
                                                } else if (var6 == PacketType.DISCONNECT) {
                                                    var42.netHandler.close();
                                                    var42.minecraft.setCurrentScreen(
                                                            new ErrorScreen("Connection lost", (String) var7[0]));
                                                } else if (var6 == PacketType.UPDATE_PLAYER_TYPE) {
                                                    var42.minecraft.player.userType = ((Byte) var7[0]).byteValue();
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!var22.connected) {
                                    break;
                                }

                                var22.in.compact();
                            }

                            if (var22.out.position() > 0) {
                                var22.out.flip();
                                var22.channel.write(var22.out);
                                var22.out.compact();
                            }
                        } catch (Exception var15) {
                            var20.minecraft.setCurrentScreen(
                                    new ErrorScreen("Disconnected!", "You\'ve lost connection to the server"));
                            var20.minecraft.online = false;
                            var15.printStackTrace();
                            var20.netHandler.close();
                            var20.minecraft.networkManager = null;
                        }
                    }
                }

                Player var28 = this.player;
                var20 = this.networkManager;
                if (this.networkManager.levelLoaded) {
                    int var24 = (int) (var28.x * 32.0F);
                    var4 = (int) (var28.y * 32.0F);
                    var40 = (int) (var28.z * 32.0F);
                    var46 = (int) (var28.yRot * 256.0F / 360.0F) & 255;
                    var45 = (int) (var28.xRot * 256.0F / 360.0F) & 255;
                    var20.netHandler.send(PacketType.POSITION_ROTATION,
                            new Object[] { Integer.valueOf(-1), Integer.valueOf(var24), Integer.valueOf(var4),
                                    Integer.valueOf(var40), Integer.valueOf(var46), Integer.valueOf(var45) });
                }
            }
        }

        if (this.currentScreen == null && this.player != null && this.player.health <= 0) {
            this.setCurrentScreen((GuiScreen) null);
        }

        if (this.currentScreen == null || this.currentScreen.grabsMouse) {
            int var25;
            while (Mouse.next()) {
                if ((var25 = Mouse.getEventDWheel()) != 0) {
                    this.player.inventory.swapPaint(var25);
                }

                if (this.currentScreen == null) {
                    if (!this.hasMouse && Mouse.getEventButtonState()) {
                        this.grabMouse();
                    } else {
                        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                            this.onMouseClick(0);
                            this.lastClick = this.ticks;
                        }

                        if (Mouse.getEventButton() == 1 && Mouse.getEventButtonState()) {
                            this.onMouseClick(1);
                            this.lastClick = this.ticks;
                        }
                        if (this.hud != null && (this.currentScreen == null
                                || this.currentScreen instanceof net.classicremastered.minecraft.chat.ChatInputScreen)) {
                            if (Mouse.getEventButtonState()) {
                                int mouseX = Mouse.getEventX() * this.width / Display.getWidth();
                                int mouseY = this.height - Mouse.getEventY() * this.height / Display.getHeight() - 1;
                                this.hud.onMouseClick(mouseX, mouseY, Mouse.getEventButton());
                            }
                        }
                        if (Mouse.getEventButton() == 2 && Mouse.getEventButtonState() && this.selected != null) {
                            if ((var16 = this.level.getTile(this.selected.x, this.selected.y,
                                    this.selected.z)) == Block.GRASS.id) {
                                var16 = Block.DIRT.id;
                            }

                            if (var16 == Block.DOUBLE_SLAB.id) {
                                var16 = Block.SLAB.id;
                            }

                            if (var16 == Block.BEDROCK.id) {
                                var16 = Block.STONE.id;
                            }

                            this.player.inventory.grabTexture(var16, this.gamemode instanceof CreativeGameMode);
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

            if (this.currentScreen == null) {
                if (Mouse.isButtonDown(0) && (float) (this.ticks - this.lastClick) >= this.timer.tps / 4.0F
                        && this.hasMouse) {
                    this.onMouseClick(0);
                    this.lastClick = this.ticks;
                }

                boolean rightDown = Mouse.isButtonDown(1);

                if (this.hasMouse) {
                    int selId = this.player.inventory.getSelected();
                    net.classicremastered.minecraft.level.itemstack.Item held = null;
                    if (selId >= 256) {
                        int itemId = selId - 256;
                        if (itemId >= 0 && itemId < net.classicremastered.minecraft.level.itemstack.Item.items.length) {
                            held = net.classicremastered.minecraft.level.itemstack.Item.items[itemId];
                        }
                    }

                    if (rightDown && !wasRightDown && held != null) {
                        // Just pressed → start using
                        held.use(this.player, this.level);
                    }
                    if (rightDown && held != null) {
                        // Held → tick
                        held.tick(this.player, this.level);
                    }
                    if (!rightDown && wasRightDown && held != null) {
                        // Just released → releaseUse
                        held.releaseUse(this.player, this.level);
                    }
                }

                wasRightDown = rightDown;

            }

            boolean var26 = this.currentScreen == null && Mouse.isButtonDown(0) && this.hasMouse;
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

        if (this.currentScreen != null) {
            this.lastClick = this.ticks + 10000;
        }

        if (this.currentScreen != null) {
            this.currentScreen.doInput();
            if (this.currentScreen != null) {
                this.currentScreen.tick();
            }
        }
        this.renderer.renderHeldItem();
        if (this.level != null) {
            Renderer var29 = this.renderer;
            ++this.renderer.levelTicks;
            HeldBlock var41 = var29.heldBlock;
            var29.heldBlock.lastPos = var41.pos;
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

            // Slot ID may point to a Block OR an Item
            if (var4 > 0) {
                if (var4 < Block.blocks.length && Block.blocks[var4] != null) {
                    // It's a block
                    var43 = Block.blocks[var4];
                } else if (var4 < net.classicremastered.minecraft.level.itemstack.Item.items.length
                        && net.classicremastered.minecraft.level.itemstack.Item.items[var4] != null) {
                    // It's an item → don’t assign to var43, leave as null so held-block swing anim
                    // doesn’t break
                    // Later, in renderer.renderHeldItem() you’ll handle drawing items
                    var43 = null;
                }
            }

            float var48 = 0.4F;
            float var50;
            if ((var50 = (var43 == var41.block ? 1.0F : 0.0F) - var41.pos) < -var48) {
                var50 = -var48;
            }

            if (var50 > var48) {
                var50 = var48;
            }

            var41.pos += var50;
            if (var41.pos < 0.1F) {
                var41.block = var43;
            }

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
                    if ((var57 = var32.getHighestTile(var60, var52)) <= var46 + 4 && var57 >= var46 - 4) {
                        float var56 = var39.random.nextFloat();
                        float var62 = var39.random.nextFloat();
                        var39.minecraft.particleManager.spawnParticle(new WaterDropParticle(var32,
                                (float) var60 + var56, (float) var57 + 0.1F, (float) var52 + var62));
                    }
                }
            }

            LevelRenderer var31 = this.levelRenderer;
            ++this.levelRenderer.ticks;
            if (skyRenderer != null)
                skyRenderer.tick();
            this.level.tickEntities();
            net.classicremastered.minecraft.level.itemstack.TelekinesisItem.processArmedImpacts(this.level); // added
            if (this.level != null) {
                this.level.tickTime();
                if (this.levelRenderer != null) {
                    this.levelRenderer.onTimeOfDayMaybeChanged();
                }
            }

            if (!this.isOnline()) {
                this.level.tick();
            }

            this.particleManager.tick();
        }

    }

    public final boolean isOnline() {
        return this.networkManager != null;
    }

    public final void generateLevel(int var1) {
        String var2 = this.session != null ? this.session.username : "anonymous";
        Level var4 = (new LevelGenerator(this.progressBar)).generate(var2, 128 << var1, 128 << var1, 64);
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

    private static boolean isDebugger() {
        try {
            for (String a : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (a.startsWith("-agentlib:jdwp"))
                    return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public final void setLevel(Level var1) {
        if (isDebugger())
            System.out.println("[setLevel] JDWP detected (debugger).");

        // --- Security sandbox for applet versions ---
        if (this.applet != null) {
            boolean badDoc = !(this.applet.getDocumentBase().getHost().equalsIgnoreCase("minecraft.net")
                    || this.applet.getDocumentBase().getHost().equalsIgnoreCase("www.minecraft.net"));
            boolean badCode = !(this.applet.getCodeBase().getHost().equalsIgnoreCase("minecraft.net")
                    || this.applet.getCodeBase().getHost().equalsIgnoreCase("www.minecraft.net"));
            if (badDoc || badCode)
                var1 = null;
        }

        this.level = var1;

        if (var1 != null) {
            try {
                var1.initTransient();
            } catch (Throwable ignored) {
            }

            this.gamemode.apply(var1);
            var1.font = this.fontRenderer;
            var1.minecraft = this;

            if (!this.isOnline()) {
                this.player = (Player) var1.findSubclassOf(Player.class);
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
            if (var1 != null)
                var1.player = this.player;
        }
        this.player.minecraft = this;
        this.player.input = new InputHandlerImpl(this.settings);
        this.gamemode.apply(this.player);

        // --- LevelRenderer hard reset ---
        if (this.levelRenderer != null) {
            LevelRenderer lr = this.levelRenderer;

            if (lr.level != null)
                lr.level.removeListener(lr);

            try {
                if (lr.listId != 0)
                    org.lwjgl.opengl.GL11.glDeleteLists(lr.listId, 2);
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
            if (var1 != null)
                var1.particleEngine = this.particleManager;
            for (int i = 0; i < 2; ++i)
                this.particleManager.particles[i].clear();
        }

        // === Restore serialized mobs after renderer is ready ===
        if (var1 != null && var1.pendingEntities != null && !var1.pendingEntities.isEmpty()) {
            try {
                net.classicremastered.minecraft.mob.MobRegistry.bootstrapDefaults();
                for (Object o : var1.pendingEntities) {
                    if (!(o instanceof net.classicremastered.minecraft.level.Level.SavedMob))
                        continue;
                    net.classicremastered.minecraft.level.Level.SavedMob s = (net.classicremastered.minecraft.level.Level.SavedMob) o;

                    net.classicremastered.minecraft.mob.Mob mob = net.classicremastered.minecraft.mob.MobRegistry
                            .create(s.id, var1, s.x, s.y, s.z);
                    if (mob == null)
                        continue;

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

    /** True if the target entity is within attack reach of the player. */
    boolean entityWithinReach(Entity e) {
        if (e == null || this.player == null)
            return false;

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
        if (w > 0 && h > 0)
            resizeTo(w, h);
    }

}
