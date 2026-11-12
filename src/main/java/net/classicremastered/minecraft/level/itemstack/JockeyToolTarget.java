package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;

/** Resolves the body that can move without breaking a mob riding pair. */
final class JockeyToolTarget {
    private JockeyToolTarget() {}

    static Mob body(Entity hit, Level level) {
        if (!usable(hit, level)) return null;
        Mob mob = (Mob) hit;
        if (mob.riding == null) return validBody(mob, level) ? mob : null;
        if (!(mob.riding instanceof Mob mount) || mount.rider != mob) return null;
        return validBody(mount, level) ? mount : null;
    }

    static boolean validBody(Mob body, Level level) {
        return usable(body, level)
                && body.riding == null
                && !PhysicsGunItem.isHeld(body)
                && (body.rider == null
                        || usable(body.rider, level)
                                && body.rider.riding == body
                                && !PhysicsGunItem.isHeld((Mob) body.rider));
    }

    static boolean isGrappled(Mob body, Level level) {
        if (level.blockMap == null) return false;
        for (Entity entity : level.blockMap.all) {
            if (entity instanceof Player player
                    && player.grapple != null
                    && player.grapple.level() == level
                    && player.grapple.body() == body) {
                return true;
            }
        }
        return false;
    }

    private static boolean usable(Entity entity, Level level) {
        return entity instanceof Mob mob
                && !(mob instanceof Player)
                && mob.level == level
                && mob.health > 0
                && !mob.removed
                && mob.isPickable();
    }
}
