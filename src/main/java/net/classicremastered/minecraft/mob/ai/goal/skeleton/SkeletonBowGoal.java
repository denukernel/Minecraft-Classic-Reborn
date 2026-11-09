package net.classicremastered.minecraft.mob.ai.goal.skeleton;

import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.skeleton.SkeletonAI;

public final class SkeletonBowGoal extends MobGoal<SkeletonAI> {

    public SkeletonBowGoal(SkeletonAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return ai.mob instanceof Skeleton;
    }

    @Override
    public void tick() {
        ((Skeleton) ai.mob).updateBowAttack(ai.attackTarget,
                ai.attackTarget != null && ai.hasLineOfSight(ai.mob, ai.attackTarget));
    }

}
