package net.classicremastered.minecraft.mob.ai.goal.stickfigure;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigurePowers;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class BossCombatGoal extends MobGoal<StickFigureAI> {

    public BossCombatGoal(StickFigureAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        StickFigure boss = (StickFigure) ai.mob;
        if (CombatRules.difficulty(ai.level) == Difficulty.PEACEFUL) {
            ai.clearPlayerSearch();
            ai.movement().cancel();
            ai.powers.cancel(boss);
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
                && ai.attackTarget != null) {
            resolveStrike(boss, ai.attackTarget);
        }
        ai.defaultAttack();
        if (swimming) {
            ai.specialMovement =
                    ai.swimToLastSeenPlayer(boss) || ai.movement().chase(ai, boss, ai.attackTarget);
        } else if (boss.dodgeTicks > 0) {
            ai.xxa = ai.yya = ai.yRotA = 0;
            ai.jumping = false;
        } else if (boss.strikeTicks == 0 && ai.attackTarget != null) {
            ai.specialMovement = ai.movement().chase(ai, boss, ai.attackTarget);
        }
    }

    private void resolveStrike(StickFigure boss, Entity target) {
        boolean hit = strike(boss, target, boss.kicking ? 7 : 5, boss.kicking ? .55f : .2f);
        if (boss.isEnraged() && boss.kicking) {
            float forwardX = target.x - boss.x, forwardZ = target.z - boss.z;
            for (Mob attacker : StickFigurePowers.nearbyAttackers(boss)) {
                float dx = attacker.x - boss.x, dz = attacker.z - boss.z;
                if (attacker != target
                        && forwardX * dx + forwardZ * dz >= 0
                        && strike(boss, attacker, 4, .55f)) hit = true;
            }
        }
        if (hit) {
            ai.hitThisStrike = true;
            ai.combatSound(
                    boss.kicking ? "mob/irongolem/hit" : "damage/hurtflesh",
                    .9f,
                    boss.kicking ? .8f : 1.15f);
        }
    }

    private boolean strike(StickFigure boss, Entity target, int damage, float force) {
        if (target.removed
                || CreativeModeHelper.shouldIgnoreTarget(target)
                || !ai.withinMeleeReach(target, boss.kicking ? 2.7f : 2.2f)
                || !ai.hasLineOfSight(boss, target)) return false;
        int before = target instanceof Mob victim ? victim.health : 0;
        target.hurt(boss, damage);
        if (target instanceof Mob victim && victim.health >= before) return false;
        float dx = target.x - boss.x, dz = target.z - boss.z;
        float distance = (float) Math.hypot(dx, dz);
        if (distance > .01f) {
            target.xd += dx / distance * force;
            target.zd += dz / distance * force;
        }
        return true;
    }
}
