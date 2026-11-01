package net.classicremastered.minecraft.level.infinite;

import java.util.*;
import java.util.concurrent.*;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;

public final class SimpleChunkManager {
    public enum Mode { FLAT, TERRAIN }

    private final Map<Long, SimpleChunk> map = new ConcurrentHashMap<>();
    private final int height;
    private final long seed;
    private final InfiniteTerrainGenerator terrain;
    private final Mode mode;
    public Level level;

    // Multi-core thread pool for asynchronous background chunk generation
    private static final int WORKER_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
    private final ExecutorService executor = Executors.newFixedThreadPool(WORKER_THREADS, new ThreadFactory() {
        private int count = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ChunkGen-Worker-" + (++count));
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });
    private final Set<Long> pendingAsync = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static boolean warnedFarLands = false;
    private static int farlandsDamageCounter = 0;

    public SimpleChunkManager(long seed, int height, Mode mode) {
        this.seed = seed;
        this.height = height;
        this.mode = mode;
        this.terrain = new InfiniteTerrainGenerator(seed, height);
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
        c.buildEmissiveList();
        c.meshed = false;
        c.loaded = true;
        c.lastAccessTick = 0L;
        map.put(key, c);
    }

    public boolean isChunkLoaded(int cx, int cz) {
        SimpleChunk c = map.get(key(cx, cz));
        return c != null && c.loaded;
    }

    private long lastCx = Long.MIN_VALUE;
    private long lastCz = Long.MIN_VALUE;
    private SimpleChunk lastChunk = null;

    public SimpleChunk tryGet(int cx, int cz) {
        if (cx == lastCx && cz == lastCz && lastChunk != null) {
            return lastChunk;
        }
        SimpleChunk c = map.get(key(cx, cz));
        if (c != null) {
            lastCx = cx;
            lastCz = cz;
            lastChunk = c;
        }
        return c;
    }

    /**
     * Multithreaded prefetch: schedules background generation tasks for chunks around (cx, cz).
     */
    public void prefetch(int centerCx, int centerCz, int radius) {
        if (executor.isShutdown()) return;

        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    final int cx = centerCx + dx;
                    final int cz = centerCz + dz;
                    final long k = key(cx, cz);

                    if (!map.containsKey(k) && pendingAsync.add(k)) {
                        executor.submit(() -> {
                            try {
                                if (map.containsKey(k)) return;
                                SimpleChunk c = new SimpleChunk(cx, cz, height);
                                generateChunk(c);
                                c.loaded = true;
                                map.put(k, c);
                            } catch (Throwable t) {
                                t.printStackTrace();
                            } finally {
                                pendingAsync.remove(k);
                            }
                        });
                    }
                }
            }
        }
    }

    public SimpleChunk getOrCreate(int cx, int cz) {
        if (cx == lastCx && cz == lastCz && lastChunk != null) {
            return lastChunk;
        }
        long k = key(cx, cz);
        SimpleChunk c = map.get(k);
        if (c == null) {
            c = new SimpleChunk(cx, cz, height);
            generateChunk(c);
            c.loaded = true;
            map.put(k, c);
            pendingAsync.remove(k);
        }
        c.loaded = true;
        lastCx = cx;
        lastCz = cz;
        lastChunk = c;
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
        int idx = SimpleChunk.idx(lx, y, lz, height);
        byte old = c.blocks[idx];
        if (old != id) {
            c.blocks[idx] = id;
            c.buildEmissiveList();
        }
        c.meshed = false;
        c.loaded = true;
    }

    private void generateChunk(SimpleChunk c) {
        int worldX = c.cx * SimpleChunk.SIZE;
        int worldZ = c.cz * SimpleChunk.SIZE;

        if (terrain != null) {
            terrain.generateChunk(c, worldX, worldZ, mode == Mode.FLAT, level);
        }
        c.buildEmissiveList();
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

    public void dispose() {
        shutdown();
        for (SimpleChunk c : map.values()) {
            if (c != null) c.dispose();
        }
        map.clear();
    }

    public void shutdown() {
        try {
            executor.shutdownNow();
        } catch (Throwable ignored) {
        }
        pendingAsync.clear();
    }
}
