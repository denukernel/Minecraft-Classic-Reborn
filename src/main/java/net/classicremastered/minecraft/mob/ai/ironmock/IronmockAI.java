package net.classicremastered.minecraft.mob.ai.ironmock;

import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.goal.ironmock.IronmockAttackGoal;

public final class IronmockAI extends BasicAttackAI {

    public IronmockAI() {
        runSpeed = .75f;
    }

    @Override
    protected boolean usesPlannedWandering() {
        return true;
    }

    public Difficulty difficulty() {
        return level != null && level.minecraft != null && level.minecraft.settings != null
                ? level.minecraft.settings.difficulty
                : Difficulty.NORMAL;
    }

    @Override
    protected void doAttack() {
        if (difficulty() == Difficulty.PEACEFUL) {
            attackTarget = null;
            running = false;
            return;
        }
        super.doAttack();
    }

    @Override
    protected MeleeAttackGoal createMeleeGoal() {
        return new IronmockAttackGoal(this);
    }
}
