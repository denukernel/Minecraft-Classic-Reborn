package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

/**
 * Builds supported stone steps along its approach rather than along its gaze.
 */
public class ZombieBuilder extends Zombie {

    public static final long serialVersionUID = 0L;
    public boolean poseBreaking, posePlacing;

    private transient net.classicremastered.minecraft.mob.ai.goal.zombie.BuildStepsGoal building;
    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector construction;
    public ZombieBuilder(Level level, float x, float y, float z) {
        super(level, x, y, z);
        ai = new net.classicremastered.minecraft.mob.ai.zombie.ZombieBuilderAI();
    }

    public net.classicremastered.minecraft.mob.ai.goal.zombie.BuildStepsGoal buildStepsGoal() {
        if (building == null)
            building = new net.classicremastered.minecraft.mob.ai.goal.zombie.BuildStepsGoal(this);
        return building;
    }

    public boolean isBuilderBreaking() {
        return poseBreaking;
    }

    public boolean isBuilderPlacing() {
        return posePlacing;
    }

    @Override
    public void tick() {
        super.tick();
        if (construction == null)
            construction = new net.classicremastered.minecraft.mob.ai.goal.GoalSelector(buildStepsGoal());
        construction.tick();
    }
}
