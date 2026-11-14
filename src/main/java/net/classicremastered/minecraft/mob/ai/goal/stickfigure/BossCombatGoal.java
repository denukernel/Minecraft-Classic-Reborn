package net.classicremastered.minecraft.mob.ai.goal.stickfigure;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class BossCombatGoal extends MobGoal<StickFigureAI> {

    public BossCombatGoal(StickFigureAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        StickFigure boss = (StickFigure) ai.mob;
        if (CombatRules.difficulty(ai.level) == Difficulty.PEACEFUL) {
            ai.movement().cancel();
            ai.powers.cancel(boss);
            ai.attackTarget = null;
            boss.strikeTicks = 0;
            ai.running = false;
            return;
        }
        if (ai.movement().isLeaping() && !ai.powers.blocksNavigation()) {
            ai.specialMovement = ai.movement().chase(ai, boss, ai.attackTarget);
            if (ai.specialMovement) return;
        }
        boolean swimming = boss.isInWater();
        if (swimming) {
            if (ai.powers.blocksNavigation()) ai.powers.cancel(boss);
        } else if (ai.powers.tick(boss, ai.attackTarget, ai.random)) {
            ai.movement().cancel();
            ai.xxa = ai.yya = ai.yRotA = 0;
            ai.jumping = ai.running = false;
            return;
        }
        if (boss.strikeTicks == 0 || boss.strikeTicks > 6) {
            ai.hitThisStrike = false;
            ai.playedSwing = false;
        }
        if (boss.strikeTicks <= 6 && boss.strikeTicks >= 3 && !ai.playedSwing) {
            ai.combatSound("random/bow", .5f, boss.kicking ? .65f : 1.35f);
            ai.playedSwing = true;
        }
        if (boss.strikeTicks <= 6
                && boss.strikeTicks >= 3
                && !ai.hitThisStrike
                && ai.attackTarget != null
                && !CreativeModeHelper.shouldIgnoreTarget(ai.attackTarget)
                && ai.withinMeleeReach(ai.attackTarget, boss.kicking ? 2.7f : 2.2f)
                && ai.hasLineOfSight(ai.mob, ai.attackTarget)) {
            int before = ai.attackTarget instanceof Mob victim ? victim.health : 0;
            ai.attackTarget.hurt(ai.mob, boss.kicking ? 7 : 5);
            if (!(ai.attackTarget instanceof Mob victim) || victim.health < before)
                finishStrike(boss);
        }
        ai.defaultAttack();
        if (swimming) {
            ai.specialMovement = ai.movement().chase(ai, boss, ai.attackTarget);
        } else if (boss.dodgeTicks > 0) {
            ai.xxa = ai.yya = ai.yRotA = 0;
            ai.jumping = false;
        } else if (boss.strikeTicks == 0 && ai.attackTarget != null) {
            ai.specialMovement = ai.movement().chase(ai, boss, ai.attackTarget);
        }
    }

    private void finishStrike(StickFigure boss) {
        ai.hitThisStrike = true;
        ai.combatSound(
                boss.kicking ? "mob/irongolem/hit" : "damage/hurtflesh",
                .9f,
                boss.kicking ? .8f : 1.15f);
        float dx = ai.attackTarget.x - boss.x, dz = ai.attackTarget.z - boss.z;
        float distance = (float) Math.hypot(dx, dz);
        if (distance > .01f) {
            float force = boss.kicking ? .55f : .2f;
            ai.attackTarget.xd += dx / distance * force;
            ai.attackTarget.zd += dz / distance * force;
        }
    }
}
