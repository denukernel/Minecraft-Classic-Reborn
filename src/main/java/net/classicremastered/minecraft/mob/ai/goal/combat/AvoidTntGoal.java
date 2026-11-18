package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.path.Pathfinder;

public final class AvoidTntGoal extends MobGoal<BasicAttackAI> {
    private static final int[] ESCAPE_ANGLES = {0, -45, 45, -85, 85};
    private static final int[] ROUTE_ANGLES = {0, -45, 45, -90, 90, -135, 135, 180};
    private static final float[] REFUGE_RADII = {8.5f, 6, 4};
    private Entity trackedTnt;
    private Vec3D refuge;
    private int replanTicks;

    public AvoidTntGoal(BasicAttackAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart()
                && (ai.threats().primedTnt != null
                        || trackedTnt != null
                                && !trackedTnt.removed
                                && trackedTnt.level == ai.level);
    }

    @Override
    public void stop() {
        if (trackedTnt != null) ai.clearNavigation();
        trackedTnt = null;
        refuge = null;
        replanTicks = 0;
    }

    @Override
    public void tick() {
        ai.evading = true;
        ai.running = false;
        ai.jumping = false;
        ai.xxa = ai.yya = ai.yRotA = 0;
        var threat = ai.threats().primedTnt;
        if (ai.mob instanceof StickFigure) {
            retreatAlongRoute(threat != null ? threat : trackedTnt);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(threat.x - ai.mob.x, ai.mob.z - threat.z));
        for (int offset : ESCAPE_ANGLES) {
            float heading = yaw + offset;
            double angle = Math.toRadians(heading);
            float x = ai.mob.x - (float) Math.sin(angle) * .7f;
            float z = ai.mob.z + (float) Math.cos(angle) * .7f;
            if (Pathfinder.canWalkStraight(ai.level, ai.mob, x, ai.mob.bb.y0, z)) {
                ai.mob.yRot = heading;
                ai.yya = .65f;
                return;
            }
        }
        ai.mob.xd *= .5f;
        ai.mob.zd *= .5f;
    }

    private void retreatAlongRoute(Entity threat) {
        if (trackedTnt != threat) {
            trackedTnt = threat;
            refuge = null;
            replanTicks = 0;
            ai.clearNavigation();
        }
        ai.running = true;
        if (--replanTicks <= 0) {
            replanTicks = 8;
            if (refuge == null
                    || ai.mob.horizontalCollision
                    || !Pathfinder.canStandAt(ai.level, ai.mob, refuge.x, refuge.y, refuge.z)
                    || horizontalDistanceSquared(refuge.x, refuge.z, threat) < 36) {
                refuge = findRefuge(threat);
            }
        }
        if (refuge != null) ai.navigateTo(refuge.x, refuge.y, refuge.z, ai.runSpeed);
    }

    private Vec3D findRefuge(Entity threat) {
        var mob = ai.mob;
        double heading = Math.atan2(mob.z - threat.z, mob.x - threat.x);
        float offset = Pathfinder.offset(mob.bbWidth);
        float currentDistance = horizontalDistanceSquared(mob.x, mob.z, threat);
        float minimumDistance =
                CombatRules.difficulty(ai.level).compareTo(Difficulty.HARD) >= 0
                        ? Math.min(9, Math.max(0, currentDistance - 1))
                        : 0;
        for (float radius : REFUGE_RADII) {
            Vec3D best = null;
            int shortest = Integer.MAX_VALUE;
            for (int degrees : ROUTE_ANGLES) {
                double angle = heading + Math.toRadians(degrees);
                float x = threat.x + radius * (float) Math.cos(angle);
                float z = threat.z + radius * (float) Math.sin(angle);
                var path = Pathfinder.findChasePath(ai.level, mob, x, mob.bb.y0, z, 160);
                if (path == null || path.size() < 2 || path.size() >= shortest) continue;
                var end = path.get(path.size() - 1);
                float endX = end.x + offset, endZ = end.z + offset;
                if (horizontalDistanceSquared(endX, endZ, threat)
                        < Math.max(currentDistance + 1, (radius - 1) * (radius - 1))) continue;
                if (minimumDistance > 0
                        && path.stream()
                                .skip(1)
                                .anyMatch(
                                        node ->
                                                horizontalDistanceSquared(
                                                                node.x + offset,
                                                                node.z + offset,
                                                                threat)
                                                        < minimumDistance)) continue;
                best = new Vec3D(endX, end.feetY, endZ);
                shortest = path.size();
            }
            if (best != null) return best;
        }
        return null;
    }

    private static float horizontalDistanceSquared(float x, float z, Entity threat) {
        float dx = x - threat.x, dz = z - threat.z;
        return dx * dx + dz * dz;
    }
}
