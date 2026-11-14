package net.classicremastered.minecraft.mob.ai.combat;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.level.tile.LadderBlock;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Shared proactive targets for player-hunting mobs, including custom combat paths. */
public final class HostileTargets {
    private static final java.util.Map<Mob, LadderApproach> LADDER_APPROACHES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private record LadderApproach(int x, int y, int z, int tick, boolean reachable) {}

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
        return nearest(mob, range, retained, excluded, false);
    }

    public static Mob nearestForPursuit(Mob mob, float range, Entity retained) {
        return nearest(mob, range, retained, null, true);
    }

    private static Mob nearest(
            Mob mob, float range, Entity retained, Entity excluded, boolean visiblePlayers) {
        if (mob == null || mob.level == null) return null;
        Mob battle = net.classicremastered.minecraft.level.itemstack.MobBattle.target(mob);
        if (battle != null) return battle;
        if (CombatRules.difficulty(mob.level) == Difficulty.PEACEFUL) return null;
        if (retained != excluded
                && eligible(retained)
                && mob.distanceToSqr(retained) <= range * range
                && (!visiblePlayers || canLocate(mob, retained))) return (Mob) retained;
        Mob nearest = null;
        float distance = range * range;
        Entity player = mob.level.getPlayer();
        if (player != excluded
                && eligible(player)
                && mob.distanceToSqr(player) <= distance
                && (!visiblePlayers || canLocate(mob, player))) {
            nearest = (Mob) player;
            distance = mob.distanceToSqr(player);
        }
        for (Object candidate : mob.level.findEntities(mob, mob.bb.grow(range, range, range))) {
            if (candidate instanceof Mob target
                    && target != excluded
                    && eligible(target)
                    && mob.distanceToSqr(target) < distance
                    && (!visiblePlayers || canLocate(mob, target))) {
                nearest = target;
                distance = mob.distanceToSqr(target);
            }
        }
        return nearest;
    }

    public static boolean canLocate(Mob mob, Entity target) {
        return target.level == mob.level
                && (!(target instanceof Player)
                        || hasLineOfSight(mob, target)
                        || canReachLadderLanding(mob, target));
    }

    private static boolean canReachLadderLanding(Mob mob, Entity target) {
        float rise = target.bb.y0 - mob.bb.y0;
        if (!Pathfinder.supportsLadders(mob)
                || rise < 2
                || rise > 16
                || mob.distanceToSqr(target) > 20 * 20) return false;
        int x = (int) Math.floor(target.x),
                y = (int) Math.floor(target.bb.y0),
                z = (int) Math.floor(target.z);
        LadderApproach previous = LADDER_APPROACHES.get(mob);
        if (previous != null
                && previous.x() == x
                && previous.y() == y
                && previous.z() == z
                && mob.tickCount - previous.tick() < 10) return previous.reachable();
        boolean reachable = findLadderApproach(mob, target, x, y, z);
        LADDER_APPROACHES.put(mob, new LadderApproach(x, y, z, mob.tickCount, reachable));
        return reachable;
    }

    private static boolean findLadderApproach(Mob mob, Entity target, int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (LadderBlock.isLadder(mob.level.getTile(x + dx, y - 1, z + dz))
                        || LadderBlock.isLadder(mob.level.getTile(x + dx, y, z + dz))) {
                    var path =
                            Pathfinder.findChasePath(
                                    mob.level, mob, target.x, target.bb.y0, target.z, 1024);
                    if (path == null || path.size() < 2) return false;
                    var end = path.get(path.size() - 1);
                    if (Math.abs(end.feetY - target.bb.y0) >= 1) return false;
                    for (var node : path) {
                        if (node.parent != null
                                && node.x == node.parent.x
                                && node.z == node.parent.z) return true;
                    }
                    return false;
                }
            }
        }
        return false;
    }

    public static boolean hasLineOfSight(Entity from, Entity to) {
        if (from == null || to == null || from.level == null || to.level != from.level)
            return false;
        float fromFeet = from.bb != null ? from.bb.y0 : from.y - from.heightOffset;
        float toFeet = to.bb != null ? to.bb.y0 : to.y - to.heightOffset;
        float eye = fromFeet + (from.bbHeight > 0 ? from.bbHeight * .85f : .9f);
        float middle = toFeet + (to.bbHeight > 0 ? to.bbHeight * .5f : .5f);
        float dx = to.x - from.x, dy = middle - eye, dz = to.z - from.z;
        // Level.clip visits at most 21 cells per trace.
        int segments =
                Math.max(1, (int) Math.ceil((Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) / 16));
        for (int i = 0; i < segments; i++) {
            float start = (float) i / segments;
            float end = (float) (i + 1) / segments;
            if (from.level.clip(
                            new Vec3D(from.x + dx * start, eye + dy * start, from.z + dz * start),
                            new Vec3D(from.x + dx * end, eye + dy * end, from.z + dz * end),
                            true)
                    != null) return false;
        }
        return true;
    }
}
