package net.classicremastered.minecraft.client.screenshot;

import java.io.File;

import net.classicremastered.minecraft.util.Screenshot;

public final class ScreenshotService {
    private final File mcDir;
    private volatile boolean pending = false;

    public ScreenshotService(File mcDir) {
        this.mcDir = mcDir;
    }

    public void request() {
        pending = true;
    }

    public boolean consumeIfRequested() {
        if (pending) {
            pending = false;
            return true;
        }
        return false;
    }

    public void capture() {
        Screenshot.take(mcDir);
    }
}