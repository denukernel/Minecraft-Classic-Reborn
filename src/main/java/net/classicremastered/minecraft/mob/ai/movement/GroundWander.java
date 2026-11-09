package net.classicremastered.minecraft.mob.ai.movement;

import java.util.Random;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.path.PathNavigator;
import net.classicremastered.minecraft.path.Pathfinder;

/**
 * Continuous bounded wandering. Randomness chooses destinations, never delays
 * or per-tick steering.
 */
public final class GroundWander {

    public final Level level;
    private final PathNavigator navigator;
    private int walkTicks, stalledTicks;
    private float goalX, goalY, goalZ, lastX, lastZ;

    public GroundWander(Level level, Random random) {
        this.level = level;
        navigator = new PathNavigator(level);
    }

    public PathNavigator.Result steer(Mob mob, Random random) {
        if (!navigator.hasGoal() && !chooseGoal(mob, random)) {
            return PathNavigator.Result.idle();
        }
        float dx = mob.x - lastX, dz = mob.z - lastZ;
        stalledTicks = dx * dx + dz * dz < 0.000025f ? stalledTicks + 1 : 0;
        lastX = mob.x;
        lastZ = mob.z;
        float gx = goalX - mob.x, gz = goalZ - mob.z;
        if (--walkTicks <= 0 || stalledTicks > 45
                || (gx * gx + gz * gz < 1.5f * 1.5f && Math.abs(mob.bb.y0 - goalY) < 0.5f)) {
            // Extend the walk before braking at its endpoint. Keep the old route if
            // a new destination is temporarily unavailable, rather than adding a pause.
            if (!chooseGoal(mob, random) && stalledTicks > 45)
                navigator.clearGoal();
        }
        return navigator.steer(mob);
    }

    private boolean chooseGoal(Mob mob, Random random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            // Prefer a continued walk. Broaden the search when facing an obstacle.
            float arc = attempt < 4 ? 50f : attempt < 6 ? 140f : 360f;
            float angle = (mob.yRot + (random.nextFloat() - 0.5f) * arc) * (float) Math.PI / 180f;
            float distance = 4f + random.nextFloat() * 3f;
            int x = Pathfinder.cell(mob.x - (float) Math.sin(angle) * distance, mob.bbWidth);
            int z = Pathfinder.cell(mob.z + (float) Math.cos(angle) * distance, mob.bbWidth);
            float feet = Math.round(mob.bb.y0 * 2) / 2f;
            for (float dy : new float[]{0, 0.5f, -0.5f, 1, -1, 1.5f, -1.5f, 2, -2, 2.5f, -2.5f, 3, -3, 3.5f, -3.5f, 4,
                    -4}) {
                var route = Pathfinder.findPath(level, mob, x, feet + dy, z, 256);
                if (route == null || route.size() < 4)
                    continue;
                goalX = x + Pathfinder.offset(mob.bbWidth);
                goalY = feet + dy;
                goalZ = z + Pathfinder.offset(mob.bbWidth);
                navigator.clearGoal();
                navigator.setGoal(goalX, goalY, goalZ);
                walkTicks = 220;
                stalledTicks = 0;
                lastX = mob.x;
                lastZ = mob.z;
                return true;
            }
        }
        return false;
    }
}
