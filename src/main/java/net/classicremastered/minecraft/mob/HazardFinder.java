package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

/**
 * Hazard locating helpers.
 * This version focuses on FALL-DAMAGE PITS (deep air columns with safe bottom),
 * and provides precise edge targets so a controlled player steps off the lip.
 */
final class HazardFinder {
    private HazardFinder() {}

    /** Result describing a pit entry target the player should be steered to. */
    static final class PitTarget {
        public final float targetX, targetY, targetZ; // point just over the edge (inside air) to cross the lip
        public final int   dropDepth;                 // vertical air run after the edge
        PitTarget(float x, float y, float z, int d) { targetX = x; targetY = y; targetZ = z; dropDepth = d; }
    }

    // =============================================================================================
    // PUBLIC PIT API
    // =============================================================================================

    /**
     * Find the nearest pit that will cause fall damage:
     * - Walkable edge (air here, solid directly below here).
     * - Immediately beyond edge: a vertical air column >= minDrop.
     * - Bottom of the column: SOLID and NOT liquid (no water/lava).
     * Returns a target just past the edge so stepping crosses into the void.
     */
    static PitTarget findNearestFallPit(Level level, float cx, float cy, float cz, int searchRadius, int minDrop) {
        if (level == null) return null;
        int icx = MathHelper.floor(cx), icy = clampY(level, MathHelper.floor(cy)), icz = MathHelper.floor(cz);

        PitTarget best = null;
        int bestD2 = Integer.MAX_VALUE;

        for (int dz = -searchRadius; dz <= searchRadius; dz++) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                int x = icx + dx, z = icz + dz;

                // Pick a stable walkable Y near the actor
                int y = findWalkableY(level, x, z, icy + 3, icy - 6);
                if (y == Integer.MIN_VALUE) continue;

                // Check each of 4 cardinal edges from that walkable cell
                // We compute a target slightly beyond the edge (0.6 blocks) so the player steps off.
                PitTarget cand;

                cand = checkEdge(level, x, y, z,  1,  0, minDrop);
                if (cand != null) { int d2 = dx*dx + (y - icy)*(y - icy) + dz*dz; if (d2 < bestD2) { bestD2 = d2; best = cand; } }

                cand = checkEdge(level, x, y, z, -1,  0, minDrop);
                if (cand != null) { int d2 = dx*dx + (y - icy)*(y - icy) + dz*dz; if (d2 < bestD2) { bestD2 = d2; best = cand; } }

                cand = checkEdge(level, x, y, z,  0,  1, minDrop);
                if (cand != null) { int d2 = dx*dx + (y - icy)*(y - icy) + dz*dz; if (d2 < bestD2) { bestD2 = d2; best = cand; } }

                cand = checkEdge(level, x, y, z,  0, -1, minDrop);
                if (cand != null) { int d2 = dx*dx + (y - icy)*(y - icy) + dz*dz; if (d2 < bestD2) { bestD2 = d2; best = cand; } }
            }
        }

        return best;
    }

    /** True if player has *just* landed with noticeable fall distance. */
    static boolean playerHasLandedAfterFall(Player p, float minFall) {
        if (p == null) return false;
        // Classic keeps fallDistance accumulated while airborne and resets on land in causeFallDamage().
        // If we're on ground and the accumulated fallDistance was large this tick, it's "done".
        return p.onGround && p.fallDistance >= minFall;
    }

    /** True if player stands over a deep air column (about to drop). */
    static boolean playerOverDeepAir(Level level, Player p, int minDrop) {
        if (level == null || p == null) return false;
        int bx = MathHelper.floor(p.x), by = clampY(level, MathHelper.floor(p.y)), bz = MathHelper.floor(p.z);
        return isDeepAirBelow(level, bx, by, bz, minDrop);
    }

    // =============================================================================================
    // INTERNALS
    // =============================================================================================

    /** Validate an edge and return a target just over the lip if it’s a damaging pit. */
    private static PitTarget checkEdge(Level level, int x, int y, int z, int dx, int dz, int minDrop) {
        // Current must be walkable (air) with solid directly below.
        int here = level.getTile(x, clampY(level, y), z);
        int belowHere = level.getTile(x, clampY(level, y-1), z);
        if (!(here == 0 && isSolidNonLiquid(level, belowHere))) return null;

        // Next cell (beyond edge)
        int nx = x + dx, nz = z + dz;
        // Must be air here, and an air column below of at least minDrop that ends on SOLID and NON-liquid.
        int run = 0;
        for (int k = 0; k < minDrop + 8; k++) { // +buffer so we can verify bottom
            int yy = clampY(level, y - 1 - k);
            int id = level.getTile(nx, yy, nz);
            if (id == 0) { run++; continue; }
            // Hit non-air: require it to be solid, not liquid; and only accept if we already have >= minDrop air
            if (run >= minDrop && isSolidNonLiquid(level, id)) {
                // Good pit. Compute a target slightly inside the air beyond the edge so step crosses it.
                float tx = nx + 0.5f + dx * 0.10f; // a small push in
                float tz = nz + 0.5f + dz * 0.10f;
                float ty = y + 0.05f;              // same Y (edge height), tiny lift for smoothness
                return new PitTarget(tx, ty, tz, run);
            }
            // Non-air but unsuitable: stop
            return null;
        }
        // Column never closed with floor: treat as bad (void/too deep)
        return null;
    }

    /** Find a walkable Y (air with solid under) near a vertical band. */
    private static int findWalkableY(Level level, int x, int z, int yTop, int yBottom) {
        if (yTop < yBottom) { int t=yTop; yTop=yBottom; yBottom=t; }
        for (int y = yTop; y >= yBottom; y--) {
            int here  = level.getTile(x, clampY(level, y), z);
            int below = level.getTile(x, clampY(level, y - 1), z);
            if (here == 0 && isSolidNonLiquid(level, below)) return clampY(level, y);
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isSolidNonLiquid(Level level, int id) {
        if (id <= 0) return false;
        LiquidType lt = Block.blocks[id].getLiquidType();
        if (lt != LiquidType.NOT_LIQUID) return false;
        return Block.blocks[id].isSolid();
    }

    private static boolean isDeepAirBelow(Level level, int x, int y, int z, int depth) {
        int run = 0;
        for (int i = 1; i <= depth + 2; i++) {
            int yy = clampY(level, y - i);
            int id = level.getTile(x, yy, z);
            if (id == 0) run++; else break;
        }
        return run >= depth;
    }

    private static int clampY(Level level, int y) {
        if (y < 0) return 0;
        if (y >= level.depth) return level.depth - 1;
        return y;
    }
}
