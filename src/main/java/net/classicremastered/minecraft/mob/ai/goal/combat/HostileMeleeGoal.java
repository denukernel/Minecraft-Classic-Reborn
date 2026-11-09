package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public final class HostileMeleeGoal extends MobGoal<BasicAttackAI> {

    public HostileMeleeGoal(BasicAttackAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        ai.tickCombatGoals();
    }

}
