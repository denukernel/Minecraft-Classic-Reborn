package net.classicremastered.minecraft.level.itemstack;

import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

/** Hold RMB to grab, release to drop, LMB/G to launch, J/K to adjust distance. */
public final class PhysicsGunItem extends ToolItem {
    public static final float MAX_RANGE = 12f;
    private static final float MIN_DISTANCE = 3f;
    private static final float THROW_FORCE = 2.2f;
    private static final int ARMED_TICKS = 16;
    public static float cameraShake;
    public static final Map<Mob, Player> thrownBy = new WeakHashMap<>();
    private static final Map<Player, Mob> grabbed = new WeakHashMap<>();
    private static final Map<Player, Float> holdDist = new WeakHashMap<>();
    private static final Map<Mob, Integer> armed = new WeakHashMap<>();
    private static final Map<Mob, Float> impactSpeed = new WeakHashMap<>();

    public PhysicsGunItem(int id) {
        super(id, "Physics Gun", "/items/physics_gun.png");
    }

    public static Mob getGrabbed(Player player) { return grabbed.get(player); }

    public static void tickShake() { cameraShake = Math.max(0, cameraShake - .05f); }

    private static Vec3D look(Player player) {
        float yaw = -player.yRot * .017453292f - (float) Math.PI;
        float pitch = -player.xRot * .017453292f;
        float cp = MathHelper.cos(pitch);
        return new Vec3D(MathHelper.sin(yaw) * cp, MathHelper.sin(pitch), MathHelper.cos(yaw) * cp);
    }

    private static Vec3D eye(Player player) { return new Vec3D(player.x, player.y + .12f, player.z); }

