package net.classicremastered.minecraft.mob.ai.goal.zombie;

import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.Goal;

public final class ZombieDifficultyGoal extends Goal {

    private final Zombie zombie;
    public ZombieDifficultyGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(java.util.EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canStart() {
        return zombie.level != null;
    }

    @Override
    public void tick() {
        Difficulty diff = (zombie.level.minecraft != null && zombie.level.minecraft.settings != null)
                ? zombie.level.minecraft.settings.difficulty
                : Difficulty.NORMAL;
        // --- AI behavior by difficulty ---
        if (zombie.ai instanceof net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI) {
            net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI a = (net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI) zombie.ai;

            switch (diff) {
                case PEACEFUL :
                    a.damage = 0;
                    a.runSpeed = 0.8f;
                    a.attackTarget = null; // no attacking
                    break;

                case EASY :
                    a.damage = 6;
                    a.runSpeed = 0.95f;
                    break;

                case NORMAL :
                    a.damage = 6;
                    a.runSpeed = 1.05f;
                    break;

                case HARD, VERY_HARD, EXTREME:
                    a.damage = 6;
                    a.runSpeed = 1.20f;
                    break;
            }
        }

    }
}
