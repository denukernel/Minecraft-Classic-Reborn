package net.classicremastered.minecraft.path;

import java.util.*;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.*;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.phys.AABB;

/** Bounded surface-water search for a body-clear, reachable bank, including side exits. */
public final class ShoreNavigator {
    private ShoreNavigator() {
    }
    public record Step(float x, float feetY, float z) {
    }

    private record Cell(int x, int z, int surface, Cell parent, int distance) {
    }
    private static boolean loaded(Level level, int x, int z) {
        if (level instanceof LevelInfiniteTerrain t)
            return t.chunks().isChunkLoaded(x >> 4, z >> 4);
        if (level instanceof LevelInfiniteFlat f)
            return f.chunks.isChunkLoaded(x >> 4, z >> 4);
        return level.isInBounds(x, 1, z);
    }

    private static int surface(Level level, Mob mob, int x, int z, int near) {
        if (!loaded(level, x, z))
            return -1;
        for (int y = Math.min(level.depth - 2, near + 4); y >= Math.max(1, near - 5); y--)
            if (level.isWater(x, y, z) && !level.isWater(x, y + 1, z)) {
                float half = mob.bbWidth * .5f - .002f, px = x + .5f, pz = z + .5f, feet = y + .4f;
                if (level.getCubes(new AABB(px - half, feet, pz - half, px + half, feet + mob.bbHeight, pz + half))
                        .isEmpty())
                    return y + 1;
            }
        return -1;
    }

    public static List<Step> find(Level level, Mob mob, Entity target) {
        int sx = (int) Math.floor(mob.x), sz = (int) Math.floor(mob.z);
        int startSurface = surface(level, mob, sx, sz, (int) Math.floor(mob.bb.y0));
        if (startSurface < 0)
            return List.of();
        GroundClearance dry = new GroundClearance(level, mob.bbWidth, mob.bbHeight);
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Cell start = new Cell(sx, sz, startSurface, null, 0), best = null;
        Step exit = null;
        float bestScore = Float.MAX_VALUE;
        queue.add(start);
        seen.add(Level.getCoordKey(sx, 0, sz));
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && seen.size() <= 625) {
            Cell current = queue.remove();
            for (int[] dir : directions) {
                int x = current.x + dir[0], z = current.z + dir[1];
                if (Math.abs(x - sx) > 12 || Math.abs(z - sz) > 12 || !loaded(level, x, z))
                    continue;
                int water = surface(level, mob, x, z, current.surface);
                if (water >= 0 && Math.abs(water - current.surface) <= 1) {
                    if (seen.add(Level.getCoordKey(x, 0, z)))
                        queue.add(new Cell(x, z, water, current, current.distance + 1));
                    continue;
                }
                for (float rise : new float[]{0, .5f, 1, -.5f, -1}) {
                    float feet = current.surface + rise;
                    if (!dry.supportedAt(x + .5f, feet, z + .5f) || !dry.bodyLineClear(current.x + .5f,
                            Math.max(feet, current.surface), current.z + .5f, x + .5f, z + .5f))
                        continue;
                    float score = current.distance + 1
                            + (float) Math.hypot(x + .5f - target.x, z + .5f - target.z) * 1.5f;
                    if (score < bestScore) {
                        bestScore = score;
                        best = current;
                        exit = new Step(x + .5f, feet, z + .5f);
                    }
                    break;
                }
            }
        }
        if (best == null)
            return List.of();
        LinkedList<Step> route = new LinkedList<>();
        for (Cell node = best; node != null; node = node.parent)
            route.addFirst(new Step(node.x + .5f, node.surface - .6f, node.z + .5f));
        route.add(exit);
        return route;
    }
}
