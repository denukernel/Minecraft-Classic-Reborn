package net.classicremastered.minecraft.mob.ai.zombie;

import net.classicremastered.minecraft.mob.ZombieBuilder;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;

public final class ZombieBuilderAI extends BasicAttackAI {

    public ZombieBuilderAI() {
        super(true, true);
    }

    public void holdForConstruction() {
        holdPosition(mob.yRot);
    }

    public void moveToBuiltStep(float x, float feet, float z) {
        float dx = x - mob.x, dz = z - mob.z;
        float distance = (float) Math.hypot(dx, dz);
        holdPosition(distance > .01f ? (float) Math.toDegrees(Math.atan2(dz, dx)) - 90 : mob.yRot);
        // Rise vertically until the feet clear the tread, then move onto its center.
        float speed = mob.bb.y0 + .02f < feet ? 0 : Math.min(.24f, distance);
        mob.xd = distance > .01f ? dx / distance * speed : 0;
        mob.zd = distance > .01f ? dz / distance * speed : 0;
    }

    @Override
    protected void update() {
        super.update();
        if (!(mob instanceof ZombieBuilder builder))
            return;
        if (attackTarget != null && !builder.canFollow(attackTarget))
            attackTarget = null;
        var turret = builder.headTarget(this);
        if (turret != null) {
            boolean close = builder.distanceToSqr(turret) <= 4f && hasLineOfSight(builder, turret);
            builder.tearAtHead(turret, close);
            if (close) {
                float yaw = (float) Math
                        .toDegrees(Math.atan2(builder.x - turret.x, turret.z - builder.z));
                holdPosition(yaw);
                builder.xd = builder.zd = 0f;
            } else {
                navigateTo(turret.x, turret.bb.y0, turret.z, runSpeed);
            }
        } else if (builder.hasStolenHead()) {
            if (builder.fireStolenHead(this))
                holdPosition(builder.yRot);
        } else {
            builder.buildStepsGoal().steer(this);
        }
    }
}
