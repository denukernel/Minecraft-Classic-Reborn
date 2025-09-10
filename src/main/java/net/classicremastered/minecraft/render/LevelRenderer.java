package net.classicremastered.minecraft.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.player.Player;

import java.nio.IntBuffer;
import java.util.*;

public final class LevelRenderer {

    private int starList = -1;
    public Level level;
    public TextureManager textureManager;
    public int listId;
    public IntBuffer buffer = BufferUtils.createIntBuffer(65536);
    public List<Chunk> chunks = new ArrayList<>();
    private Chunk[] loadQueue;
    public Chunk[] chunkCache;
    private int xChunks;
    private int yChunks;
    private int zChunks;
    private int baseListId;
    public Minecraft minecraft;
    private int[] chunkDataCache = new int['\uc350'];
    public int ticks = 0;
    private float lastLoadX = -9999.0F;
    private float lastLoadY = -9999.0F;
    private float lastLoadZ = -9999.0F;
    public float cracks;

    public boolean waterReady = false;
    private int lightingCooldown = 0;
    private float lastDayFactor = -1f;

    // === Infinite world cache ===
    public final Map<Long, Chunk> infiniteCache = new HashMap<>();
    private final ArrayDeque<Chunk> dirtyQueue = new ArrayDeque<>();

    private static long key3(int cx, int sy, int cz) {
        return ((((long) cx) << 32) ^ (cz & 0xffffffffL)) ^ (((long) sy) << 56);
    }

    public LevelRenderer(Minecraft mc, TextureManager tex) {
        this.minecraft = mc;
        this.textureManager = tex;
        this.listId = GL11.glGenLists(2);
        this.baseListId = GL11.glGenLists(4096 << 6 << 1);
    }

    // --- Helpers ---
    private boolean isInfiniteLevel() {
        return (level instanceof LevelInfiniteFlat) || (level instanceof LevelInfiniteTerrain);
    }

    public void markDirty(int cx, int sy, int cz) {
        Chunk c = infiniteCache.get(key3(cx, sy, cz));
        if (c != null) {
            c.setAllDirty();
            if (!c.inBuildQueue) {
                c.inBuildQueue = true;
                dirtyQueue.add(c);
            }
        }
    }

    public void markDirty(Chunk c) {
        if (c != null && !c.inBuildQueue) {
            c.inBuildQueue = true;
            dirtyQueue.add(c);
        }
    }

    public void updateSomeChunks() {
        int budget = 3;
        while (!dirtyQueue.isEmpty() && budget-- > 0) {
            Chunk c = dirtyQueue.poll();
            if (c != null) {
                c.inBuildQueue = false;
                c.update();
            }
        }
    }

    // === Refresh ===
    public final void refresh() {
        if (isInfiniteLevel()) {
            infiniteCache.clear();
            dirtyQueue.clear();
            if (chunkCache != null) {
                for (Chunk c : chunkCache) if (c != null) c.dispose();
            }
            chunkCache = null;
            loadQueue = null;
            chunks.clear();
            waterReady = false;
            return;
        }

        // finite path
        if (this.chunkCache != null) {
            for (Chunk c : this.chunkCache) if (c != null) c.dispose();
        }
        try {
            if (this.baseListId != 0) {
                GL11.glDeleteLists(this.baseListId,
                        this.chunkCache != null ? this.chunkCache.length * 2 : 0);
            }
        } catch (Throwable ignored) {}

        this.buffer.clear().limit(0);

        this.xChunks = this.level.width / 16;
        this.yChunks = this.level.depth / 16;
        this.zChunks = this.level.height / 16;

        int chunkCount = this.xChunks * this.yChunks * this.zChunks;
        try {
            this.baseListId = GL11.glGenLists(chunkCount * 2);
        } catch (Throwable t) {
            this.baseListId = 0;
        }

        this.chunkCache = new Chunk[chunkCount];
        this.loadQueue = new Chunk[chunkCount];

        int offset = 0;
        for (int cx = 0; cx < this.xChunks; ++cx) {
            for (int cy = 0; cy < this.yChunks; ++cy) {
                for (int cz = 0; cz < this.zChunks; ++cz) {
                    int idx = (cz * this.yChunks + cy) * this.xChunks + cx;
                    this.chunkCache[idx] =
                            new Chunk(this.level, cx << 4, cy << 4, cz << 4, 16, this.baseListId + offset);
                    this.loadQueue[idx] = this.chunkCache[idx];
                    offset += 2;
                }
            }
        }
        this.chunks.clear();
        this.queueChunks(0, 0, 0, this.level.width, this.level.depth, this.level.height);
    }

