package net.classicremastered.minecraft.mob.ai.goal.stickfigure;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class BossStrikeGoal extends MeleeAttackGoal {

    public BossStrikeGoal(StickFigureAI ai) {
        super(ai);
    }

    @Override
    public boolean hit(Entity target) {
        StickFigureAI ai = (StickFigureAI) this.ai;
        if (ai.mob == null
                || target == null
                || target.removed
                || ai.attackDelay > 0
                || ai.mob.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(target)
                || CombatRules.difficulty(ai.level) == Difficulty.PEACEFUL
                || !ai.withinMeleeReach(target, 2f)
                || !ai.hasLineOfSight(ai.mob, target))
            return false;
        StickFigure boss = (StickFigure) ai.mob;
        boss.kicking = ai.nextKick;
        ai.nextKick = !ai.nextKick;
        boss.strikeTicks = 14;
        ai.mob.attackTime = 14;
        ai.attackDelay = boss.kicking ? 32 : 24;
        ai.noActionTime = 0;
        return true;
    }
}
