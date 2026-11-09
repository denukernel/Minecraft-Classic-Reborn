package net.classicremastered.minecraft.mob.ai.villager;

import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.villager.HostileVillagerGoal;
import net.classicremastered.minecraft.mob.ai.goal.villager.ReturnHomeGoal;
import net.classicremastered.minecraft.mob.ai.goal.villager.VillagerFleeGoal;
import net.classicremastered.minecraft.mob.ai.goal.villager.VillagerWanderGoal;

/**
 * Shared navigation for home travel, wandering, fleeing, and hostile villagers.
 */
public class VillagerAI extends BasicAI {

    private static final long serialVersionUID = 0L;
    public float homeX, homeY, homeZ;
    public float homeRadius = 3f;
    public boolean enforceInsideAtNight = true;
    public boolean freeWander, returningHome;
    @Override
    protected boolean usesWanderNavigation() {
        return false;
    }

    @Override
    protected boolean usesPlannedWandering() {
        return freeWander;
    }

    public VillagerAI(float x, float y, float z, float radius) {
        homeX = x;
        homeY = y;
        homeZ = z;
        homeRadius = radius;
        runSpeed = 0.6f;
        walkSpeed = 0.1f;
    }

    @Override
    protected void update() {
        freeWander = false;
        xxa = yya = yRotA = 0;
        jumping = running = false;
        if (level == null || !(mob instanceof Villager villager))
            return;
        boolean home = villager.hasHome;
        if (home) {
            homeX = villager.homeX;
            homeY = villager.homeY;
            homeZ = villager.homeZ;
            homeRadius = villager.homeRadius;
        }
    }

    @Override
    protected void registerGoals(net.classicremastered.minecraft.mob.ai.goal.GoalSelector selector) {
        selector.addGoal(0, new VillagerFleeGoal(this));
        selector.addGoal(1, new HostileVillagerGoal(this));
        selector.addGoal(2, new ReturnHomeGoal(this));
        selector.addGoal(3, new VillagerWanderGoal(this));
    }
}
