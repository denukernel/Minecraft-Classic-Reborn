package net.classicremastered.minecraft.mob.ai.skeleton;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.GoalSelector;
import net.classicremastered.minecraft.mob.ai.goal.skeleton.SkeletonBowGoal;
import net.classicremastered.minecraft.mob.ai.goal.skeleton.SkeletonStrafeGoal;

public final class SkeletonAI extends BasicAttackAI {

    public SkeletonAI() {
        super(true, true);
    }

    public static final long serialVersionUID = 0L;
    public boolean rangedStance;
    public int strafeTicks;
    public float strafeDirection = 1;
    private transient GoalSelector strafeGoals, bowGoals;
    @Override
    public void tick(Level level, Mob mob) {
        rangedStance = false;
        super.tick(level, mob);
        if (bowGoals == null)
            bowGoals = createGoalSelector(new SkeletonBowGoal(this));
        bowGoals.tick();
    }

    @Override
    protected boolean navigationAllowed() {
        return !rangedStance && super.navigationAllowed();
    }

    public boolean groundNavigationAllowed() {
        return super.navigationAllowed();
    }

    @Override
    protected void doAttack() {
        super.doAttack();
        if (strafeGoals == null)
            strafeGoals = createGoalSelector(new SkeletonStrafeGoal(this));
        strafeGoals.tick();
    }

    @Override
    public boolean attack(Entity target) {
        return false;
    }

    @Override
    public void beforeRemove() {
        super.beforeRemove();
        if (mob instanceof Skeleton s)
            s.cancelBowAttack();
    }
}
