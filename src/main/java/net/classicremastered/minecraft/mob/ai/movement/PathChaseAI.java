package net.classicremastered.minecraft.mob.ai.movement;

import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.movement.ChasePlayerGoal;

/** Player following through the same grounded navigator used by combat mobs. */
public class PathChaseAI extends BasicAI {

    public static final long serialVersionUID = 0L;
    public float chaseRange = 32f;
    @Override
    protected void update() {
    }

    @Override
    protected void registerGoals(net.classicremastered.minecraft.mob.ai.goal.GoalSelector selector) {
        selector.addGoal(0, new ChasePlayerGoal(this));
    }

    @Override
    protected boolean usesWanderNavigation() {
        return false;
    }
}
