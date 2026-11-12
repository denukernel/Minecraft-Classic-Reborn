package net.classicremastered.minecraft.level.structure;

import java.util.Arrays;
import java.util.Random;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.ai.BasicAI;

public final class HouseStructure implements Structure {

    @Override
    public String id() {
        return "house_basic_v1";
    }

    // Footprint (outer): 7×7×5 Interior: 5×5×3
    private static final int W = 7, D = 7, H = 5;

    private static boolean isLiquid(int id) {
        return id == Block.WATER.id || id == Block.STATIONARY_WATER.id || id == Block.LAVA.id
                || id == Block.STATIONARY_LAVA.id;
    }

    /**
     * Highest non-liquid solid y at (x,z). Returns -1 if lava column encountered.
     */
    private static int sampleSolidY(Level level, int x, int z) {
        int y = level.getHighestTile(x, z);
        if (y <= 0)
            return -1;
        while (y > 0) {
            int id = level.getTile(x, y, z);
            if (id == 0) {
                y--;
                continue;
            } // air above
            if (id == Block.STATIONARY_LAVA.id || id == Block.LAVA.id)
                return -1;
            if (id == Block.WATER.id || id == Block.STATIONARY_WATER.id) {
                y--;
                continue;
            }
            return y; // solid
        }
        return -1;
    }

    /** Median “pad” level from the whole footprint; we’ll build on padY+1. */
    private static int computeTargetY(Level level, int x, int z) {
        int[] ys = new int[W * D];
        int n = 0;
        for (int dx = 0; dx < W; dx++) {
            for (int dz = 0; dz < D; dz++) {
                int y = sampleSolidY(level, x + dx, z + dz);
                if (y < 0)
                    return -1; // lava or invalid
                ys[n++] = y;
            }
        }
        Arrays.sort(ys, 0, n);
        return ys[n / 2] + 1; // build on top of the pad
    }

    private static final class FootprintStats {

        int maxUp, maxDown, maxWater; // relative to padY
        boolean lava;
    }

    private static FootprintStats analyze(Level level, int x, int targetY, int z) {
        FootprintStats s = new FootprintStats();
        int padY = targetY - 1;
        for (int dx = 0; dx < W; dx++) {
            for (int dz = 0; dz < D; dz++) {
                int wx = x + dx, wz = z + dz;
                int solid = sampleSolidY(level, wx, wz);
                if (solid < 0) {
                    s.lava = true;
                    return s;
                }
                int diff = solid - padY;
                if (diff > s.maxUp)
                    s.maxUp = diff;
                if (-diff > s.maxDown)
                    s.maxDown = -diff;

                int aboveSurface = level.getTile(wx, solid + 1, wz);
                if (aboveSurface == Block.WATER.id || aboveSurface == Block.STATIONARY_WATER.id) {
                    s.maxWater = Math.max(s.maxWater, 1);
                }
            }
        }
        return s;
    }

    // ---- Placement gates -----------------------------------------------------

    @Override
    public boolean canPlace(Level level, int x, int yIgnored, int z) {
        final int targetY = computeTargetY(level, x, z);
        // IMPORTANT: use level.depth (vertical), not level.height (Z extent)
        if (targetY < 1 || targetY + H - 1 >= level.depth)
            return false;

        final FootprintStats s = analyze(level, x, targetY, z);
        if (s.lava)
            return false;
        if (s.maxWater > 0)
            return false; // no water footprints

        return s.maxUp <= 2 && s.maxDown <= 2;
    }

    // ---- Generation (terraform + build + NPCs) -------------------------------

    @Override
    public boolean generate(Level level, int x, int yIgnored, int z, Random rand) {
        int targetY = computeTargetY(level, x, z);
        if (targetY < 1 || targetY + H - 1 >= level.depth)
            return false; // depth, not height
        FootprintStats footprint = analyze(level, x, targetY, z);
        if (footprint.lava || footprint.maxWater > 0 || footprint.maxUp > 2 || footprint.maxDown > 2)
            return false;
        int padY = targetY - 1;

        // Terraform: fill upward to pad with cobble; clear build volume
        for (int dx = 0; dx < W; dx++) {
            for (int dz = 0; dz < D; dz++) {
                int wx = x + dx, wz = z + dz;
                int solid = sampleSolidY(level, wx, wz);
                if (solid < 0)
                    return false;
                for (int y = solid + 1; y <= padY; y++) {
                    level.setTile(wx, y, wz, Block.COBBLESTONE.id);
                }
                for (int y = targetY; y < targetY + H; y++) {
                    level.setTile(wx, y, wz, 0);
                }
            }
        }

        // Floor
        for (int dx = 0; dx < W; dx++)
            for (int dz = 0; dz < D; dz++)
                level.setTile(x + dx, padY, z + dz, Block.COBBLESTONE.id);

        // Walls
        for (int dy = 1; dy < H - 1; dy++) {
            for (int dx = 0; dx < W; dx++) {
                for (int dz = 0; dz < D; dz++) {
                    boolean wall = (dx == 0 || dx == W - 1 || dz == 0 || dz == D - 1);
                    if (!wall)
                        continue;
                    if (dy == 2 && !(dz == D / 2 && dx == 0)) {
                        level.setTile(x + dx, padY + dy, z + dz, Block.GLASS.id);
                    } else {
                        level.setTile(x + dx, padY + dy, z + dz, Block.WOOD.id);
                    }
                }
            }
        }

        // Roof
        for (int dx = 0; dx < W; dx++)
            for (int dz = 0; dz < D; dz++)
                level.setTile(x + dx, padY + H - 1, z + dz, Block.WOOD.id);

        // Door gap
        int doorX = x + W / 2;
        int doorZ = z;
        level.setTile(doorX, padY + 1, doorZ, 0);
        level.setTile(doorX, padY + 2, doorZ, 0);

        var occupants =
                new java.util.ArrayList<net.classicremastered.minecraft.level.GeneratedOccupant>();
        net.classicremastered.minecraft.level.GeneratedOccupant.offerHouse(occupants, rand.nextLong(),
                x + W / 2, padY + 1, z + D / 2);
        for (var occupant : occupants) occupant.spawn(level);
        return true;
    }
}
