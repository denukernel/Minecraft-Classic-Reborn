package net.classicremastered.minecraft.mob.ai.zombie;

import net.classicremastered.minecraft.mob.ZombieBuilder;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;

public final class ZombieBuilderAI extends BasicAttackAI {

    public ZombieBuilderAI() {
        super(true, true);
    }

    @Override
    protected void update() {
        super.update();
        if (mob instanceof ZombieBuilder builder)
            builder.buildStepsGoal().steer(this);
    }
}
