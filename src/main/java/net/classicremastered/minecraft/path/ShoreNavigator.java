package net.classicremastered.minecraft.path;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.*;
import net.classicremastered.minecraft.mob.Mob;

import java.util.*;

/** Bounded surface-water search for a body-clear, reachable bank, including side exits. */
public final class ShoreNavigator {
    private ShoreNavigator() {}

    public record Step(float x, float feetY, float z) {}

    private record Cell(int x, int z, int surface, Cell parent, float distance) {}

    private static boolean loaded(Level level, int x, int z) {
        if (level instanceof LevelInfiniteTerrain t)
            return t.chunks().isChunkLoaded(x >> 4, z >> 4);
        if (level instanceof LevelInfiniteFlat f) return f.chunks.isChunkLoaded(x >> 4, z >> 4);
        return level.isInBounds(x, 1, z);
    }

    private static int surface(Level level, GroundClearance swim, int x, int z, int near) {
        if (!loaded(level, x, z)) return -1;
        int top = Math.min(level.depth - 2, near + 4);
        while (top < level.depth - 2 && level.isWater(x, top, z)) top++;
        for (int y = top; y >= Math.max(0, near - 5); y--) {
            if (level.isWater(x, y, z)
                    && !level.isWater(x, y + 1, z)
                    && swim.bodyClear(x + swim.offset, y + .4f, z + swim.offset)) return y + 1;
        }
        return -1;
    }

    public static List<Step> find(Level level, Mob mob, Entity target) {
        return find(level, mob, target, null);
    }

    public static List<Step> find(Level level, Mob mob, Entity target, Step blockedExit) {
        return find(level, mob, target, blockedExit, false);
    }

    public static List<Step> findWater(Level level, Mob mob, Entity target) {
        return find(level, mob, target, null, true);
    }

    private static List<Step> find(
            Level level, Mob mob, Entity target, Step blockedExit, boolean waterGoal) {
        int sx = Pathfinder.cell(mob.x, mob.bbWidth), sz = Pathfinder.cell(mob.z, mob.bbWidth);
        int near = (int) Math.floor(mob.bb.y0);
        float standingHeight =
                mob instanceof net.classicremastered.minecraft.mob.StickFigure
                        ? net.classicremastered.minecraft.mob.StickFigure.STANDING_HEIGHT
                        : mob.bbHeight;
        GroundClearance dry = new GroundClearance(level, mob.bbWidth, standingHeight);
        GroundClearance swim = new GroundClearance(level, mob.bbWidth, mob.bbHeight, true);
        float offset = swim.offset;
        float bankInset = Math.max(0, mob.bbWidth * .5f - offset + .05f);
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Cell best = null;
        Step exit = null;
        float bestScore = Float.MAX_VALUE;
        for (int x = sx - 1; x <= sx + 1; x++) {
            for (int z = sz - 1; z <= sz + 1; z++) {
                int startSurface = surface(level, swim, x, z, near);
                float feet = Math.max(mob.bb.y0, startSurface - .6f);
                if (startSurface < 0
                        || !swim.bodyLineClear(mob.x, feet, mob.z, x + offset, z + offset))
                    continue;
                float approach = (float) Math.hypot(x + offset - mob.x, z + offset - mob.z);
                queue.add(new Cell(x, z, startSurface, null, approach));
                seen.add(Level.getCoordKey(x, 0, z));
            }
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int radius = 24;
        int targetX = waterGoal ? Pathfinder.cell(target.x, mob.bbWidth) : 0;
        int targetZ = waterGoal ? Pathfinder.cell(target.z, mob.bbWidth) : 0;
        while (!queue.isEmpty()) {
            Cell current = queue.remove();
            if (waterGoal && current.x == targetX && current.z == targetZ) {
                best = current;
                break;
            }
            if (target == null && current.distance + 1 >= bestScore) continue;
            for (int[] dir : directions) {
                int x = current.x + dir[0], z = current.z + dir[1];
                if (Math.abs(x - sx) > radius || Math.abs(z - sz) > radius || !loaded(level, x, z))
                    continue;
                float px = x + offset, pz = z + offset;
                int water = surface(level, swim, x, z, current.surface);
                if (water >= 0 && Math.abs(water - current.surface) <= 1) {
                    long key = Level.getCoordKey(x, 0, z);
                    if (!seen.contains(key)
                            && swim.bodyLineClear(
                                    current.x + offset,
                                    Math.max(water, current.surface) - .6f,
                                    current.z + offset,
                                    px,
                                    pz)) {
                        seen.add(key);
                        queue.add(new Cell(x, z, water, current, current.distance + 1));
                    }
                    continue;
                }
                if (waterGoal) continue;
                // Land far enough past the lip that the whole footprint clears the water.
                px += dir[0] * bankInset;
                pz += dir[1] * bankInset;
                for (float rise : new float[] {0, .5f, 1, -.5f, -1}) {
                    float feet = current.surface + rise;
                    if (blockedExit != null
                            && blockedExit.x() == px
                            && blockedExit.z() == pz
                            && blockedExit.feetY() == feet) continue;
                    if (!dry.supportedAt(px, feet, pz)
                            || !swim.bodyLineClear(
                                    current.x + offset,
                                    Math.max(feet, current.surface),
                                    current.z + offset,
                                    px,
                                    pz)) continue;
                    float score = current.distance + 1;
                    if (target != null)
                        score += (float) Math.hypot(px - target.x, pz - target.z) * 1.5f;
                    if (score < bestScore) {
                        bestScore = score;
                        best = current;
                        exit = new Step(px, feet, pz);
                    }
                    break;
                }
            }
        }
        if (best == null) return List.of();
        LinkedList<Step> route = new LinkedList<>();
        for (Cell node = best; node != null; node = node.parent)
            route.addFirst(new Step(node.x + offset, node.surface - .6f, node.z + offset));
        if (!waterGoal) route.add(exit);
        return route;
    }
}
