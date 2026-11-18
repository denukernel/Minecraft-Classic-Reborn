package net.classicremastered.minecraft.client.screenshot;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.WorldStorage;
import net.classicremastered.minecraft.level.dimensions.StickmansHouse;
import net.classicremastered.minecraft.util.Screenshot;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;

/** Keeps a recent world-only frame available even when a save exits to the menu. */
public final class WorldPreview {
    private Level scene;
    private Level home;
    private BufferedImage latest;
    private long capturedAt;

    public void clear() {
        scene = null;
        home = null;
        latest = null;
        capturedAt = 0;
    }

    public void capture(Minecraft minecraft) {
        Level active = minecraft.level;
        Level world = StickmansHouse.homeLevel(active);
        if (world == null
                || world.saveFile == null
                || minecraft.isOnline()
                || active.isNetworkMode()
                || WorldStorage.icon(world.saveFile) == null) return;
        long now = System.nanoTime();
        if (scene == active && now - capturedAt < 1_000_000_000L) return;
        scene = active;
        home = world;
        capturedAt = now;
        try {
            latest = Screenshot.captureWorldIcon(minecraft.width, minecraft.height);
            if (world.saveFile.isFile() && !Files.exists(WorldStorage.icon(world.saveFile)))
                save(world, active);
        } catch (RuntimeException | LinkageError failure) {
            latest = null;
        }
    }

    public void save(Level world, Level active) {
        if (home != world || scene != active || latest == null || world.saveFile == null) return;
        try {
            WorldStorage.saveIcon(world.saveFile, latest);
        } catch (IOException | RuntimeException failure) {
            System.err.println("Could not write world preview: " + failure.getMessage());
        }
    }
}
