package net.classicremastered.minecraft.level.infinite;

import java.util.*;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.tile.Block;

public final class SimpleChunkManager {
    public enum Mode { FLAT, TERRAIN }

    private final Map<Long, SimpleChunk> map = new HashMap<>();
    private final int height;
    private final long seed;
    private final InfiniteTerrainGenerator terrain;
    private final Mode mode;

    private static boolean warnedFarLands = false;
    private static int farlandsDamageCounter = 0;

    public SimpleChunkManager(long seed, int height, Mode mode) {
        this.seed = seed;
        this.height = height;
        this.mode = mode;
        this.terrain = (mode == Mode.TERRAIN ? new InfiniteTerrainGenerator(seed, height) : null);
    }

    private static long key(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }

    public Map<Long, SimpleChunk> getAllChunks() {
        return map;
    }

    public void restoreChunk(long key, byte[] blocks) {
        int cx = (int) (key >> 32);
        int cz = (int) (key & 0xffffffffL);

        SimpleChunk c = new SimpleChunk(cx, cz, height);
        if (blocks.length != c.blocks.length) {
            return;
        }

        System.arraycopy(blocks, 0, c.blocks, 0, blocks.length);
        c.meshed = false;
        c.loaded = true;
        c.lastAccessTick = 0L;
        map.put(key, c);
    }

    public boolean isChunkLoaded(int cx, int cz) {
        SimpleChunk c = map.get(key(cx, cz));
        return c != null && c.loaded;
    }

    public SimpleChunk tryGet(int cx, int cz) {
        return map.get(key(cx, cz));
    }

    public SimpleChunk getOrCreate(int cx, int cz) {
        long k = key(cx, cz);
        SimpleChunk c = map.get(k);
        if (c == null) {
            c = new SimpleChunk(cx, cz, height);
            generateChunk(c);
            map.put(k, c);
        }
        c.loaded = true;
        return c;
    }

    public Collection<SimpleChunk> getLoadedChunksAround(int cx, int cz, int radius) {
        List<SimpleChunk> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int ncx = cx + dx;
                int ncz = cz + dz;
                SimpleChunk c = map.get(key(ncx, ncz));
                if (c != null && c.loaded) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    public byte getBlock(int x, int y, int z) {
        if (y < 0 || y >= height) return 0;
        int cx = x >> 4;
        int cz = z >> 4;
        int lx = x & (SimpleChunk.SIZE - 1);
        int lz = z & (SimpleChunk.SIZE - 1);
        SimpleChunk c = getOrCreate(cx, cz);
        return c.blocks[SimpleChunk.idx(lx, y, lz, height)];
    }

    public void setBlock(int x, int y, int z, byte id) {
        if (y < 0 || y >= height) return;
        int cx = x >> 4;
        int cz = z >> 4;
        int lx = x & (SimpleChunk.SIZE - 1);
        int lz = z & (SimpleChunk.SIZE - 1);
        SimpleChunk c = getOrCreate(cx, cz);
        c.blocks[SimpleChunk.idx(lx, y, lz, height)] = id;
        c.meshed = false;
        c.loaded = true;
    }

    private void generateChunk(SimpleChunk c) {
        int worldX = c.cx * SimpleChunk.SIZE;
        int worldZ = c.cz * SimpleChunk.SIZE;

        long dist = Math.max(Math.abs(worldX), Math.abs(worldZ));
        if (dist >= 1_000_000L) {
            if (terrain != null) {
                terrain.generateChunk(c, worldX, worldZ, false);
            }
            return;
        }

        if (terrain != null) {
            terrain.generateChunk(c, worldX, worldZ, mode == Mode.FLAT);
        }
    }

    public void markDirty(int cx, int cz) {
        SimpleChunk c = map.get(key(cx, cz));
        if (c != null) {
            c.meshed = false;
            c.loaded = true;
        }
    }

    public void markDirtyWorldCoords(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        markDirty(cx, cz);
    }

    public static void checkFarLandsWarning(Minecraft mc, net.classicremastered.minecraft.player.Player player) {
        if (mc == null || mc.hud == null || player == null) return;
        long dist = (long) Math.max(Math.abs(player.x), Math.abs(player.z));
        if (dist >= 1_000_000L && dist <= 5_000_000L) {
            if (!warnedFarLands) {
                mc.hud.addChat("&4⚠ The world feels unstable... you are entering the Far Lands!");
                warnedFarLands = true;
            }
            if (!mc.level.creativeMode) {
                farlandsDamageCounter++;
                if (farlandsDamageCounter >= 20) {
                    farlandsDamageCounter = 0;
                    player.invulnerableTime = 0;
                    player.hurt(null, 1);
                }
            } else {
                farlandsDamageCounter = 0;
            }
        } else {
            warnedFarLands = false;
            farlandsDamageCounter = 0;
        }
    }
}
