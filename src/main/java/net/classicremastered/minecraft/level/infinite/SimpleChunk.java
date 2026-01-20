package net.classicremastered.minecraft.level.infinite;

public final class SimpleChunk {
    public static final int SIZE = 16;

    public final int cx;
    public final int cz;
    public final int height;

    public byte[] blocks;
    public byte[] light;

    public boolean meshed;
    public boolean loaded;

    public long lastAccessTick = 0;

    public boolean corrupted26M = false;
    public boolean corrupted30M = false;

    public SimpleChunk(int cx, int cz, int height) {
        this.cx = cx;
        this.cz = cz;
        this.height = height;
        this.blocks = new byte[SIZE * height * SIZE];
        this.light = new byte[SIZE * height * SIZE];
        this.meshed = false;
        this.loaded = false;
    }

    public static int idx(int x, int y, int z, int h) {
        return (y * SIZE + z) * SIZE + x;
    }

    public void markActive(long tick) {
        this.loaded = true;
        this.lastAccessTick = tick;
    }

    public boolean isActive(long currentTick, int timeout) {
        return loaded && currentTick - lastAccessTick <= timeout;
    }

    public int getLight(int x, int y, int z) {
        return light[idx(x, y, z, height)] & 0xFF;
    }

    public void setLight(int x, int y, int z, int value) {
        if (value < 0) value = 0;
        if (value > 15) value = 15;
        light[idx(x, y, z, height)] = (byte) value;
    }

    public void clearCorruptionFlags() {
        corrupted26M = false;
        corrupted30M = false;
    }

    public void resetArrays() {
        this.blocks = new byte[SIZE * height * SIZE];
        this.light = new byte[SIZE * height * SIZE];
        this.meshed = false;
        this.loaded = false;
        this.lastAccessTick = 0;
        corrupted26M = false;
        corrupted30M = false;
    }

    public void dispose() {
        this.blocks = null;
        this.light = null;
        this.loaded = false;
        this.meshed = false;
        corrupted26M = false;
        corrupted30M = false;
    }
}
