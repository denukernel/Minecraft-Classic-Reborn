package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.entity.HeldBlockEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.StairBlock;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;

import java.util.*;

public final class GravityGunItem extends ToolItem {
    public static final int RAW_ID = 267;
    private static final float RANGE = 12;
    private static final String SOUND = "item/gravity_gun/";

    public static final Map<Player, Mob> grabbedMobs = new WeakHashMap<>();
    public static final Map<Player, Float> holdDists = new WeakHashMap<>();
    public static final Map<Mob, Player> thrownBy = new WeakHashMap<>();
    public static final Map<Mob, Integer> armedMobs = new WeakHashMap<>();
    private static final Map<Player, HeldBlockEntity> grabbedBlocks = new WeakHashMap<>();

    private record MobHit(Mob body, float distance) {}

    public GravityGunItem(int id) {
        super(id, "Gravity Gun", "/items/gravity_gun.png");
    }

    public static Vec3D look(Player p) {
        return p.aimDirection(1f);
    }

    public static Vec3D eye(Player p) {
        return new Vec3D(p.x, p.y + .12f, p.z);
    }

    public static HeldBlockEntity getGrabbedBlock(Player p) {
        return grabbedBlocks.get(p);
    }

    public static Mob getGrabbedMob(Player p) {
        return grabbedMobs.get(p);
    }

    public static Entity getGrabbedEntity(Player p) {
        Mob mob = getGrabbedMob(p);
        return mob != null ? mob : GunEntityDrag.get(p, RAW_ID);
    }

    public static boolean isHoldingAnything(Player p) {
        return grabbedMobs.containsKey(p)
                || grabbedBlocks.containsKey(p)
                || GunEntityDrag.get(p, RAW_ID) != null;
    }

    public static void dropGently(Player p) {
        GunEntityDrag.release(p, RAW_ID, 0);
        Mob mob = grabbedMobs.remove(p);
        HeldBlockEntity block = grabbedBlocks.remove(p);
        holdDists.remove(p);
        if (mob != null) {
            mob.xd = mob.yd = mob.zd = 0;
            mob.fallDistance = 0;
            mob.markForcedFall(p);
        }
        if (block != null && !block.removed) block.dropGently();
    }

    public static void forgetBlock(Player p, HeldBlockEntity block) {
        if (grabbedBlocks.get(p) == block) {
            grabbedBlocks.remove(p);
            holdDists.remove(p);
        }
    }

    /** Deliberately excludes containers, machines, attached blocks, bedrock and special tiles. */
    public static boolean canGrabBlock(int id) {
        if (id <= 0 || id >= Block.blocks.length) return false;
        Block block = Block.blocks[id];
        if (block == Block.SANDSTONE
                || block == Block.CUT_SANDSTONE
                || block == Block.CHISELED_SANDSTONE
                || block == Block.SMOOTH_SANDSTONE
                || block == Block.SANDSTONE_SLAB
                || block instanceof StairBlock && block.getDrop() == Block.SANDSTONE_STAIRS.id)
            return true;
        return id == 1
                || id == 2
                || id == 3
                || id == 4
                || id == 5
                || id == 12
                || id == 13
                || id == 14
                || id == 15
                || id == 16
                || id == 17
                || id == 20
                || (id >= 21 && id <= 36)
                || id == 41
                || id == 42
                || id == 45
                || id == 46
                || id == 47
                || id == 48
                || id == 49
                || id == 220
                || id == 221;
    }

