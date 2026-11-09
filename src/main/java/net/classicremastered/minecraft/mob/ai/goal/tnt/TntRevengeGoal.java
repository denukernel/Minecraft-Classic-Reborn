package net.classicremastered.minecraft.mob.ai.goal.tnt;

import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.tnt.ThrowTntAI;
import net.classicremastered.util.MathHelper;

public final class TntRevengeGoal extends MobGoal<ThrowTntAI> {

    public TntRevengeGoal(ThrowTntAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.combatActive && ai.revengeTicks > 0 && ai.revengeTarget != null
                && !ai.revengeTarget.removed;
    }

    @Override
    public void tick() {
        float runRevenge = ai.walkSpeed * 1.4F * ai.revengeSprintMultiplier;
        float rdx = ai.revengeTarget.x - ai.mob.x;
        float rdz = ai.revengeTarget.z - ai.mob.z;
        float rDist = MathHelper.sqrt(rdx * rdx + rdz * rdz);

        ai.accelerateTo(ai.revengeTarget, runRevenge);

        if (rDist <= ai.revengeRange) {
            if (ai.revengeTimer > 0)
                ai.revengeTimer--;
            else {
                ai.revengeTarget.hurt(ai.mob, 1); // -1 health
                ai.mob.attackTime = 5;
                ai.revengeTimer = ai.revengeIntervalTicks;
            }
        }

        ai.revengeTicks--;
        // while raging, skip other behaviors
        return;
    }

}
