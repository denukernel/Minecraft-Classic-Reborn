package net.classicremastered.minecraft.mob.ai.zombie;

import net.classicremastered.minecraft.mob.ZombieBuilder;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;

public final class ZombieBuilderAI extends BasicAttackAI {

    public ZombieBuilderAI() {
        super(true, true);
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