    @Override
    public void use(Player p, Level level) {
        if (!canFire(p, level)) return;
        if (isHoldingAnything(p)) {
            dropGently(p);
            level.playSound(SOUND + "drop", p, .8f, 1f);
            return;
        }
        Vec3D start = eye(p), d = look(p);
        MovingObjectPosition wall = pickWall(level, start, d);
        if (wall != null
                && level.getCubes(
                                new net.classicremastered.minecraft.phys.AABB(
                                        wall.x + .001f,
                                        wall.y + .001f,
                                        wall.z + .001f,
                                        wall.x + .999f,
                                        wall.y + .999f,
                                        wall.z + .999f))
                        .isEmpty()) {
            wall =
                    level.clip(
                            new Vec3D(start.x, start.y, start.z),
                            start.add(d.x * RANGE, d.y * RANGE, d.z * RANGE),
                            true);
        }
        float nearest = wall == null ? RANGE : start.distance(wall.vec);
        MobHit hit = pickMob(p, level, start, d, nearest);
        if (hit != null) nearest = hit.distance();
        Entity loose = GunEntityDrag.pick(p, level, nearest);
        if (loose != null) {
            GunEntityDrag.grab(p, loose, RAW_ID);
            level.playSound(SOUND + "grab", p, .8f, 1f);
            return;
        }
        if (hit != null) {
            Mob target = hit.body();
            armedMobs.remove(target);
            thrownBy.remove(target);
            grabbedMobs.put(p, target);
            target.xd = target.yd = target.zd = 0;
            holdDists.put(p, Math.max(2.5f, nearest));
            level.playSound(SOUND + "grab", p, .8f, 1f);
            return;
        }
        if (wall != null && canGrabBlock(level.getTile(wall.x, wall.y, wall.z))) {
            int id = level.getTile(wall.x, wall.y, wall.z);
            // Finite ocean borders replace air with water instead of allowing removal.
            boolean infinite =
                    level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat
                            || level
                                    instanceof
                                    net.classicremastered.minecraft.level.LevelInfiniteTerrain;
            if (!infinite
                    && (wall.x == 0
                            || wall.z == 0
                            || wall.x == level.width - 1
                            || wall.z == level.height - 1)
                    && wall.y >= level.getGroundLevel()
                    && wall.y < level.getWaterLevel()) return;
            // Neighbor updates can immediately refill the cell with falling sand or liquid.
            if (level.setTile(wall.x, wall.y, wall.z, 0)) {
                HeldBlockEntity block = new HeldBlockEntity(level, p, id, wall.x, wall.y, wall.z);
                block.startAt(
                        wall.vec.x - d.x * .55f, wall.vec.y - d.y * .55f, wall.vec.z - d.z * .55f);
                holdDists.put(p, Math.max(2.5f, Math.min(12, nearest)));
                grabbedBlocks.put(p, block);
                level.addEntity(block);
                level.playSound(SOUND + "grab", p, .8f, 1f);
            }
        }
    }

    public static void launch(Player p, Level level) {
        if (!canFire(p, level)) return;
        level.playSound(SOUND + "launch", p, .9f, 1f);
        if (GunEntityDrag.get(p, RAW_ID) != null) {
            GunEntityDrag.release(p, RAW_ID, 3.2f);
            return;
        }
        HeldBlockEntity block = grabbedBlocks.remove(p);
        Mob held = grabbedMobs.remove(p);
        holdDists.remove(p);
        if (block != null && !block.removed) {
            block.throwNow(1.5f);
            return;
        }
        Vec3D d = look(p);
        if (held != null) {
            if (!JockeyToolTarget.validBody(held, level)) return;
            applyImpulse(held, launchDirection(p, held), 3.2f);
            held.horizontalCollision = held.onGround = false;
            held.markForcedFall(p);
            thrownBy.put(held, p);
            armedMobs.put(held, held instanceof Creeper ? 80 : 20);
            return;
        }
        Vec3D start = eye(p);
        MovingObjectPosition wall = pickWall(level, start, d);
        MobHit hit = pickMob(p, level, start, d, wall == null ? RANGE : start.distance(wall.vec));
        if (hit != null) {
            Mob target = hit.body();
            applyImpulse(target, d, 1.35f);
            target.yd = Math.max(.32f, target.yd);
            target.markForcedFall(p);
        }
    }

    public static Vec3D launchDirection(Player player, Entity projectile) {
        Vec3D start = new Vec3D(player.x, player.y, player.z);
        Vec3D aim = look(player);
        MovingObjectPosition wall = pickWall(player.level, start, aim);
        float distance = wall == null ? RANGE : start.distance(wall.vec);
        MobHit target = pickMob(player, player.level, start, aim, distance);
        if (target != null && target.body() != projectile) distance = target.distance();
        Vec3D point = start.add(aim.x * distance, aim.y * distance, aim.z * distance);
        Vec3D direction =
                point.subtract(
                        new Vec3D(
                                projectile.x,
                                (projectile.bb.y0 + projectile.bb.y1) * .5f,
                                projectile.z));
        return direction.x * direction.x + direction.y * direction.y + direction.z * direction.z
                        > .0001f
                ? direction.normalize()
                : aim;
    }

    private static boolean canFire(Player p, Level level) {
        return p != null
                && level != null
                && p.level == level
                && !level.isNetworkMode()
                && !p.removed
                && p.health > 0
                && !p.isSpectator();
    }

    private static void applyImpulse(Mob mob, Vec3D direction, float strength) {
        mob.xd = direction.x * strength;
        mob.yd = direction.y * strength;
        mob.zd = direction.z * strength;
        mob.fallDistance = 0;
    }

    private static MovingObjectPosition pickWall(Level level, Vec3D start, Vec3D direction) {
        // Level.clip mutates its start vector.
        return level.clip(
                new Vec3D(start.x, start.y, start.z),
                start.add(direction.x * RANGE, direction.y * RANGE, direction.z * RANGE));
    }

