package net.classicremastered.minecraft.mob.ai.goal.movement;

import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public final class PlannedStrollGoal extends MobGoal<BasicAI> {

    public PlannedStrollGoal(BasicAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.attackTarget == null && ai.hasPlannedWandering() && ai.canNavigate()
                && !ai.mob.isInWater() && !ai.mob.isInLava();
    }

    @Override
    public void tick() {
        ai.xxa = ai.yya = ai.yRotA = 0;
        ai.jumping = ai.running = false;
    }

}
