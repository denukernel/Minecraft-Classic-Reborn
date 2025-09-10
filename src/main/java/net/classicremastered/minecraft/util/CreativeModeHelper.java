package net.classicremastered.minecraft.util;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.level.Level;

public final class CreativeModeHelper {

    private CreativeModeHelper() {} // no instantiation

    /** Returns true if this level is in Creative mode. */
    public static boolean isCreative(Level level) {
        return level != null && level.creativeMode;
    }

    /** Returns true if the given entity is a creative player. */
    public static boolean isCreativePlayer(Entity e) {
        if (e instanceof Player) {
            Player p = (Player) e;
            return p.level != null && p.level.creativeMode;
        }
        return false;
    }

    /** Returns true if this entity can be damaged (respects Creative). */
    public static boolean canTakeDamage(Entity target, Entity attacker) {
        // Creative players can’t be attacked
        if (isCreativePlayer(target)) {
            return false;
        }
        return true;
    }

    /** Returns true if attacker is allowed to hurt target (respects Creative). */
    public static boolean canAttack(Entity attacker, Entity target) {
        // Creative players never count as valid attackers
        if (isCreativePlayer(attacker)) {
            return false;
        }
        return true;
    }

    /** Clears hostility/retaliation if target is Creative. */
    public static boolean shouldIgnoreTarget(Entity target) {
        return isCreativePlayer(target);
    }
}