    private static boolean valid(Player player, Mob mob, Level level) {
        return player != null && mob != null && level != null && player.level == level
            && mob.level == level && !player.removed && player.health > 0 && !mob.removed
            && mob.health > 0 && mob.rider == null && mob.riding == null;
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null) return;
        if (grabbed.containsKey(player)) { drop(player); return; }
        Mob target = raycastMob(player, level);
        if (target == null) { level.playSound("random/click", player, .5f, .9f); return; }
        if (grabbed.containsValue(target)) return;
        // The two tools must not apply competing forces to the same mob.
        if (GravityGunItem.grabbedMobs.containsValue(target)) return;
        grabbed.put(player, target);
        holdDist.put(player, Math.max(MIN_DISTANCE, Math.min(MAX_RANGE,
            eye(player).distance(new Vec3D(target.x, (target.bb.y0 + target.bb.y1) * .5f, target.z)))));
        armed.remove(target); impactSpeed.remove(target); thrownBy.remove(target);
        target.xd = target.yd = target.zd = 0;
        target.fallDistance = 0;
        level.playSound("random/pop", target, .7f, 1.2f);
    }

    @Override
    public void tick(Player player, Level level) {
        boolean keyboard = Keyboard.isCreated();
        updateControls(player, level, keyboard && Keyboard.isKeyDown(Keyboard.KEY_J),
            keyboard && Keyboard.isKeyDown(Keyboard.KEY_K), keyboard && Keyboard.isKeyDown(Keyboard.KEY_G));
    }

    /** Input is separate from physics so movement happens exactly once in Mob.tick. */
    public void updateControls(Player player, Level level, boolean farther, boolean closer, boolean launch) {
        Mob mob = grabbed.get(player);
        if (mob == null) return;
        if (!valid(player, mob, level) || player.distanceTo(mob) > MAX_RANGE * 1.5f) { drop(player); return; }
        float distance = holdDist.getOrDefault(player, MIN_DISTANCE);
        distance += (farther ? .2f : 0) - (closer ? .2f : 0);
        holdDist.put(player, Math.max(MIN_DISTANCE, Math.min(MAX_RANGE, distance)));
        if (launch) launch(player);
    }

    public static boolean isHeld(Mob mob) { return grabbed.containsValue(mob); }

    /** Replaces walking/gravity while held; collision resolution remains Entity.move. */
    public static boolean moveHeld(Mob mob) {
        Player owner = null;
        for (Map.Entry<Player, Mob> entry : grabbed.entrySet()) if (entry.getValue() == mob) { owner = entry.getKey(); break; }
        if (owner == null) return false;
        if (!valid(owner, mob, mob.level)) { drop(owner); return false; }
        Vec3D start = eye(owner), direction = look(owner);
        float distance = holdDist.getOrDefault(owner, MIN_DISTANCE);
        Vec3D target = start.add(direction.x * distance, direction.y * distance, direction.z * distance);
        // Level.clip advances its start vector in place; retain the original eye position.
        MovingObjectPosition wall = mob.level.clip(new Vec3D(start.x, start.y, start.z), target, true);
        if (wall != null) {
            distance = Math.max(0, start.distance(wall.vec) - Math.max(mob.bbWidth, mob.bbHeight) * .5f - .15f);
            target = start.add(direction.x * distance, direction.y * distance, direction.z * distance);
        }
        // A damped spring follows the aim without teleporting or fighting the mob AI.
        mob.xd = mob.xd * .45f + (target.x - mob.x) * .22f;
        mob.yd = mob.yd * .45f + (target.y - (mob.bb.y0 + mob.bb.y1) * .5f) * .22f;
        mob.zd = mob.zd * .45f + (target.z - mob.z) * .22f;
        float speed = (float) Math.sqrt(mob.xd * mob.xd + mob.yd * mob.yd + mob.zd * mob.zd);
        if (speed > .9f) { float scale = .9f / speed; mob.xd *= scale; mob.yd *= scale; mob.zd *= scale; }
        mob.fallDistance = 0;
        mob.move(mob.xd, mob.yd, mob.zd);
        mob.fallDistance = 0;
        if (owner.tickCount % 12 == 0) mob.level.playSound("random/fuse", mob, .12f, 1.2f);
        return true;
    }

    public static void drop(Player player) {
        Mob mob = grabbed.remove(player);
        holdDist.remove(player);
        if (mob == null) return;
        mob.xd = mob.yd = mob.zd = 0;
        mob.fallDistance = 0;
        armed.remove(mob); impactSpeed.remove(mob); thrownBy.remove(mob);
    }

    @Override
    public void releaseUse(Player player, Level level) { drop(player); }

    public static void launch(Player player) {
        Mob mob = grabbed.get(player);
        if (mob == null || !valid(player, mob, player.level)) { drop(player); return; }
        grabbed.remove(player); holdDist.remove(player);
        Vec3D direction = look(player);
        mob.xd = direction.x * THROW_FORCE;
        mob.yd = direction.y * THROW_FORCE;
        mob.zd = direction.z * THROW_FORCE;
        mob.fallDistance = 0;
        thrownBy.put(mob, player);
        armed.put(mob, ARMED_TICKS);
        impactSpeed.put(mob, THROW_FORCE * THROW_FORCE);
        mob.level.playSound("random/explode", player, .6f, 1.7f);
    }

    /** Capture velocity before collision resolution zeroes the blocked components. */
    public static void beforeMobTick(Mob mob) {
        if (armed.containsKey(mob)) impactSpeed.put(mob, mob.xd * mob.xd + mob.yd * mob.yd + mob.zd * mob.zd);
    }

    public static void processArmedImpacts(Level level) {
        tickShake();
        for (Player player : new ArrayList<>(grabbed.keySet())) {
            Mob mob = grabbed.get(player);
            if (!valid(player, mob, level) || player.inventory.getSelected() != 266) drop(player);
        }
        for (Mob mob : new ArrayList<>(armed.keySet())) {
            if (mob == null) continue;
            if (mob.level != level || mob.removed || mob.health <= 0) { disarm(mob); continue; }
            int left = armed.getOrDefault(mob, 0);
            if (left <= 0) { disarm(mob); continue; }
            float speed = impactSpeed.getOrDefault(mob, 0f);
            if (speed > .8f * .8f && (mob.horizontalCollision || mob.collision || mob.onGround)) {
                mob.hurt(thrownBy.get(mob), 4);
                cameraShake = .25f;
                level.playSound("random/anvil_land", mob, .7f, 1.7f);
                disarm(mob);
            } else armed.put(mob, left - 1);
        }
    }

    private static void disarm(Mob mob) {
        armed.remove(mob); impactSpeed.remove(mob); thrownBy.remove(mob);
    }

    private static Mob raycastMob(Player player, Level level) {
        if (level.blockMap == null) return null;
        Vec3D start = eye(player), direction = look(player);
        Vec3D end = start.add(direction.x * MAX_RANGE, direction.y * MAX_RANGE, direction.z * MAX_RANGE);
        MovingObjectPosition wall = level.clip(new Vec3D(start.x, start.y, start.z), end, true);
        float nearest = wall == null ? MAX_RANGE : start.distance(wall.vec);
        Mob result = null;
        for (Object object : level.blockMap.getEntities(player,
                player.bb.expand(direction.x * MAX_RANGE, direction.y * MAX_RANGE, direction.z * MAX_RANGE).grow(.2f, .2f, .2f))) {
            if (!(object instanceof Mob mob) || mob instanceof Player || !valid(player, mob, level) || !mob.isPickable()) continue;
            MovingObjectPosition hit = mob.bb.grow(.15f, .15f, .15f).clip(start, end);
            if (hit != null && start.distance(hit.vec) < nearest) { nearest = start.distance(hit.vec); result = mob; }
        }
        return result;
    }

    public static void renderMobBeam(Player owner, Mob mob, TextureManager tm, float partial) {
        net.classicremastered.minecraft.render.GunBeamRenderer.render(owner, mob, partial, false);
    }
}
