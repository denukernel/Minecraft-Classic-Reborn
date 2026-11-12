package net.classicremastered.minecraft;

import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.input.Keyboard;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

import javax.imageio.ImageIO;

public final class GameSettings {
    public GameSettings(Minecraft minecraft, File minecraftFolder) {
        bindings =
                new KeyBinding[] {
                    forwardKey,
                    leftKey,
                    backKey,
                    rightKey,
                    jumpKey,
                    inventoryKey,
                    chatKey,
                    saveLocationKey,
                    loadLocationKey,
                    runKey,
                    flyToggleKey,
                    descendKey
                };

        settingCount = 18;

        this.minecraft = minecraft;

        settingsFile = new File(minecraftFolder, "options.txt");

        load();
    }

    public net.classicremastered.minecraft.Difficulty difficulty =
            net.classicremastered.minecraft.Difficulty.NORMAL;
    public float menuLighting = 0.2f; // brightness multiplier for main menu dirt
    public float brightness = 0f;

    /** Display correction only; gameplay continues to query unmodified world light. */
    public float renderBrightness(float light) {
        float boost = Float.isFinite(brightness) ? Math.max(0, Math.min(1, brightness)) : 0;
        light = Math.max(0, Math.min(1, light));
        return light + (1 - light) * boost * .8f;
    }

    public void setBrightness(float value) {
        brightness = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        if (minecraft != null && minecraft.levelRenderer != null) minecraft.levelRenderer.refresh();
        save();
    }

    public String mpUsername = "Player";
    public String lastServerIp = "127.0.0.1";
    public int lastServerPort = 25565;

    private static final String[] renderDistances = new String[] {"FAR", "NORMAL", "SHORT", "TINY"};
    public boolean music = true;
    public boolean sound = true;
    public boolean allowMobSounds = true;

    public enum GraphicsMode {
        FAST("Fast"),
        FANCY("Fancy"),
        FABULOUS("Fabulous");

        public final String label;

        GraphicsMode(String label) {
            this.label = label;
        }
    }

    public volatile GraphicsMode graphicsMode = GraphicsMode.FANCY;
    public boolean fabulousUnavailable;
    public boolean threeDClouds = true;

    public enum ScreenshotType {
        NORMAL("Normal"),
        BUG_REPORT("Bug Report");

        public final String label;

        ScreenshotType(String label) {
            this.label = label;
        }
    }

    public volatile ScreenshotType screenshotType = ScreenshotType.NORMAL;

    public void toggleScreenshotType() {
        screenshotType =
                screenshotType == ScreenshotType.NORMAL
                        ? ScreenshotType.BUG_REPORT
                        : ScreenshotType.NORMAL;
        save();
    }

    public boolean fastGraphics() {
        return graphicsMode == GraphicsMode.FAST;
    }

    public boolean invertMouse = false;
    public boolean autoJump = false;
    public boolean parsecMode = false;
    public boolean showFrameRate = false;
    public int viewDistance = 0;
    public boolean viewBobbing = true;
    public boolean anaglyph = false;
    public boolean limitFramerate = false;
    public boolean finiteWater = true;
    public boolean multithreaded = true;
    public KeyBinding flyToggleKey = new KeyBinding("Fly", Keyboard.KEY_V);
    public KeyBinding forwardKey = new KeyBinding("Forward", 17);
    public KeyBinding leftKey = new KeyBinding("Left", 30);
    public KeyBinding descendKey = new KeyBinding("Descend", Keyboard.KEY_LCONTROL);

    public KeyBinding backKey = new KeyBinding("Back", 31);
    public KeyBinding rightKey = new KeyBinding("Right", 32);
    public KeyBinding jumpKey = new KeyBinding("Jump", 57);
    public KeyBinding inventoryKey = new KeyBinding("Inventory", 18);
    public KeyBinding chatKey = new KeyBinding("Chat", Keyboard.KEY_T);
    public KeyBinding saveLocationKey = new KeyBinding("Save location", 28);
    public KeyBinding loadLocationKey = new KeyBinding("Load location", 19);
    public KeyBinding[] bindings;
    private Minecraft minecraft;
    private File settingsFile;
    public int settingCount;

