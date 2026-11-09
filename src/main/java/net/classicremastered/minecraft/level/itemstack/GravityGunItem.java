package net.classicremastered.minecraft.level.itemstack;

import java.util.*;
import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.entity.HeldBlockEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;
import org.lwjgl.input.Keyboard;

public final class GravityGunItem extends ToolItem {
    public static final Map<Player, Mob> grabbedMobs = new WeakHashMap<>();
    public static final Map<Player, Float> holdDists = new WeakHashMap<>();
    public static final Map<Mob, Player> thrownBy = new WeakHashMap<>();
    public static final Map<Mob, Integer> armedMobs = new WeakHashMap<>();
    private static final Map<Player, HeldBlockEntity> grabbedBlocks = new WeakHashMap<>();
    public GravityGunItem(int id) {
        super(id, "Gravity Gun", "/items/gravity_gun.png");
    }

    public static Vec3D look(Player p) {
        float yaw = -p.yRot * .017453292f - (float) Math.PI, pitch = -p.xRot * .017453292f, cp = MathHelper.cos(pitch);
        return new Vec3D(MathHelper.sin(yaw) * cp, MathHelper.sin(pitch), MathHelper.cos(yaw) * cp);
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
        return mob != null ? mob : GunEntityDrag.get(p, 267);
    }

    public static boolean isHoldingAnything(Player p) {
        return grabbedMobs.containsKey(p) || grabbedBlocks.containsKey(p) || GunEntityDrag.get(p, 267) != null;
    }

    public static void dropGently(Player p) {
        GunEntityDrag.release(p, 267, 0);
        Mob mob = grabbedMobs.remove(p);
        HeldBlockEntity block = grabbedBlocks.remove(p);
        holdDists.remove(p);
        if (mob != null) {
            mob.xd = mob.yd = mob.zd = 0;
            mob.fallDistance = 0;
        }
        if (block != null && !block.removed)
            block.dropGently();
    }

    public static void forgetBlock(Player p, HeldBlockEntity block) {
        if (grabbedBlocks.get(p) == block) {
            grabbedBlocks.remove(p);
            holdDists.remove(p);
        }
    }

    /**
     * Deliberately excludes containers, machines, attached blocks, bedrock and
     * special tiles.
     */
    public static boolean canGrabBlock(int id) {
        return id == 1 || id == 2 || id == 3 || id == 4 || id == 5 || id == 12 || id == 13 || id == 14 || id == 15
                || id == 16 || id == 17 || id == 20 || (id >= 21 && id <= 36) || id == 41 || id == 42 || id == 45
                || id == 46 || id == 47 || id == 48 || id == 49 || id == 220 || id == 221;
    }

    @Override
    public void use(Player p, Level level) {
        if (p == null || level == null || level.isNetworkMode() || p.health <= 0 || p.level != level)
            return;
        if (GunEntityDrag.get(p, 267) != null) {
            GunEntityDrag.release(p, 267, 3.2f);
            return;
        }
        HeldBlockEntity block = grabbedBlocks.remove(p);
        if (block != null && !block.removed) {
            block.throwNow(1.5f);
            holdDists.remove(p);
            return;
        }
        Mob held = grabbedMobs.remove(p);
        if (held != null) {
            Vec3D d = look(p);
            held.xd = d.x * 3.2f;
            held.yd = d.y * 3.2f;
            held.zd = d.z * 3.2f;
            held.fallDistance = 0;
            thrownBy.put(held, p);
            armedMobs.put(held, 20);
            holdDists.remove(p);
            return;
        }
        Vec3D start = eye(p), d = look(p), end = start.add(d.x * 12, d.y * 12, d.z * 12);
        // clip mutates its start vector, so always give it a copy.
        MovingObjectPosition wall = level.clip(new Vec3D(start.x, start.y, start.z), end, true);
        float nearest = wall == null ? 12 : start.distance(wall.vec);
        Mob target = null;
        if (level.blockMap != null)
            for (Object o : level.blockMap.getEntities(p,
                    p.bb.expand(d.x * 12, d.y * 12, d.z * 12).grow(.2f, .2f, .2f))) {
                if (!(o instanceof Mob m) || m instanceof Player || m.removed || m.health <= 0 || !m.isPickable()
                        || m.riding != null || m.rider != null || grabbedMobs.containsValue(m)
                        || PhysicsGunItem.isHeld(m))
                    continue;
                MovingObjectPosition hit = m.bb.grow(.15f, .15f, .15f).clip(start, end);
                if (hit != null && start.distance(hit.vec) < nearest) {
                    nearest = start.distance(hit.vec);
                    target = m;
                }
            }
        Entity loose = GunEntityDrag.pick(p, level, nearest);
        if (loose != null) {
            GunEntityDrag.grab(p, loose, 267);
            return;
        }
        if (target != null) {
            grabbedMobs.put(p, target);
            target.xd = target.yd = target.zd = 0;
            holdDists.put(p, Math.max(2.5f, nearest));
            return;
        }
        if (wall != null && canGrabBlock(level.getTile(wall.x, wall.y, wall.z))) {
            int id = level.getTile(wall.x, wall.y, wall.z);
            // Finite ocean borders replace air with water instead of allowing removal.
            boolean infinite = level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat
                    || level instanceof net.classicremastered.minecraft.level.LevelInfiniteTerrain;
            if (!infinite && (wall.x == 0 || wall.z == 0 || wall.x == level.width - 1 || wall.z == level.height - 1)
                    && wall.y >= level.getGroundLevel() && wall.y < level.getWaterLevel())
                return;
            // Neighbor updates can immediately refill the cell with falling sand or liquid.
            if (level.setTile(wall.x, wall.y, wall.z, 0)) {
                block = new HeldBlockEntity(level, p, id, wall.x, wall.y, wall.z);
                block.startAt(wall.vec.x - d.x * .55f, wall.vec.y - d.y * .55f, wall.vec.z - d.z * .55f);
                holdDists.put(p, Math.max(2.5f, Math.min(12, nearest)));
                grabbedBlocks.put(p, block);
                level.addEntity(block);
            }
        }
    }

    @Override
    public void tick(Player p, Level level) {
        boolean keys = Keyboard.isCreated();
        updateControls(p, level, keys && Keyboard.isKeyDown(Keyboard.KEY_J),
                keys && Keyboard.isKeyDown(Keyboard.KEY_K));
    }

    public void updateControls(Player p, Level level, boolean farther, boolean closer) {
        GunEntityDrag.controls(p, 267, farther, closer);
        if (p == null || !isHoldingAnything(p))
            return;
        if (p.removed || p.health <= 0 || p.level != level || p.inventory.getSelected() != 267) {
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
        if (p == null)
            return false;
        if (p.removed || p.health <= 0 || p.level != mob.level || p.inventory.getSelected() != 267 || mob.removed
                || mob.health <= 0 || p.distanceTo(mob) > 18) {
            dropGently(p);
            return false;
        }
        Vec3D start = eye(p), d = look(p);
        float distance = holdDists.getOrDefault(p, 3.5f);
        Vec3D target = start.add(d.x * distance, d.y * distance, d.z * distance);
        MovingObjectPosition wall = mob.level.clip(new Vec3D(start.x, start.y, start.z), target, true);
        if (wall != null) {
            distance = Math.max(0, start.distance(wall.vec) - Math.max(mob.bbWidth, mob.bbHeight) * .5f - .15f);
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
            if (p == null || p.removed || p.health <= 0 || p.level != level || p.inventory.getSelected() != 267)
                dropGently(p);
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
