package net.classicremastered.minecraft.mob.ai.combat;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Shared proactive targets for player-hunting mobs, including custom combat paths. */
public final class HostileTargets {
    private HostileTargets() {}

    public static boolean eligible(Entity target) {
        return target instanceof Mob living
                && !living.removed
                && living.health > 0
                && (target instanceof Defender
                        || target instanceof Player
                                && !CreativeModeHelper.shouldIgnoreTarget(target));
    }

    public static Mob nearest(Mob mob, float range, Entity retained) {
        return nearest(mob, range, retained, null);
    }

    public static Mob nearest(Mob mob, float range, Entity retained, Entity excluded) {
        if (mob == null || mob.level == null) return null;
        Mob battle = net.classicremastered.minecraft.level.itemstack.MobBattle.target(mob);
        if (battle != null) return battle;
        if (CombatRules.difficulty(mob.level) == Difficulty.PEACEFUL) return null;
        if (retained != excluded
                && eligible(retained)
                && mob.distanceToSqr(retained) <= range * range) return (Mob) retained;
        Mob nearest = null;
        float distance = range * range;
        Entity player = mob.level.getPlayer();
        if (player != excluded && eligible(player) && mob.distanceToSqr(player) <= distance) {
            nearest = (Mob) player;
            distance = mob.distanceToSqr(player);
        }
        for (Object candidate : mob.level.findEntities(mob, mob.bb.grow(range, range, range))) {
            if (candidate instanceof Mob target
                    && target != excluded
                    && eligible(target)
                    && mob.distanceToSqr(target) < distance) {
                nearest = target;
                distance = mob.distanceToSqr(target);
            }
        }
        return nearest;
    }
}
