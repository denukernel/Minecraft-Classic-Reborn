package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.util.MathHelper;

public final class LightEngine {

    private final Level level;
    private float lastDayFactor = -1f;

    public LightEngine(Level level) {
        this.level = level;
    }

    /** Compute blended skylight (0.25..1.0) from day/night cycle. */
    public float getSkyLight() {
        float angle = level.getCelestialAngleSmooth() * (float) Math.PI * 2f;
        float c = MathHelper.cos(angle) * 0.5f + 0.5f;
        float daylight = (c - 0.2f) / 0.8f;
        daylight = clamp01(daylight);
        return 0.25f + 0.75f * daylight;
    }

    public float getBrightness(int x, int y, int z) {
        // --- Base from static light value (fire, lava, etc.) ---
        int id = level.getTile(x, y, z);
        int lv = (id > 0 && Block.blocks[id] != null) ? Block.blocks[id].getLightValue() : 0;
        float blockLight = lv > 0 ? (lv / 15.0f) : 0f;

        // --- Nearby emissive scan (3-block radius) ---
        final int R = 3;
        final float R2 = R * R;
        float glow = blockLight;
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    int bid = level.getTile(x + dx, y + dy, z + dz);
                    if (bid <= 0)
                        continue;
                    Block b = Block.blocks[bid];
                    if (b == null)
                        continue;
                    int lv2 = b.getLightValue();
                    if (lv2 <= 0)
                        continue;

                    float dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 > R2)
                        continue;
                    float strength = (lv2 / 15.0f) * (1.0f - (float) Math.sqrt(dist2) / R);
                    if (strength > glow)
                        glow = strength;
                }
            }
        }

        // --- Daylight factor (make fire/lava dimmer under sun) ---
        float sky = getSkyLight();
        float dayFactor = level.getDaylightBrightness(); // 0.2 night .. 1.0 noon
        float nightFactor = 1.0f - (dayFactor - 0.2f) / 0.8f; // 1 at night, 0 at noon
        if (nightFactor < 0f)
            nightFactor = 0f;
        if (nightFactor > 1f)
            nightFactor = 1f;

        // reduce emissive light intensity during day
        glow *= (0.4f + 0.6f * nightFactor);

        // --- Combine with skylight ---
        float base = level.isLit(x, y, z) ? sky : 0.35f;
        float result = base + (1f - base) * glow;

        // --- Clamp ---
        if (result < 0.05f)
            result = 0.05f;
        if (result > 1.0f)
            result = 1.0f;
        return result;
    }

    /** Recalculate when day/night changes noticeably (~2%). */
    public boolean shouldRefreshChunks() {
        float f = getSkyLight();
        boolean changed = Math.abs(f - lastDayFactor) > 0.02f;
        if (changed)
            lastDayFactor = f;
        return changed;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
