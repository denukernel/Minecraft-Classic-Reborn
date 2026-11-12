package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

public final class GrapplingHookItem extends Item {
    private static final float RANGE = 24f;
    private static final float REST_DISTANCE = 1.8f;
    private static final String SOUND = "item/grappling_hook/";

    public record Tether(Level level, Mob mob, Mob body, MovingObjectPosition block, int blockId) {
        Vec3D endpoint(float partial) {
            if (mob == null) return block.vec;
            return new Vec3D(
                    mob.xo + (mob.x - mob.xo) * partial,
                    mob.yo + (mob.y - mob.yo) * partial + (mob.bb.y0 + mob.bb.y1) * .5f - mob.y,
                    mob.zo + (mob.z - mob.zo) * partial);
        }
    }

    public GrapplingHookItem(int id) {
        super(id, "Grappling Hook", "/items/grappling_hook.png", 1);
    }

    @Override
    public void use(Player player, Level level) {
        if (!canUse(player, level)) return;
        if (player.grapple != null) {
            detach(player);
            return;
        }

        level.playSound(SOUND + "launch", player, .8f, 1f);
        Vec3D start = GravityGunItem.eye(player);
        Vec3D aim = player.aimDirection(1);
        Vec3D end = start.add(aim.x * RANGE, aim.y * RANGE, aim.z * RANGE);
        MovingObjectPosition wall = trace(level, start, end);
        float nearest = wall == null ? RANGE : start.distance(wall.vec);
        Mob target = null;
        Mob body = null;
        if (level.blockMap != null) {
            for (Entity entity :
                    level.blockMap.getEntities(
                            player,
                            player.bb
                                    .expand(aim.x * RANGE, aim.y * RANGE, aim.z * RANGE)
                                    .grow(.2f, .2f, .2f))) {
                Mob candidate = JockeyToolTarget.body(entity, level);
                if (candidate == null || GravityGunItem.grabbedMobs.containsValue(candidate))
                    continue;
                Mob mob = (Mob) entity;
                MovingObjectPosition hit = mob.bb.grow(.12f, .12f, .12f).clip(start, end);
                float distance = hit == null ? Float.POSITIVE_INFINITY : start.distance(hit.vec);
                if (distance < nearest && !JockeyToolTarget.isGrappled(candidate, level)) {
                    nearest = distance;
                    target = mob;
                    body = candidate;
                }
            }
        }
        if (target != null) {
            player.grapple = new Tether(level, target, body, null, 0);
        } else if (wall != null) {
            int id = level.getTile(wall.x, wall.y, wall.z);
            if (id > 0
                    && Block.blocks[id] != null
                    && Block.blocks[id].getCollisionBox(wall.x, wall.y, wall.z) != null) {
                player.grapple = new Tether(level, null, null, wall, id);
            }
        }
        if (player.grapple != null) level.playSound(SOUND + "latch", player, .8f, 1f);
    }

    public static void updateTether(Player player) {
        Tether tether = player.grapple;
        if (tether == null) return;
        Level level = player.level;
        if (!isEquipped(player)
                || !canUse(player, level)
                || level != tether.level()
                || !validMobTether(tether)
                || tether.block() != null && !stillAnchored(tether)) {
            detach(player);
            return;
        }

        Vec3D start = GravityGunItem.eye(player);
        Vec3D end = tether.endpoint(1);
        float distance = start.distance(end);
        if (!Float.isFinite(distance) || distance > RANGE + 1) {
            detach(player);
            return;
        }
        MovingObjectPosition obstruction = trace(level, start, end);
        if (obstruction != null && start.distance(obstruction.vec) < distance - .12f) {
            detach(player);
            return;
        }
        if (distance <= REST_DISTANCE) return;

        if (tether.mob() == null) {
            pull(player, start, end, distance);
        } else {
            pull(tether.body(), end, start, distance);
            tether.body().markForcedFall(player);
        }
    }

    public static Vec3D renderEndpoint(Player player, float partial) {
        Tether tether = player.grapple;
        return tether != null
                        && tether.level() == player.level
                        && isEquipped(player)
                        && canUse(player, player.level)
                        && validMobTether(tether)
                        && (tether.block() == null || stillAnchored(tether))
                ? tether.endpoint(partial)
                : null;
    }

    public static void detach(Player player) {
        Tether tether = player.grapple;
        player.grapple = null;
        if (tether != null && player.level == tether.level()) {
            player.level.playSound(SOUND + "release", player, .65f, 1f);
        }
    }

    private static boolean stillAnchored(Tether tether) {
        MovingObjectPosition hit = tether.block();
        return tether.level().getTile(hit.x, hit.y, hit.z) == tether.blockId();
    }

    private static boolean isEquipped(Player player) {
        return Item.GRAPPLING_HOOK != null
                && player.inventory.getSelected() == Item.GRAPPLING_HOOK.id + 256;
    }

    private static boolean canUse(Player player, Level level) {
        return player != null
                && level != null
                && player.level == level
                && !level.isNetworkMode()
                && !player.isSpectator()
                && !player.removed
                && player.health > 0;
    }

    private static boolean validMobTether(Tether tether) {
        return tether.mob() == null
                || JockeyToolTarget.body(tether.mob(), tether.level()) == tether.body()
                        && !GravityGunItem.grabbedMobs.containsValue(tether.body());
    }

    private static MovingObjectPosition trace(Level level, Vec3D start, Vec3D end) {
        float distance = start.distance(end);
        if (distance < .001f || !Float.isFinite(distance)) return null;
        // The classic ray stops after 21 cells. Short overlapping segments also cover diagonals.
        float dx = (end.x - start.x) / distance;
        float dy = (end.y - start.y) / distance;
        float dz = (end.z - start.z) / distance;
        for (float offset = 0; offset < distance; offset += 9) {
            float from = Math.max(0, offset - .02f);
            float to = Math.min(distance, offset + 9);
            MovingObjectPosition hit =
                    level.clip(
                            start.add(dx * from, dy * from, dz * from),
                            start.add(dx * to, dy * to, dz * to),
                            true);
            if (hit != null) return hit;
        }
        return null;
    }

    private static void pull(Mob body, Vec3D from, Vec3D to, float distance) {
        float dx = (to.x - from.x) / distance;
        float dy = (to.y - from.y) / distance;
        float dz = (to.z - from.z) / distance;
        float speed = body.xd * dx + body.yd * dy + body.zd * dz;
        float desired = Math.min(.9f, (distance - REST_DISTANCE) * .35f);
        float acceleration = Math.max(-.22f, Math.min(.22f, desired - speed));
        body.xd += dx * acceleration;
        body.yd += dy * acceleration;
        body.zd += dz * acceleration;
    }
}
