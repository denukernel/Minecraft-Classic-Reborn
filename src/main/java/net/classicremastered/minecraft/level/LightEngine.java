package net.classicremastered.minecraft.level;

import java.util.Arrays;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.tile.Block;

public final class LightEngine {

    private final Level level;
    private float lastDayFactor = -1f;

    // Fast local light source cache (eliminates all hash map lookups during chunk meshing)
    private int cachedCx = Integer.MIN_VALUE;
    private int cachedCz = Integer.MIN_VALUE;
    private int[] sourceX = new int[128];
    private int[] sourceY = new int[128];
    private int[] sourceZ = new int[128];
    private int[] sourceLv = new int[128];
    private int sourceCount = 0;
    private float[] meshBrightness;
    private int meshX, meshY, meshZ;
    private boolean cachingMesh;
    private float meshSky;

    /** Cache repeated face-light queries only for the duration of one synchronous mesh. */
    public void beginMesh(int x, int y, int z) {
        if (meshBrightness == null) meshBrightness = new float[18 * 18 * 18];
        Arrays.fill(meshBrightness, -1f);
        meshX = x - 1; meshY = y - 1; meshZ = z - 1;
        cachedCx = cachedCz = Integer.MIN_VALUE;
        meshSky = getSkyLight();
        cachingMesh = true;
    }

    public void endMesh() {
        cachingMesh = false;
    }

    public LightEngine(Level level) {
        this.level = level;
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

    private void ensureCapacity(int cap) {
        if (sourceX.length < cap) {
            int newCap = Math.max(sourceX.length * 2, cap + 64);
            sourceX = Arrays.copyOf(sourceX, newCap);
            sourceY = Arrays.copyOf(sourceY, newCap);
            sourceZ = Arrays.copyOf(sourceZ, newCap);
            sourceLv = Arrays.copyOf(sourceLv, newCap);
        }
    }

    private void updateCache(int cx, int cz) {
        cachedCx = cx;
        cachedCz = cz;
        sourceCount = 0;

        if (level instanceof LevelInfiniteTerrain) {
            LevelInfiniteTerrain lit = (LevelInfiniteTerrain) level;
            for (int dcx = -1; dcx <= 1; dcx++) {
                for (int dcz = -1; dcz <= 1; dcz++) {
                    SimpleChunk sc = lit.chunks.tryGet(cx + dcx, cz + dcz);
                    if (sc != null && sc.emissiveCount > 0 && sc.emissiveBlocks != null) {
                        appendChunkSources(sc);
                    }
                }
            }
        } else if (level instanceof LevelInfiniteFlat) {
            LevelInfiniteFlat lif = (LevelInfiniteFlat) level;
            for (int dcx = -1; dcx <= 1; dcx++) {
                for (int dcz = -1; dcz <= 1; dcz++) {
                    SimpleChunk sc = lif.chunks.tryGet(cx + dcx, cz + dcz);
                    if (sc != null && sc.emissiveCount > 0 && sc.emissiveBlocks != null) {
                        appendChunkSources(sc);
                    }
                }
            }
        } else {
            // Finite Level
            if (level.emissiveCount > 0 && level.emissiveBlocks != null) {
                int count = level.emissiveCount;
                int[] list = level.emissiveBlocks;
                ensureCapacity(count);
                for (int i = 0; i < count; i++) {
                    int packed = list[i];
                    sourceX[sourceCount] = packed & 0x3FF;
                    sourceZ[sourceCount] = (packed >> 10) & 0x3FF;
                    sourceY[sourceCount] = (packed >> 20) & 0xFF;
                    sourceLv[sourceCount] = (packed >> 28) & 0xF;
                    sourceCount++;
                }
            }
        }
    }

    private void appendChunkSources(SimpleChunk sc) {
        int count = sc.emissiveCount;
        int[] list = sc.emissiveBlocks;
        int baseLx = sc.cx << 4;
        int baseLz = sc.cz << 4;
        ensureCapacity(sourceCount + count);

        for (int i = 0; i < count; i++) {
            int packed = list[i];
            sourceX[sourceCount] = baseLx + (packed & 15);
            sourceZ[sourceCount] = baseLz + ((packed >> 4) & 15);
            sourceY[sourceCount] = (packed >> 8) & 0xFFFF;
            sourceLv[sourceCount] = (packed >> 24) & 0xFF;
            sourceCount++;
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
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != cachedCx || cz != cachedCz) {
            updateCache(cx, cz);
        }

        float maxGlow = 0f;
        if (sourceCount > 0) {
            final float R = 8.0f;
            final float R2 = R * R;
            final float invRPlusOne = 1.0f / (R + 1.0f);

            for (int i = 0; i < sourceCount; i++) {
                int dx = x - sourceX[i];
                int dy = y - sourceY[i];
                int dz = z - sourceZ[i];
                int d2 = dx * dx + dy * dy + dz * dz;
                if (d2 <= R2) {
                    float dist = (float) Math.sqrt(d2);
                    float fall = 1.0f - dist * invRPlusOne;
                    float g = (sourceLv[i] / 15.0f) * fall;
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
        float sky = cachingMesh ? meshSky : getSkyLight();
        float base = level.isLit(x, y, z) ? sky : 0.35f;

        // --- Combine base light with emissive glow ---
        float result = base + (1.0f - base) * maxGlow;

        if (result < 0.05f)
            result = 0.05f;
        if (result > 1.0f)
            result = 1.0f;
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
        if (changed)
            lastDayFactor = f;
        return changed;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