    public int smoothing = 0;
    public String[] smoothingOptions = new String[] {"OFF", "Automatic", "Universal"};
    public int anisotropic = 0;
    public String[] anisotropicOptions = new String[] {"OFF", "ON"};

    public KeyBinding runKey = new KeyBinding("Run", Keyboard.KEY_LSHIFT);

    public String getBinding(int key) {
        return bindings[key].name + ": " + Keyboard.getKeyName(bindings[key].key);
    }

    public void setBinding(int key, int keyID) {
        bindings[key].key = keyID;

        save();
    }

    public void toggleSetting(int setting, int fogValue) {
        if (setting == 15) {
            boolean wasFast = fastGraphics();
            var modes = GraphicsMode.values();
            graphicsMode = modes[(graphicsMode.ordinal() + 1) % modes.length];
            fabulousUnavailable = false;
            if (wasFast != fastGraphics() && minecraft != null) {
                if (minecraft.textureManager != null) minecraft.textureManager.applyBlockTextures();
                if (minecraft.levelRenderer != null) minecraft.levelRenderer.refresh();
            }
            save();
            return;
        }
        if (setting == 0) {
            music = !music;
        }

        if (setting == 1) {
            sound = !sound;
        }

        if (setting == 2) {
            invertMouse = !invertMouse;
        }

        if (setting == 3) {
            showFrameRate = !showFrameRate;
        }

        if (setting == 4) {
            viewDistance = viewDistance + fogValue & 3;
        }

        if (setting == 5) {
            viewBobbing = !viewBobbing;
        }

        if (setting == 6) {
            anaglyph = !anaglyph;

            TextureManager textureManager = minecraft.textureManager;
            Iterator iterator = this.minecraft.textureManager.textureImages.keySet().iterator();

            int i;
            BufferedImage image;

            while (iterator.hasNext()) {
                i = (Integer) iterator.next();
                image = (BufferedImage) textureManager.textureImages.get(Integer.valueOf(i));

                textureManager.load(image, i);
            }

            iterator = textureManager.textures.keySet().iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                try {
                    if (s.startsWith("##")) {
                        image =
                                TextureManager.load1(
                                        ImageIO.read(
                                                TextureManager.class.getResourceAsStream(
                                                        s.substring(2))));
                    } else {
                        image = ImageIO.read(TextureManager.class.getResourceAsStream(s));
                    }

                    i = (Integer) textureManager.textures.get(s);

                    textureManager.load(image, i);
                } catch (IOException var6) {
                    var6.printStackTrace();
                }
            }
        }

        if (setting == 7) {
            limitFramerate = !limitFramerate;
        }

        if (setting == 8) {
            if (smoothing == smoothingOptions.length - 1) {
                smoothing = 0;
            } else {
                smoothing++;
            }

            minecraft.textureManager.textures.clear();
            minecraft.textureManager.textureImages.clear();

            minecraft.levelRenderer.refresh();

            minecraft.textureManager.applyBlockTextures();
        }

        if (setting == 9) {
            if (anisotropic == anisotropicOptions.length - 1) {
                anisotropic = 0;
            } else {
                anisotropic++;
            }

            minecraft.textureManager.textures.clear();
            minecraft.textureManager.textureImages.clear();

            minecraft.levelRenderer.refresh();
        }
        if (setting == 10) {
            difficulty = difficulty.next();
        }
        if (setting == 11) {
            finiteWater = !finiteWater;
        }
        if (setting == 12) {
            allowMobSounds = !allowMobSounds;
        }
        if (setting == 14) threeDClouds = !threeDClouds;
        if (setting == 13) {
            multithreaded = !multithreaded;
        }

