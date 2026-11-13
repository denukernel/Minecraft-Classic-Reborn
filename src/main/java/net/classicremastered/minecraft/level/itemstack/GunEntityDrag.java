package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.entity.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared ownership and collision-safe dragging for non-mob entities. */
public final class GunEntityDrag {
    private record Hold(Entity entity, int tool) {
    }
    private static final Map<Player, Hold> held = new WeakHashMap<>();
    private static final Map<Player, Float> distances = new WeakHashMap<>();

    private GunEntityDrag() {
    }

    public static Entity get(Player player, int tool) {
        Hold hold = held.get(player);
        return hold != null && hold.tool == tool ? hold.entity : null;
    }

    public static boolean isHeld(Entity entity) {
        return held.values().stream().anyMatch(hold -> hold.entity == entity);
    }

    public static Entity pick(Player player, Level level, float range) {
        if (level == null || level.isNetworkMode() || level.blockMap == null)
            return null;
        Vec3D start = GravityGunItem.eye(player), direction = GravityGunItem.look(player);
        Vec3D end = start.add(direction.x * range, direction.y * range, direction.z * range);
        MovingObjectPosition wall = level.clip(new Vec3D(start.x, start.y, start.z), end, true);
        float nearest = wall == null ? range : start.distance(wall.vec);
        Entity result = null;
        for (Entity entity : level.blockMap.getEntities(player,
                player.bb.expand(direction.x * range, direction.y * range, direction.z * range).grow(.3f, .3f, .3f))) {
            if (entity instanceof Mob
                    || entity instanceof BoatEntity
                    || entity instanceof HeldBlockEntity
                    || entity instanceof SignEntity
                    || entity instanceof TakeEntityAnim
                    || entity instanceof DamageCloud
                    || entity.removed
                    || entity.bb == null
                    || entity.rider != null
                    || entity.riding != null
                    || isHeld(entity)) continue;
            MovingObjectPosition hit = entity.bb.grow(.2f, .2f, .2f).clip(start, end);
            if (hit != null && start.distance(hit.vec) < nearest) {
                nearest = start.distance(hit.vec);
                result = entity;
            }
        }
        return result;
    }

    public static float hitDistance(Player player, Entity entity) {
        Vec3D start = GravityGunItem.eye(player), direction = GravityGunItem.look(player);
        MovingObjectPosition hit = entity.bb.grow(.15f, .15f, .15f).clip(start,
                start.add(direction.x * 12, direction.y * 12, direction.z * 12));
        return hit == null ? 12 : start.distance(hit.vec);
    }

    public static void grab(Player player, Entity entity, int tool) {
        if (entity == null || isHeld(entity))
            return;
        held.put(player, new Hold(entity, tool));
        distances.put(player, Math.max(3, Math.min(12, player.distanceTo(entity))));
        entity.xd = entity.yd = entity.zd = 0;
        if (entity instanceof Arrow arrow)
            arrow.releaseFromBlock();
        if (entity instanceof PrimedTnt tnt) tnt.setImpactFuse(false);
        if (entity instanceof ImpactPrimedTnt tnt) tnt.clearGravityGunLaunch();
    }

    public static void release(Player player, int tool, float force) {
        Entity entity = get(player, tool);
        if (entity == null)
            return;
        held.remove(player);
        distances.remove(player);
        Vec3D direction =
                tool == GravityGunItem.RAW_ID && force > 0
                        ? GravityGunItem.launchDirection(player, entity)
                        : GravityGunItem.look(player);
        entity.xd = direction.x * force;
        entity.yd = direction.y * force;
        entity.zd = direction.z * force;
        entity.fallDistance = 0;
        if (entity instanceof Arrow arrow)
            arrow.releaseFromBlock();
        if (tool == GravityGunItem.RAW_ID && force > 0) {
            if (entity instanceof PrimedTnt tnt) tnt.armOnImpact(player);
            if (entity instanceof ImpactPrimedTnt tnt) tnt.armOnImpact(player);
        }
    }

    public static void controls(Player player, int tool, boolean farther, boolean closer) {
        if (get(player, tool) != null) {
            float distance = distances.getOrDefault(player, 3f) + (farther ? .2f : 0) - (closer ? .2f : 0);
            distances.put(player, Math.max(3, Math.min(12, distance)));
        }
    }

    public static void cleanup(Level level) {
        for (Player player : held.keySet().toArray(new Player[0])) {
            Hold hold = held.get(player);
            if (player.isSpectator() || player.removed || player.health <= 0
                    || player.level != level || hold.entity.level != level
                    || level.isNetworkMode() || hold.entity.removed || player.inventory.getSelected() != hold.tool)
                release(player, hold.tool, 0);
        }
    }

    /**
     * Called by the entity tick loop so a held projectile cannot fight the drag
     * motion.
     */
    public static boolean tick(Entity entity) {
        Player owner = null;
        for (Map.Entry<Player, Hold> entry : held.entrySet()) {
            if (entry.getValue().entity == entity) {
                owner = entry.getKey();
                break;
            }
        }
        if (owner == null)
            return false;
        Hold hold = held.get(owner);
        if (owner.isSpectator() || owner.removed || owner.health <= 0 || entity.removed
                || owner.level != entity.level
                || entity.level.isNetworkMode() || owner.inventory.getSelected() != hold.tool
                || owner.distanceTo(entity) > 18) {
            release(owner, hold.tool, 0);
            return false;
        }
        entity.xo = entity.x;
        entity.yo = entity.y;
        entity.zo = entity.z;
        Vec3D start = GravityGunItem.eye(owner), direction = GravityGunItem.look(owner);
        float distance = distances.getOrDefault(owner, 3f);
        Vec3D target = start.add(direction.x * distance, direction.y * distance, direction.z * distance);
        MovingObjectPosition wall = entity.level.clip(new Vec3D(start.x, start.y, start.z), target, true);
        if (wall != null) {
            distance = Math.max(0, start.distance(wall.vec) - Math.max(entity.bbWidth, entity.bbHeight) * .5f - .15f);
            target = start.add(direction.x * distance, direction.y * distance, direction.z * distance);
        }
        entity.xd = entity.xd * .45f + (target.x - entity.x) * .22f;
        entity.yd = entity.yd * .45f + (target.y - (entity.bb.y0 + entity.bb.y1) * .5f) * .22f;
        entity.zd = entity.zd * .45f + (target.z - entity.z) * .22f;
        float speed = (float) Math.sqrt(entity.xd * entity.xd + entity.yd * entity.yd + entity.zd * entity.zd);
        if (speed > .9f) {
            entity.xd *= .9f / speed;
            entity.yd *= .9f / speed;
            entity.zd *= .9f / speed;
        }
        entity.move(entity.xd, entity.yd, entity.zd);
        entity.fallDistance = 0;
        if (entity instanceof PrimedTnt tnt)
            tnt.tickFuse();
        if (entity instanceof ImpactPrimedTnt tnt)
            tnt.tickFuse();
        return true;
    }
}
