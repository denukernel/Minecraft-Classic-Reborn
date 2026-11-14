package net.classicremastered.minecraft.mob.ai.goal.villager;

import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.villager.VillagerAI;

public final class HostileVillagerGoal extends MobGoal<VillagerAI> {

    public HostileVillagerGoal(VillagerAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.mob instanceof Villager villager && villager.isHostile();
    }

    private float exitX(Villager villager) {
        float dx = villager.homeDoorX + .5f - villager.homeX;
        float dz = villager.homeDoorZ + .5f - villager.homeZ;
        return Math.abs(dx) > Math.abs(dz) ? Math.signum(dx) * 1.5f : 0;
    }

    private float exitZ(Villager villager) {
        float dx = villager.homeDoorX + .5f - villager.homeX;
        float dz = villager.homeDoorZ + .5f - villager.homeZ;
        return Math.abs(dx) > Math.abs(dz) ? 0 : Math.signum(dz) * 1.5f;
    }

    @Override
    public void tick() {
        Villager villager = (Villager) ai.mob;
        boolean home = villager.hasHome;
        ai.runSpeed = 1.2f;
        ai.walkSpeed = 0.3f;
        if (home
                && villager.hasHomeDoor
                && Math.abs(ai.mob.x - ai.homeX) < ai.homeRadius
                && Math.abs(ai.mob.z - ai.homeZ) < ai.homeRadius) {
            ai.level.villagerDoors().openForHostile(villager);
            ai.navigateTo(
                    villager.homeDoorX + .5f + exitX(villager),
                    villager.homeDoorY,
                    villager.homeDoorZ + .5f + exitZ(villager),
                    ai.runSpeed);
            return;
        }
        net.classicremastered.minecraft.mob.Mob target =
                net.classicremastered.minecraft.mob.ai.combat.HostileTargets.nearestForPursuit(
                        ai.mob, 40, ai.attackTarget);
        ai.setPursuitTarget(target);
        if (target != null
                && target.health > 0
                && !net.classicremastered.minecraft.util.CreativeModeHelper.shouldIgnoreTarget(
                        target)) {
            ai.navigateTo(target.x, target.bb.y0, target.z, ai.runSpeed);
            return;
        }

        ai.freeWander = true;
        ai.defaultMovement();
    }
}
