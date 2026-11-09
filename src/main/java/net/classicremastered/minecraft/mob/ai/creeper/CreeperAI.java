package net.classicremastered.minecraft.mob.ai.creeper;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.creeper.CreeperSwellGoal;

/** Creepers navigate toward targets but never deal melee damage. */
public final class CreeperAI extends BasicAttackAI {

    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector swell;
    @Override
    protected boolean usesPlannedWandering() {
        return true;
    }

    @Override
    protected boolean navigationAllowed() {
        return !(mob instanceof Creeper c && c.ignited) && super.navigationAllowed();
    }

    @Override
    protected void doAttack() {
        super.doAttack();
        if (swell == null)
            swell = createGoalSelector(new CreeperSwellGoal(this));
        swell.tick();
    }

    @Override
    public boolean attack(Entity target) {
        return false;
    }
}
