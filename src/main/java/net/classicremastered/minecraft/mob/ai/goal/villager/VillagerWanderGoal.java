package net.classicremastered.minecraft.mob.ai.goal.villager;

import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.villager.VillagerAI;

public final class VillagerWanderGoal extends MobGoal<VillagerAI> {

    public VillagerWanderGoal(VillagerAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        ai.freeWander = true;
        ai.defaultMovement();
    }

}
