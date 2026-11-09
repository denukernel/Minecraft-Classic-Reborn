package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public final class AvoidTntGoal extends MobGoal<BasicAttackAI> {

    public AvoidTntGoal(BasicAttackAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.isTntAvoidanceEnabled() && ai.threats().avoidTicks > 0;
    }

    @Override
    public void tick() {
        ai.evading = true;
        ai.threats().avoidTicks--;
        ai.running = false;
        ai.yya = -0.6f;
        // small yaw wander while backing up
        ai.mob.yRot += (ai.random.nextFloat() - 0.5f) * 10.0f;
        return;
    }

}
