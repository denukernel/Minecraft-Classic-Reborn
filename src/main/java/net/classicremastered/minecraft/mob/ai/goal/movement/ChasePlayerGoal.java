package net.classicremastered.minecraft.mob.ai.goal.movement;

import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.movement.PathChaseAI;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class ChasePlayerGoal extends MobGoal<PathChaseAI> {

    public ChasePlayerGoal(PathChaseAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        net.classicremastered.minecraft.mob.Mob target =
                net.classicremastered.minecraft.mob.ai.combat.HostileTargets.nearestForPursuit(
                        ai.mob, ai.chaseRange, ai.attackTarget);
        ai.setPursuitTarget(target);
        if (target == null
                || target.removed
                || target.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(target)) {
            ai.xxa = ai.yya = ai.yRotA = 0;
            ai.jumping = false;
            return;
        }
        ai.navigateTo(target.x, target.bb.y0, target.z, ai.runSpeed);
        ai.mob.xRot = ai.defaultLookAngle;
    }
}