    // === Sort & Draw ===
 // === Sort & Draw ===
    public final int sortChunks(Player pl, int pass) {
        updateSomeChunks();

        if (isInfiniteLevel()) {
            final int pcx = (int) Math.floor(pl.x / 16.0);
            final int pcz = (int) Math.floor(pl.z / 16.0);
            final int view = 8;
            final int slicesY = (this.level.depth + 15) >> 4;

            int len = 0;
            this.buffer.clear();

            for (int ring = 0; ring <= view; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;

                        final int cx = pcx + dx, cz = pcz + dz;
                        for (int sy = 0; sy < slicesY; sy++) {
                            final long k = key3(cx, sy, cz);
                            Chunk c = infiniteCache.get(k);
                            if (c == null) {
                                int baseId = GL11.glGenLists(2); // one for terrain/lava, one for water
                                c = new Chunk(this.level, (cx << 4), (sy << 4), (cz << 4), 16, baseId);
                                c.setAllDirty();
                                c.inBuildQueue = true;
                                infiniteCache.put(k, c);
                                dirtyQueue.add(c);
                            }
                            c.visible = true;
                            len = c.appendLists(this.chunkDataCache, len, pass);
                        }
                    }
                }
            }

            if (len > 0) {
                this.buffer.put(this.chunkDataCache, 0, len);
                this.buffer.flip();

                if (pass == 0) {
                    // solids + lava → terrain atlas
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                            this.textureManager.load("/terrain.png"));
                } else {
                    // water → water atlas
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                            this.textureManager.load("/water.png"));
                }

                GL11.glCallLists(this.buffer);
            }
            return len;
        }

        // === finite path ===
        float dx = pl.x - this.lastLoadX;
        float dy = pl.y - this.lastLoadY;
        float dz = pl.z - this.lastLoadZ;
        if (dx * dx + dy * dy + dz * dz > 64.0F) {
            this.lastLoadX = pl.x;
            this.lastLoadY = pl.y;
            this.lastLoadZ = pl.z;
            Arrays.sort(this.loadQueue, new ChunkDistanceComparator(pl));
        }

        int len = 0;
        for (Chunk c : this.loadQueue)
            len = c.appendLists(this.chunkDataCache, len, pass);

        this.buffer.clear();
        if (len > 0) {
            this.buffer.put(this.chunkDataCache, 0, len);
            this.buffer.flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureManager.load("/terrain.png"));
            GL11.glCallLists(this.buffer);
        }
        return len;
    }

    public final void queueChunks(int x0, int y0, int z0, int x1, int y1, int z1) {
        if (isInfiniteLevel()) return;

        x0 /= 16; y0 /= 16; z0 /= 16;
        x1 /= 16; y1 /= 16; z1 /= 16;
        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (z0 < 0) z0 = 0;
        if (x1 > this.xChunks - 1) x1 = this.xChunks - 1;
        if (y1 > this.yChunks - 1) y1 = this.yChunks - 1;
        if (z1 > this.zChunks - 1) z1 = this.zChunks - 1;

        for (int cx = x0; cx <= x1; ++cx) {
            for (int cy = y0; cy <= y1; ++cy) {
                for (int cz = z0; cz <= z1; ++cz) {
                    Chunk c = this.chunkCache[(cz * this.yChunks + cy) * this.xChunks + cx];
                    if (!c.loaded) {
                        c.loaded = true;
                        this.chunks.add(c);
                    }
                }
            }
        }
    }

    public void onTimeOfDayMaybeChanged() {
        if (level == null) return;
        float f = level.getDayFactor();
        if (lightingCooldown > 0) { lightingCooldown--; return; }
        if (lastDayFactor < 0f) { lastDayFactor = f; return; }
        if (Math.abs(f - lastDayFactor) > 0.10f) {
            lastDayFactor = f;
            lightingCooldown = 5;
            refresh();
        }
    }

    public void renderEntities(float partial) {
        if (level == null) return;
        for (Entity e : level.entityList) {
            if (!e.removed) e.render(textureManager, partial);
        }
    }
}
