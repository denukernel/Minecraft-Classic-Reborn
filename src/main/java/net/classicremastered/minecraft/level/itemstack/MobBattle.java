package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.AI;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.player.Player;

public final class MobBattle {
    public record Fight(Mob target, AI original, boolean persistent) {}

    private MobBattle() {}

    public static void fight(Mob mob, Mob target) {
        if (mob == target || mob.level != target.level || mob.health <= 0 || target.health <= 0
                || target instanceof Player player && player.isSpectator())
            return;
        Fight old = mob.battle;
        mob.battle =
                new Fight(
                        target,
                        old == null ? mob.ai : old.original,
                        old == null ? mob.persistent : old.persistent);
        mob.persistent = true;
        if (!(mob.ai instanceof BasicAttackAI)
                && !(mob.ai
                        instanceof net.classicremastered.minecraft.mob.ai.skeleton.SkeletonArrowAI))
            mob.ai = new BasicAttackAI();
        tick(mob);
    }

    public static Mob target(Mob mob) {
        Fight fight = mob.battle;
        return fight == null ? null : fight.target;
    }

    public static void tick(Mob mob) {
        Fight fight = mob.battle;
        if (fight == null) return;
        if (fight.target.removed || fight.target instanceof Player player && player.isSpectator()
                || fight.target.health <= 0 || fight.target.level != mob.level || mob.health <= 0) {
            stop(mob);
            return;
        }
        if (mob.ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI ai) {
            ai.bind(mob.level, mob);
            ai.attackTarget = fight.target;
            if (ai instanceof BasicAttackAI combat) combat.retaliationTicks = 1200;
        }
    }

    public static void stop(Mob mob) {
        Fight fight = mob.battle;
        mob.battle = null;
        if (fight != null) {
            mob.ai = fight.original;
            mob.persistent = fight.persistent;
        }
        if (mob.ai instanceof BasicAttackAI ai) {
            ai.attackTarget = null;
            ai.retaliationTicks = 0;
        }
    }
}