    private static MobHit pickMob(
            Player p, Level level, Vec3D start, Vec3D direction, float nearest) {
        if (level.blockMap == null) return null;
        Vec3D end = start.add(direction.x * RANGE, direction.y * RANGE, direction.z * RANGE);
        Mob target = null;
        for (Entity entity :
                level.blockMap.getEntities(
                        p,
                        p.bb.expand(direction.x * RANGE, direction.y * RANGE, direction.z * RANGE)
                                .grow(.2f, .2f, .2f))) {
            Mob body = JockeyToolTarget.body(entity, level);
            if (body == null || grabbedMobs.containsValue(body)) {
                continue;
            }
            MovingObjectPosition hit = entity.bb.grow(.15f, .15f, .15f).clip(start, end);
            if (hit != null
                    && start.distance(hit.vec) < nearest
                    && !JockeyToolTarget.isGrappled(body, level)) {
                nearest = start.distance(hit.vec);
                target = body;
            }
        }
        return target == null ? null : new MobHit(target, nearest);
    }

    @Override
    public void tick(Player p, Level level) {
        boolean keys = Keyboard.isCreated();
        updateControls(
                p,
                level,
                keys && Keyboard.isKeyDown(Keyboard.KEY_J),
                keys && Keyboard.isKeyDown(Keyboard.KEY_K));
    }

    public void updateControls(Player p, Level level, boolean farther, boolean closer) {
        GunEntityDrag.controls(p, RAW_ID, farther, closer);
        if (p == null || !isHoldingAnything(p)) return;
        if (p.removed || p.health <= 0 || p.level != level || p.inventory.getSelected() != RAW_ID) {
            dropGently(p);
            return;
        }
        float distance = holdDists.getOrDefault(p, 3.5f) + (farther ? .2f : 0) - (closer ? .2f : 0);
        holdDists.put(p, Math.max(2.5f, Math.min(12, distance)));
    }

    public static boolean moveHeld(Mob mob) {
        Player p = null;
        for (var entry : grabbedMobs.entrySet())
            if (entry.getValue() == mob) {
                p = entry.getKey();
                break;
            }
        if (p == null) return false;
        if (p.removed
                || p.health <= 0
                || p.level != mob.level
                || p.inventory.getSelected() != RAW_ID
                || !JockeyToolTarget.validBody(mob, p.level)
                || p.distanceTo(mob) > 18) {
            dropGently(p);
            return false;
        }
        Vec3D start = eye(p), d = look(p);
        float distance = holdDists.getOrDefault(p, 3.5f);
        Vec3D target = start.add(d.x * distance, d.y * distance, d.z * distance);
        MovingObjectPosition wall =
                mob.level.clip(new Vec3D(start.x, start.y, start.z), target, true);
        if (wall != null) {
            distance =
                    Math.max(
                            0,
                            start.distance(wall.vec)
                                    - Math.max(mob.bbWidth, mob.bbHeight) * .5f
                                    - .15f);
            target = start.add(d.x * distance, d.y * distance, d.z * distance);
        }
        mob.xd = mob.xd * .45f + (target.x - mob.x) * .22f;
        mob.yd = mob.yd * .45f + (target.y - (mob.bb.y0 + mob.bb.y1) * .5f) * .22f;
        mob.zd = mob.zd * .45f + (target.z - mob.z) * .22f;
        mob.move(mob.xd, mob.yd, mob.zd);
        mob.fallDistance = 0;
        return true;
    }

    public static void cleanup(Level level) {
        GunEntityDrag.cleanup(level);
        Set<Player> owners = new HashSet<>(grabbedMobs.keySet());
        owners.addAll(grabbedBlocks.keySet());
        for (Player p : owners)
            if (p == null
                    || p.removed
                    || p.health <= 0
                    || p.level != level
                    || p.inventory.getSelected() != RAW_ID) dropGently(p);
        for (Mob m : new ArrayList<>(armedMobs.keySet())) {
            int ticks = armedMobs.get(m);
            if (!m.removed && m.health > 0 && m.level == level && ticks > 0) {
                if (m.horizontalCollision || m.onGround) {
                    m.hurt(thrownBy.get(m), 4);
                    ticks = 0;
                } else {
                    armedMobs.put(m, ticks - 1);
                    continue;
                }
            }
            armedMobs.remove(m);
            thrownBy.remove(m);
        }
    }

    public static void renderMobBeam(Player p, Mob mob, TextureManager tm, float partial) {
        net.classicremastered.minecraft.render.GunBeamRenderer.render(p, mob, partial, true);
    }
}
