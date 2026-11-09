// file: src/com/mojang/minecraft/mob/ai/SkeletonArrowAI.java
package net.classicremastered.minecraft.mob.ai.skeleton;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.skeleton.RangedArrowGoal;

public class SkeletonArrowAI extends BasicAI {

    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector rangedGoals;
    public float preferredMin = 4.0F;
    public float preferredMax = 16.0F;
    public float walkSpeed = 0.025F;

    public int windupTicks = 8;
    public int cooldownTicks = 40;
    public float arrowSpeed = 1.2F;

    public int windup = 0;
    public int cooldown = 0;

    public void tick(Level level, Mob mob) {
        super.tick(level, mob);
        if (rangedGoals == null)
            rangedGoals = createGoalSelector(new RangedArrowGoal(this));
        rangedGoals.tick();
    }

}
