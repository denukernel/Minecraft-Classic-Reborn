package net.classicremastered.minecraft.mob.ai.goal.villager;

import net.classicremastered.minecraft.level.VillagerDoorManager;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.villager.VillagerAI;

public final class ReturnHomeGoal extends MobGoal<VillagerAI> {

    public ReturnHomeGoal(VillagerAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.mob instanceof Villager villager && !villager.isHostile() && villager.hasHome;
    }

    @Override
    public void tick() {
        Villager villager = (Villager) ai.mob;
        net.classicremastered.minecraft.level.VillagerDoorManager.Approach approach = ai.level.villagerDoors()
                .approach(villager);
        if (approach != null) {
            ai.navigateTo(approach.x(), approach.y(), approach.z(), ai.runSpeed);
            return;
        }
        float dx = ai.homeX - ai.mob.x, dz = ai.homeZ - ai.mob.z, distance = dx * dx + dz * dz;
        boolean night = ai.enforceInsideAtNight && !ai.level.isDaytime();
        if (distance > ai.homeRadius * ai.homeRadius * (night ? 1f : 1.6f))
            ai.returningHome = true;
        if (distance < ai.homeRadius * ai.homeRadius * 0.5f)
            ai.returningHome = false;
        if (ai.returningHome) {
            ai.navigateTo(ai.homeX, ai.homeY - 1, ai.homeZ, ai.runSpeed);
            return;
        }
        if (night)
            return;
        ai.freeWander = true;
        ai.defaultMovement();
    }

}
