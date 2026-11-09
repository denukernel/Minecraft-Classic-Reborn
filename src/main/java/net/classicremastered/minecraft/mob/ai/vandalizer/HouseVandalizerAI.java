package net.classicremastered.minecraft.mob.ai.vandalizer;

import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.vandalizer.VandalizeHouseGoal;

public final class HouseVandalizerAI extends BasicAttackAI {

    public HouseVandalizerAI() {
        runSpeed = .72f;
        damage = 4;
    }

    @Override
    protected boolean usesPlannedWandering() {
        return true;
    }

    @Override
    protected void update() {
    }

    @Override
    protected void registerGoals(net.classicremastered.minecraft.mob.ai.goal.GoalSelector selector) {
        selector.addGoal(0, new VandalizeHouseGoal(this));
    }

    public void defaultUpdate() {
        super.update();
    }

}
