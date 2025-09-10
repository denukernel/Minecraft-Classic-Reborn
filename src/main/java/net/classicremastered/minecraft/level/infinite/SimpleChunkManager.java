package net.classicremastered.minecraft.level.infinite;

import java.util.*;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.phys.AABB;

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

    // === Persistence helper (for LevelIO) ===
    public void restoreChunk(long key, byte[] blocks) {
        int cx = (int) (key >> 32);
        int cz = (int) (key & 0xffffffffL);

        SimpleChunk c = new SimpleChunk(cx, cz, height);
        if (blocks.length != c.blocks.length) {
            throw new IllegalArgumentException("Block array length mismatch: got " +
                    blocks.length + ", expected " + c.blocks.length);
        }

        System.arraycopy(blocks, 0, c.blocks, 0, blocks.length);
        c.meshed = false;
        c.loaded = true;
        map.put(key, c);
    }

    public Map<Long, SimpleChunk> getAllChunks() {
        return map;
    }

    private static long key(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
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

    public boolean isChunkLoaded(int cx, int cz) {
        SimpleChunk c = map.get(key(cx, cz));
        return c != null && c.loaded;
    }

    public Collection<SimpleChunk> getLoadedChunksAround(int cx, int cz, int radius) {
        List<SimpleChunk> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int ncx = cx + dx;
                int ncz = cz + dz;
                if (isChunkLoaded(ncx, ncz)) {
                    out.add(map.get(key(ncx, ncz)));
                }
            }
        }
        return out;
    }

    public byte getBlock(int x, int y, int z) {
        if (y < 0 || y >= height) return 0;
        int cx = floorDiv(x, SimpleChunk.SIZE);
        int cz = floorDiv(z, SimpleChunk.SIZE);
        int lx = x & (SimpleChunk.SIZE - 1);
        int lz = z & (SimpleChunk.SIZE - 1);
        SimpleChunk c = getOrCreate(cx, cz);
        return c.blocks[SimpleChunk.idx(lx, y, lz, height)];
    }

    public void setBlock(int x, int y, int z, byte id) {
        if (y < 0 || y >= height) return;
        int cx = floorDiv(x, SimpleChunk.SIZE);
        int cz = floorDiv(z, SimpleChunk.SIZE);
        int lx = x & (SimpleChunk.SIZE - 1);
        int lz = z & (SimpleChunk.SIZE - 1);
        SimpleChunk c = getOrCreate(cx, cz);
        c.blocks[SimpleChunk.idx(lx, y, lz, height)] = id;
        c.meshed = false;
        c.loaded = true;
    }

    private static int floorDiv(int v, int d) {
        int q = v / d, r = v % d;
        return (r < 0) ? q - 1 : q;
    }

    // === World gen selector ===
    private void generateChunk(SimpleChunk c) {
        int worldX = c.cx * SimpleChunk.SIZE;
        int worldZ = c.cz * SimpleChunk.SIZE;

        long dist = Math.max(Math.abs(worldX), Math.abs(worldZ));
        if (dist >= 1_000_000) {
            // Instead of old fake flat generator → use corrupted noise
            terrain.generateChunk(c, worldX, worldZ, false /* not flat, but corruption built-in */);
            return;
        }

        if (terrain != null) {
            terrain.generateChunk(c, worldX, worldZ, mode == Mode.FLAT);
        }
    }



    // === Flat generator ===
    private void generateFlat(SimpleChunk c) {
        final int yGrass = 32;
        final int yDirt = yGrass - 3;
        for (int x = 0; x < SimpleChunk.SIZE; x++) {
            for (int z = 0; z < SimpleChunk.SIZE; z++) {
                for (int y = 0; y < c.height; y++) {
                    byte id = 0;
                    if (y < yDirt) id = (byte) Block.STONE.id;
                    else if (y < yGrass) id = (byte) Block.DIRT.id;
                    else if (y == yGrass) id = (byte) Block.GRASS.id;
                    c.blocks[SimpleChunk.idx(x, y, z, c.height)] = id;
                }
            }
        }
        c.loaded = true;
    }

    // === Fake Far Lands ===
    private void generateFakeFarLands(SimpleChunk c, int worldX, int worldZ) {
        final int baseHeight = 32;
        Random rand = new Random(seed ^ (c.cx * 341873128712L + c.cz * 132897987541L));
        for (int x = 0; x < SimpleChunk.SIZE; x++) {
            for (int z = 0; z < SimpleChunk.SIZE; z++) {
                int globalX = worldX + x;
                int globalZ = worldZ + z;
                int offset = (int)((globalX + globalZ) * 0.002f) % 40;
                offset += (int)(Math.sin(globalX * 0.0005) * 10);
                int columnHeight = baseHeight + offset;
                for (int y = 0; y < c.height; y++) {
                    byte id = 0;
                    if (y == 0) id = (byte) Block.BEDROCK.id;
                    else if (y < columnHeight - 3) id = (byte) Block.STONE.id;
                    else if (y < columnHeight) id = (byte) Block.DIRT.id;
                    else if (y == columnHeight) id = (byte) Block.GRASS.id;
                    c.blocks[SimpleChunk.idx(x, y, z, c.height)] = id;
                }
            }
        }
        c.loaded = true;
    }
    public void markDirty(int cx, int cz) {
        SimpleChunk c = map.get(key(cx, cz));
        if (c != null) {
            c.meshed = false;
            c.loaded = true;
        }
    }

    public void markDirtyWorldCoords(int x, int y, int z) {
        int cx = floorDiv(x, SimpleChunk.SIZE);
        int cz = floorDiv(z, SimpleChunk.SIZE);
        markDirty(cx, cz);
    }
    // === Far Lands warning ===
    public static void checkFarLandsWarning(Minecraft mc, net.classicremastered.minecraft.player.Player player) {
        if (mc == null || mc.hud == null || player == null) return;
        long dist = (long)Math.max(Math.abs(player.x), Math.abs(player.z));
        if (dist >= 1_000_000 && dist <= 5_000_000) {
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
