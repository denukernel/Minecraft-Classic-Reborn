package net.classicremastered.minecraft.mob.ai.goal.skeleton;

import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.skeleton.SkeletonAI;
import net.classicremastered.minecraft.path.Pathfinder;

public final class SkeletonStrafeGoal extends MobGoal<SkeletonAI> {

    public SkeletonStrafeGoal(SkeletonAI ai) {
        super(ai);
    }

    @Override
    public void tick() {

        if (ai.attackTarget == null
                || ai.attackTarget.removed
                || ai.mob.isInWater()
                || !ai.groundNavigationAllowed()
                || !ai.hasLineOfSight(ai.mob, ai.attackTarget)) return;
        float dx = ai.attackTarget.x - ai.mob.x, dz = ai.attackTarget.z - ai.mob.z;
        float distance = (float) Math.hypot(dx, dz);
        if (distance > 20 || Math.abs(ai.attackTarget.bb.y0 - ai.mob.bb.y0) > 5) return;
        if (Pathfinder.waterBetween(
                ai.level, ai.mob, ai.attackTarget.x, ai.attackTarget.bb.y0, ai.attackTarget.z))
            return;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float turn = yaw - ai.mob.yRot;
        while (turn > 180) turn -= 360;
        while (turn < -180)
            turn += 360;
        ai.mob.yRot += Math.max(-15, Math.min(15, turn));
        ai.yRotA = 0;
        ai.running = false;
        ai.jumping = false;
        if (++ai.strafeTicks >= 35) {
            ai.strafeTicks = 0;
            if (ai.random.nextBoolean())
                ai.strafeDirection = -ai.strafeDirection;
        }
        float side = ai.strafeDirection * .5f,
                forward = distance < 7 ? -.65f : distance > 12 ? .4f : 0;
        double angle = Math.toRadians(ai.mob.yRot);
        float sx = (float) Math.cos(angle), sz = (float) Math.sin(angle);
        float fx = -sz, fz = sx;
        if (!safeStep(sx * side, sz * side)) {
            ai.strafeDirection = -ai.strafeDirection;
            side = ai.strafeDirection * .5f;
        }
        if (!safeStep(sx * side, sz * side))
            side = 0;
        if (!safeStep(fx * forward, fz * forward)) forward = 0;
        if (!safeStep(sx * side + fx * forward, sz * side + fz * forward)) forward = 0;
        ai.rangedStance = side != 0 || forward != 0;
        ai.xxa = side;
        ai.yya = forward;
        ai.mob.xd *= .8f;
        ai.mob.zd *= .8f;
    }

    private boolean safeStep(float dx, float dz) {
        if (dx == 0 && dz == 0) return true;
        float length = (float) Math.hypot(dx, dz);
        float x = ai.mob.x + dx / length * .7f, z = ai.mob.z + dz / length * .7f;
        return Pathfinder.canWalkStraight(ai.level, ai.mob, x, ai.mob.bb.y0, z);
    }
}
