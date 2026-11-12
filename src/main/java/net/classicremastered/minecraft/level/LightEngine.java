package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.util.MathHelper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class LightEngine {

    private final Level level;
    private float lastDayFactor = -1f;
    private final SkyLightCache skyCache;

    private record FireCell(int x, int y, int z) {
    }

    private Set<FireCell> burningMobs = Set.of();

    void refreshBurningMobs() {
        Set<FireCell> next = new HashSet<>();
        synchronized (level.blockMap.all) {
            for (var entity : level.blockMap.all)
                if (entity instanceof Mob mob && !mob.removed && mob.remainingFireTicks > 0)
                    next.add(new FireCell(MathHelper.floor(mob.x),
                            MathHelper.floor((mob.bb.y0 + mob.bb.y1) * .5f), MathHelper.floor(mob.z)));
        }
        if (next.equals(burningMobs))
            return;
        for (FireCell source : burningMobs)
            if (!next.contains(source))
                queueGlow(source);
        for (FireCell source : next)
            if (!burningMobs.contains(source))
                queueGlow(source);
        burningMobs = next;
        querySources.cx = querySources.cz = Integer.MIN_VALUE;
    }

    private void queueGlow(FireCell source) {
        // Face lighting can sample one block beyond a mesh boundary.
        level.queueLightRegion(source.x, source.y, source.z, 9);
    }

    public void blockChanged(int x, int y, int z) {
        skyCache.invalidate(x, z);
        querySources.cx = querySources.cz = Integer.MIN_VALUE;
    }

    public float getAmbientBrightness(int x, int y, int z) {
        float sky = cachingMesh ? meshSky : getSkyLight();
        return Math.max(.2f + .15f * sky, sky * skyCache.exposure(x, y, z));
    }

    private static final class Sources {
        int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE, count;
        long revision = Long.MIN_VALUE;
        int[] x = new int[128], y = new int[128], z = new int[128], light = new int[128];

        void add(int px, int py, int pz, int value, int minY, int maxY) {
            if (py < minY || py > maxY) return;
            if (count == x.length) {
                int capacity = count * 2;
                x = Arrays.copyOf(x, capacity);
                y = Arrays.copyOf(y, capacity);
                z = Arrays.copyOf(z, capacity);
                light = Arrays.copyOf(light, capacity);
            }
            x[count] = px;
            y[count] = py;
            z[count] = pz;
            light[count++] = value;
        }
    }

    private final Sources querySources = new Sources(), meshSources = new Sources();
    private float[] meshBrightness;
    private int meshX, meshY, meshZ;
    private boolean cachingMesh;
    private float meshSky;

    /** Cache repeated face-light queries only for the duration of one synchronous mesh. */
    public void beginMesh(int x, int y, int z) {
        if (meshBrightness == null) meshBrightness = new float[18 * 18 * 18];
        Arrays.fill(meshBrightness, -1f);
        meshX = x - 1;
        meshY = y - 1;
        meshZ = z - 1;
        querySources.cx = querySources.cz = Integer.MIN_VALUE;
        updateCache(meshSources, x >> 4, z >> 4, meshY - 8, meshY + 25);
        meshSky = getSkyLight();
        cachingMesh = true;
    }

    public void endMesh() {
        cachingMesh = false;
    }

    public LightEngine(Level level) {
        this.level = level;
        this.skyCache = new SkyLightCache(level);
        try {
            this.lastDayFactor = getSkyLight();
        } catch (Throwable ignored) {
            this.lastDayFactor = 0.5f;
        }
    }

    /** Compute blended skylight (0.25..1.0) from day/night cycle. */
    public float getSkyLight() {
        return level.getDayFactorSmooth();
    }

    private void updateCache(Sources sources, int cx, int cz, int minY, int maxY) {
        sources.cx = cx;
        sources.cz = cz;
        sources.count = 0;
        int minCx = cx - 1, maxCx = cx + 1, minCz = cz - 1, maxCz = cz + 1;
        if (sources == meshSources) {
            minCx = (meshX - 8) >> 4;
            maxCx = (meshX + 25) >> 4;
            minCz = (meshZ - 8) >> 4;
            maxCz = (meshZ + 25) >> 4;
        }

        if (level instanceof LevelInfiniteTerrain) {
            LevelInfiniteTerrain lit = (LevelInfiniteTerrain) level;
            for (int sx = minCx; sx <= maxCx; sx++) {
                for (int sz = minCz; sz <= maxCz; sz++) {
                    SimpleChunk sc = lit.chunks.tryGet(sx, sz);
                    if (sc != null && sc.emissiveCount > 0 && sc.emissiveBlocks != null) {
                        appendChunkSources(sources, sc, minY, maxY);
                    }
                }
            }
        } else if (level instanceof LevelInfiniteFlat) {
            LevelInfiniteFlat lif = (LevelInfiniteFlat) level;
            for (int sx = minCx; sx <= maxCx; sx++) {
                for (int sz = minCz; sz <= maxCz; sz++) {
                    SimpleChunk sc = lif.chunks.tryGet(sx, sz);
                    if (sc != null && sc.emissiveCount > 0 && sc.emissiveBlocks != null) {
                        appendChunkSources(sources, sc, minY, maxY);
                    }
                }
            }
        } else {
            // Eight-block glow cannot reach beyond these neighboring columns.
            sources.revision = level.getEmissiveRevision();
            for (int sx = minCx; sx <= maxCx; sx++)
                for (int sz = minCz; sz <= maxCz; sz++) {
                    int[] indices = level.getEmissiveColumn(sx, sz);
                    int count = level.getEmissiveColumnCount(sx, sz);
                    for (int i = 0; i < count; i++) {
                        int packed = level.emissiveBlocks[indices[i]];
                        sources.add(
                                packed & 1023,
                                (packed >>> 20) & 255,
                                (packed >>> 10) & 1023,
                                (packed >>> 28) & 15,
                                minY,
                                maxY);
                    }
                }
        }
        for (FireCell source : burningMobs) {
            int sx = source.x >> 4, sz = source.z >> 4;
            if (sx >= minCx && sx <= maxCx && sz >= minCz && sz <= maxCz)
                sources.add(source.x, source.y, source.z, Block.FIRE.getLightValue(), minY, maxY);
        }
    }

    private void appendChunkSources(Sources sources, SimpleChunk sc, int minY, int maxY) {
        int count = sc.emissiveCount;
        int[] list = sc.emissiveBlocks;
        int baseLx = sc.cx << 4;
        int baseLz = sc.cz << 4;

        for (int i = 0; i < count; i++) {
            int packed = list[i];
            sources.add(
                    baseLx + (packed & 15),
                    (packed >>> 8) & 0xFFFF,
                    baseLz + ((packed >>> 4) & 15),
                    (packed >>> 24) & 0xFF,
                    minY,
                    maxY);
        }
    }

    public float getBrightness(int x, int y, int z) {
        if (cachingMesh) {
            int dx = x - meshX, dy = y - meshY, dz = z - meshZ;
            if (dx >= 0 && dx < 18 && dy >= 0 && dy < 18 && dz >= 0 && dz < 18) {
                int index = (dy * 18 + dz) * 18 + dx;
                float brightness = meshBrightness[index];
                if (brightness < 0) meshBrightness[index] = brightness = computeBrightness(x, y, z);
                return brightness;
            }
        }
        return computeBrightness(x, y, z);
    }

    private float computeBrightness(int x, int y, int z) {
        if (y < 0) return 0.05f;
        if (y >= level.depth) return 1.0f;

        // --- Direct light from block at (x, y, z) ---
        int selfId = level.getTile(x, y, z);
        if (selfId > 0 && Block.blocks[selfId] != null) {
            int selfLv = Block.blocks[selfId].getLightValue();
            if (selfLv >= 15) {
                return 1.0f;
            }
        }

        // --- Fast chunk-cached emissive evaluation ---
        boolean inMesh =
                cachingMesh
                        && x >= meshX
                        && x < meshX + 18
                        && y >= meshY
                        && y < meshY + 18
                        && z >= meshZ
                        && z < meshZ + 18;
        Sources sources = inMesh ? meshSources : querySources;
        if (!inMesh) {
            int cx = x >> 4, cz = z >> 4;
            boolean finite =
                    !(level instanceof LevelInfiniteFlat)
                            && !(level instanceof LevelInfiniteTerrain);
            if (cx != sources.cx
                    || cz != sources.cz
                    || finite && sources.revision != level.getEmissiveRevision())
                updateCache(sources, cx, cz, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        float maxGlow = 0f;
        if (sources.count > 0) {
            final float R = 8.0f;
            final float R2 = R * R;
            final float invRPlusOne = 1.0f / (R + 1.0f);

            for (int i = 0; i < sources.count; i++) {
                int dx = x - sources.x[i];
                int dy = y - sources.y[i];
                int dz = z - sources.z[i];
                int d2 = dx * dx + dy * dy + dz * dz;
                if (d2 <= R2) {
                    float dist = (float) Math.sqrt(d2);
                    float fall = 1.0f - dist * invRPlusOne;
                    int light = sources.light[i];
                    if (light == 7
                            && level.getTile(sources.x[i], sources.y[i], sources.z[i])
                                    == Block.REDSTONE_TORCH.id
                            && level.redstone()
                                            .state(sources.x[i], sources.y[i], sources.z[i])
                                            .power
                                    == 0) light = 0;
                    float g = (light / 15.0f) * fall;
                    if (g > maxGlow) {
                        maxGlow = g;
                        if (maxGlow >= 1.0f) {
                            maxGlow = 1.0f;
                            break;
                        }
                    }
                }
            }
        }

        // --- Skylight & Cave Base ---
        float base = getAmbientBrightness(x, y, z);

        // --- Combine base light with emissive glow ---
        float result = base + (1.0f - base) * maxGlow;

        if (result < 0.05f) result = 0.05f;
        if (result > 1.0f) result = 1.0f;
        return result;
    }

    /** Keep baked terrain lighting close to the continuously changing sky. */
    public boolean shouldRefreshChunks() {
        float f = getSkyLight();
        if (lastDayFactor < 0f) {
            lastDayFactor = f;
            return false;
        }
        boolean changed = Math.abs(f - lastDayFactor) > 0.02f;
        if (changed) lastDayFactor = f;
        return changed;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
