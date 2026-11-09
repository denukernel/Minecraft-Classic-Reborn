package net.classicremastered.minecraft.mob.ai.goal.villager;

import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.goal.movement.FleeThreatGoal;
import net.classicremastered.minecraft.mob.ai.villager.VillagerAI;

public final class VillagerFleeGoal extends FleeThreatGoal {

    public VillagerFleeGoal(VillagerAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return ai.mob instanceof Villager villager
                && !(villager.hasHome && !ai.level.isDaytime()) && super.canStart();
    }
}
