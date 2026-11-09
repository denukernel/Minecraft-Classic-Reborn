package net.classicremastered.minecraft.mob.ai.golem;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.golem.FollowPlayerGoal;

/** Gentle following with a stopping band and normal collision/jump physics. */
public final class ForestGolemFollowAI extends BasicAI {

    public static final float FOLLOW_RANGE = 32f;
    public boolean following;

    public ForestGolemFollowAI() {
        runSpeed = 0.7f;
        walkSpeed = 0.08f;
        suppressRandomJump = true;
    }

    @Override
    public void tick(Level level, Mob mob) {
        // A companion should not use BasicAI random distance despawning.
        noActionTime = 0;
        super.tick(level, mob);
    }

    @Override
    protected void update() {
    }

    @Override
    protected void registerGoals(net.classicremastered.minecraft.mob.ai.goal.GoalSelector selector) {
        selector.addGoal(0, new FollowPlayerGoal(this));
    }

    @Override
    protected boolean usesWanderNavigation() {
        return false;
    }
}
