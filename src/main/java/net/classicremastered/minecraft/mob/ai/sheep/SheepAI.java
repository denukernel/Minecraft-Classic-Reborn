package net.classicremastered.minecraft.mob.ai.sheep;

import net.classicremastered.minecraft.mob.Sheep;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.GoalSelector;
import net.classicremastered.minecraft.mob.ai.goal.movement.DefaultMovementGoal;
import net.classicremastered.minecraft.mob.ai.goal.sheep.SheepGrazeGoal;

public final class SheepAI extends BasicAI {

    @Override
    protected boolean navigationAllowed() {
        return !(mob instanceof Sheep sheep && sheep.grazing) && super.navigationAllowed();
    }

    @Override
    protected void update() {
    }

    @Override
    protected void registerGoals(GoalSelector selector) {
        selector.addGoal(0, new SheepGrazeGoal(this));
        selector.addGoal(1, new DefaultMovementGoal(this));
    }
}