        if (setting == 16) autoJump = !autoJump;
        if (setting == 17) parsecMode = !parsecMode;
        save();
    }

    public String getSetting(int id) {
        if (id == 16) return "Auto-Jump: " + (autoJump ? "ON" : "OFF");
        if (id == 17) return "Parsec Mode: " + (parsecMode ? "ON" : "OFF");
        if (id == 14) return "3D clouds: " + (threeDClouds ? "ON" : "OFF");
        if (id == 15) {
            return "Graphics: "
                    + (graphicsMode == GraphicsMode.FABULOUS && (anaglyph || fabulousUnavailable)
                            ? "Fancy (fallback)"
                            : graphicsMode.label);
        }
        return id == 0
                ? "Music: " + (music ? "ON" : "OFF")
                : (id == 1
                        ? "Sound: " + (sound ? "ON" : "OFF")
                        : (id == 2
                                ? "Invert mouse: " + (invertMouse ? "ON" : "OFF")
                                : (id == 3
                                        ? "Show FPS: " + (showFrameRate ? "ON" : "OFF")
                                        : (id == 4
                                                ? "Render distance: "
                                                        + renderDistances[viewDistance]
                                                : (id == 5
                                                        ? "View bobbing: "
                                                                + (viewBobbing ? "ON" : "OFF")
                                                        : (id == 6
                                                                ? "3d anaglyph: "
                                                                        + (anaglyph ? "ON" : "OFF")
                                                                : (id == 7
                                                                        ? "Limit framerate: "
                                                                                + (limitFramerate
                                                                                        ? "ON"
                                                                                        : "OFF")
                                                                        : (id == 8
                                                                                ? "Smoothing: "
                                                                                        + smoothingOptions[
                                                                                                smoothing]
                                                                                : (id == 9
                                                                                        ? "Anisotropic:"
                                                                                              + " "
                                                                                                + anisotropicOptions[
                                                                                                        anisotropic]
                                                                                        : (id == 10
                                                                                                ? "Difficulty:"
                                                                                                      + " "
                                                                                                        + difficulty
                                                                                                                .getLabel()
                                                                                                : (id
                                                                                                                == 11
                                                                                                        ? "Finite"
                                                                                                              + " Liquids:"
                                                                                                              + " "
                                                                                                                + (finiteWater
                                                                                                                        ? "ON"
                                                                                                                        : "OFF")
                                                                                                        : (id
                                                                                                                        == 12
                                                                                                                ? "Allow"
                                                                                                                      + " mob sounds:"
                                                                                                                      + " "
                                                                                                                        + (allowMobSounds
                                                                                                                                ? "ON"
                                                                                                                                : "OFF")
                                                                                                                : (id
                                                                                                                                == 13
                                                                                                                        ? "Multithreading:"
                                                                                                                              + " "
                                                                                                                                + (multithreaded
                                                                                                                                        ? "ON"
                                                                                                                                        : "OFF")
                                                                                                                        : "")))))))))))));
    }

    private void load() {
        try {
            if (settingsFile.exists()) {
                FileReader fileReader = new FileReader(settingsFile);
                BufferedReader reader = new BufferedReader(fileReader);

                String line = null;

                while ((line = reader.readLine()) != null) {
                    String[] setting = line.split(":");
                    if (setting[0].equals("brightness") && setting.length > 1) {
                        try {
                            brightness = Float.parseFloat(setting[1]);
                        } catch (NumberFormatException ignored) {
                            brightness = 0;
                        }
                        brightness =
                                Float.isFinite(brightness)
                                        ? Math.max(0, Math.min(1, brightness))
                                        : 0;
                    }

                    if (setting[0].equals("music")) {
                        music = setting[1].equals("true");
                    }
                    if (setting[0].equals("difficulty")) {
                        difficulty =
                                net.classicremastered.minecraft.Difficulty.fromString(
                                        setting[1],
                                        net.classicremastered.minecraft.Difficulty.NORMAL);
                    }
                    if (setting[0].equals("mpUsername")) {
                        mpUsername = setting[1];
                    }
                    if (setting[0].equals("lastServerIp")) {
                        lastServerIp = setting[1];
                    }
                    if (setting[0].equals("lastServerPort")) {
                        lastServerPort = Integer.parseInt(setting[1]);
                    }

                    if (setting[0].equals("sound")) {
                        sound = setting[1].equals("true");
                    }

                    if (setting[0].equals("graphicsMode")) {
                        for (GraphicsMode mode : GraphicsMode.values()) {
                            if (mode.name().equals(setting[1])) graphicsMode = mode;
                        }
                    }
                    if (setting[0].equals("screenshotType") && setting.length > 1) {
                        screenshotType =
                                ScreenshotType.BUG_REPORT.name().equals(setting[1])
                                        ? ScreenshotType.BUG_REPORT
                                        : ScreenshotType.NORMAL;
                    }
                    if (setting[0].equals("threeDClouds")) threeDClouds = setting[1].equals("true");
                    if (setting[0].equals("allowMobSounds")) {
                        allowMobSounds = setting[1].equals("true");
                    }

                    if (setting[0].equals("invertYMouse")) {
                        invertMouse = setting[1].equals("true");
                    }
                    if (setting[0].equals("autoJump")) autoJump = setting[1].equals("true");
                    if (setting[0].equals("parsecMode")) parsecMode = setting[1].equals("true");

                    if (setting[0].equals("showFrameRate")) {
                        showFrameRate = setting[1].equals("true");
                    }

                    if (setting[0].equals("viewDistance")) {
                        viewDistance = Math.max(0, Math.min(3, Integer.parseInt(setting[1])));
                    }

                    if (setting[0].equals("bobView")) {
                        viewBobbing = setting[1].equals("true");
                    }

                    if (setting[0].equals("anaglyph3d")) {
                        anaglyph = setting[1].equals("true");
                    }

                    if (setting[0].equals("limitFramerate")) {
                        limitFramerate = setting[1].equals("true");
                    }

                    if (setting[0].equals("finiteWater")) {
                        finiteWater = setting[1].equals("true");
                    }

                    if (setting[0].equals("smoothing")) {
                        smoothing = Integer.parseInt(setting[1]);
                    }

                    if (setting[0].equals("anisotropic")) {
                        anisotropic = Integer.parseInt(setting[1]);
                    }

                    if (setting[0].equals("multithreaded")) {
                        multithreaded = setting[1].equals("true");
                    }

                    for (int index = 0; index < this.bindings.length; index++) {
                        if (setting[0].equals("key_" + bindings[index].name)) {
                            bindings[index].key = Integer.parseInt(setting[1]);
                        }
                    }
                }

                reader.close();
            }
        } catch (Exception e) {
            System.out.println("Failed to load options");

            e.printStackTrace();
        }

        // Also check system property override if set by launcher
        String mtProp = System.getProperty("minecraft.multithreaded");
        if (mtProp != null && !mtProp.isEmpty()) {
            multithreaded = Boolean.parseBoolean(mtProp);
        }
    }

    public void save() {
        try {
            FileWriter fileWriter = new FileWriter(this.settingsFile);
            PrintWriter writer = new PrintWriter(fileWriter);

            writer.println("music:" + music);
            writer.println("brightness:" + brightness);
            writer.println("sound:" + sound);
            writer.println("allowMobSounds:" + allowMobSounds);
            writer.println("threeDClouds:" + threeDClouds);
            writer.println("graphicsMode:" + graphicsMode.name());
            writer.println("screenshotType:" + screenshotType.name());
            writer.println("invertYMouse:" + invertMouse);
            writer.println("autoJump:" + autoJump);
            writer.println("parsecMode:" + parsecMode);
            writer.println("showFrameRate:" + showFrameRate);
            writer.println("viewDistance:" + viewDistance);
            writer.println("bobView:" + viewBobbing);
            writer.println("anaglyph3d:" + anaglyph);
            writer.println("limitFramerate:" + limitFramerate);
            writer.println("finiteWater:" + finiteWater);
            writer.println("difficulty:" + difficulty.name());
            writer.println("smoothing:" + smoothing);
            writer.println("anisotropic:" + anisotropic);
            writer.println("multithreaded:" + multithreaded);
            writer.println("mpUsername:" + mpUsername);
            writer.println("lastServerIp:" + lastServerIp);
            writer.println("lastServerPort:" + lastServerPort);

            for (int binding = 0; binding < bindings.length; binding++) {
                writer.println("key_" + bindings[binding].name + ":" + bindings[binding].key);
            }

            writer.close();
        } catch (Exception e) {
            System.out.println("Failed to save options");

            e.printStackTrace();
        }
    }
}
