package net.classicremastered.minecraft.mob.ai.goal.movement;

import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public final class DefaultMovementGoal extends MobGoal<BasicAI> {

    public DefaultMovementGoal(BasicAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        ai.defaultMovement();
    }

}
