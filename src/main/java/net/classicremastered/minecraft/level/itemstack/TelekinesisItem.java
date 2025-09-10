package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;

import java.util.*;

/**
 * Physics Gun: grab, drag, drop. Extra features: - Slam damage on
 * wall/floor/ceiling impact while held - Throw with R - Adjust distance with
 * J/K - Post-release "armed" impact window so thrown/released mobs can still
 * take impact damage
 */
public final class TelekinesisItem extends ToolItem {

    // --- Config ---
    private static final float MAX_RANGE = 12.0f; // absolute hard cap
    private static final float STIFFNESS = 0.50f;
    private static final float DAMPING = 0.35f;
    private static final float SLAM_THRESH = 0.80f; // velocity magnitude that causes damage
    private static final int SLAM_DAMAGE = 4; // hearts lost when slammed
    private static final int ARMED_TICKS = 16; // post-release window to allow impact damage
    private static final float THROW_FORCE = 2.20f;
    public static final Map<Mob, Player> thrownBy = new WeakHashMap<>();
    // Per-player state
    private static final Map<Player, Mob> grabbed = new WeakHashMap<Player, Mob>();
    private static final Map<Player, Float> holdDist = new WeakHashMap<Player, Float>();

    // Post-release impact arming (mob -> ticks remaining)
    private static final Map<Mob, Integer> armed = new WeakHashMap<Mob, Integer>();

    public TelekinesisItem(int id) {
        super(id, "Physics Gun", "/items/physics_test.png");
    }

    public static Mob getGrabbed(Player p) {
        return grabbed.get(p);
    }

    public static void processArmedImpacts(Level level) {
        if (level == null || armed.isEmpty())
            return;

        // Copy keys to avoid concurrent modification if a mob removes itself
        List<Mob> keys = new ArrayList<Mob>(armed.keySet());
        for (int i = 0; i < keys.size(); i++) {
            Mob m = keys.get(i);
            if (m == null || m.removed) {
                armed.remove(m);
                thrownBy.remove(m); // also clean here
                continue;
            }

            // Decrement window
            Integer left = armed.get(m);
            if (left == null)
                continue;
            if (left <= 0) {
                armed.remove(m);
                continue;
            }

            // If moving fast and collided with wall/floor/ceiling, deal impact damage
            float vx = m.xd, vy = m.yd, vz = m.zd;
            float v2 = vx * vx + vy * vy + vz * vz;
            if (v2 > SLAM_THRESH * SLAM_THRESH) {
                if (m.horizontalCollision || m.onGround || hitCeiling(level, m)) {
                    // ✅ Prefer stored thrower, fallback to nearest player
                    Player killer = thrownBy.getOrDefault(m, level.getNearestPlayer(m.x, m.y, m.z, 8f));
                    m.hurt(killer, SLAM_DAMAGE);

                    float pitch = 1.6f + level.random.nextFloat() * 0.3f;
                    level.playSound("random/anvil_land", m, 0.8f, pitch);

                    armed.remove(m);
                    thrownBy.remove(m); // cleanup
                    continue;
                }
            }


            armed.put(m, left - 1);
        }
    }

    @Override
    public void use(Player player, Level level) {
        if (grabbed.containsKey(player)) {
            release(player, false); // drop // changed
            return;
        }

        Mob target = pickFromCrosshair(player);
        if (target == null)
            target = raycastMob(player, level, MAX_RANGE);
        if (target == null) {
            if (level != null)
                level.playSound("random/click", player, 0.5f, 0.9f);
            return;
        }

        grabbed.put(player, target); // added
        holdDist.put(player, 3.5f); // added
        if (level != null)
            level.playSound("random/pop", target, 0.8f, 1.1f);
    }

    @Override
    public void tick(Player player, Level level) {
        Mob m = grabbed.get(player);
        if (m == null)
            return;

        // --- Safe hold distance with J/K control ---
        float dist = holdDist.containsKey(player) ? holdDist.get(player) : 4.0f;
        if (Keyboard.isKeyDown(Keyboard.KEY_J))
            dist += 0.2f;
        if (Keyboard.isKeyDown(Keyboard.KEY_K))
            dist -= 0.2f;
        if (dist < 3.0f)
            dist = 3.0f; // minimum safe distance
        if (dist > MAX_RANGE)
            dist = MAX_RANGE;
        holdDist.put(player, dist);

        // --- Throw mechanic ---
        if (Keyboard.isKeyDown(Keyboard.KEY_R)) {
            throwMob(player, m);
            armForImpact(m);
            release(player, true);
            return;
        }

        // Auto-drop if dead/out of range
        if (m.removed || m.health <= 0 || tooFar(player, m, MAX_RANGE * 1.5f)) {
            armForImpact(m);
            release(player, true);
            return;
        }

        // --- Target hold point (crosshair forward) ---
        float yaw = player.yRot, pitch = player.xRot;
        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);

        float lx = sy * cp;
        float ly = sp;
        float lz = cy * cp;

        float eyeY = player.y + 0.12f;
        float tx = player.x + lx * dist;
        float ty = eyeY + ly * dist;
        float tz = player.z + lz * dist;

        // --- Apply force toward target instead of teleporting ---
        float dx = tx - m.x;
        float dy = ty - m.y;
        float dz = tz - m.z;

        // Spring-like force (keeps it tight but physical)
        float strength = 0.14f; // lower = floatier, higher = snappier
        m.xd += dx * strength;
        m.yd += dy * strength;
        m.zd += dz * strength;

        // Apply damping (to avoid wild oscillation)
        m.xd *= 0.8f;
        m.yd *= 0.8f;
        m.zd *= 0.8f;

        // Move with collisions → still respects walls + gravity
        m.move(m.xd, m.yd, m.zd);

        // Cancel fall damage but don’t disable gravity completely
        m.fallDistance = 0f;

        // --- Slam check (uses true velocity) ---
        float v2 = m.xd * m.xd + m.yd * m.yd + m.zd * m.zd;
        if (v2 > SLAM_THRESH * SLAM_THRESH) {
            if (m.horizontalCollision || m.onGround || hitCeiling(level, m)) {
                m.hurt(player, SLAM_DAMAGE);
                if (level != null) {
                    float pitchSnd = 1.6f + level.random.nextFloat() * 0.3f;
                    level.playSound("random/anvil_land", m, 0.8f, pitchSnd);
                }
            }
        }
    }

    @Override
    public void releaseUse(Player player, Level level) {
        // When releasing RMB without throwing, still arm a short window so an immediate
        // collision hurts
        Mob m = grabbed.get(player);
        if (m != null)
            armForImpact(m); // added
        release(player, false);
    }

    // === Helpers ===

    /** Apply forward impulse for throw. */
    private static void throwMob(Player player, Mob m) {
        float yaw = player.yRot, pitch = player.xRot;
        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);
        float lx = sy * cp, ly = sp, lz = cy * cp;

        m.xd += lx * THROW_FORCE;
        m.yd += ly * THROW_FORCE;
        m.zd += lz * THROW_FORCE;
        m.fallDistance = 0f;

        // ✅ Remember who threw this mob
        thrownBy.put(m, player);
    }


    /** Arm a mob for a brief post-release impact window. */
    private static void armForImpact(Mob m) {
        if (m == null)
            return;
        armed.put(m, ARMED_TICKS); // added
    }

    private static void release(Player player, boolean silent) {
        Mob m = grabbed.remove(player); // changed
        holdDist.remove(player); // changed
        if (m != null && !silent && m.level != null) {
            m.level.playSound("random/click", m, 0.6f, 0.9f);
        }
    }

    private static boolean tooFar(Player p, Entity e, float max) {
        float dx = e.x - p.x, dy = e.y - p.y, dz = e.z - p.z;
        return dx * dx + dy * dy + dz * dz > max * max;
    }

    private static Mob pickFromCrosshair(Player player) {
        if (player == null || player.minecraft == null)
            return null;
        MovingObjectPosition sel = player.minecraft.selected;
        if (sel == null || sel.entityPos != 1 || sel.entity == null)
            return null;
        if (!(sel.entity instanceof Mob))
            return null;
        Mob m = (Mob) sel.entity;
        return tooFar(player, m, MAX_RANGE) ? null : m;
    }

    private static Mob raycastMob(Player player, Level level, float range) {
        if (player == null || level == null || level.blockMap == null)
            return null;

        float yaw = player.yRot, pitch = player.xRot;
        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);
        float lx = sy * cp, ly = sp, lz = cy * cp;

        Vec3D start = new Vec3D(player.x, player.y + 0.12f, player.z);
        Vec3D end = start.add(lx * range, ly * range, lz * range);

        List list = level.blockMap.getEntities(player, player.bb.expand(lx * range, ly * range, lz * range));
        float best = 0f;
        Mob bestMob = null;

        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!(o instanceof Mob))
                continue;
            Mob m = (Mob) o;
            if (!m.isPickable())
                continue;
            net.classicremastered.minecraft.phys.AABB grown = m.bb.grow(0.15f, 0.15f, 0.15f);
            MovingObjectPosition hit = grown.clip(start, end);
            if (hit == null)
                continue;

            float dist = start.distance(hit.vec);
            if (dist < best || best == 0f) {
                best = dist;
                bestMob = m;
            }
        }
        return bestMob;
    }

    /** True if a solid block is immediately above the mob's head (ceiling hit). */
    private static boolean hitCeiling(Level level, Mob m) {
        if (level == null || m == null || m.bb == null)
            return false;

        final float eps = 0.02f;
        final int yAbove = (int) Math.floor(m.bb.y1 + eps);

        // sample 4 corners at top of the AABB
        float x0 = m.bb.x0 + 0.01f, x1 = m.bb.x1 - 0.01f;
        float z0 = m.bb.z0 + 0.01f, z1 = m.bb.z1 - 0.01f;

        int ix0 = (int) Math.floor(x0), ix1 = (int) Math.floor(x1);
        int iz0 = (int) Math.floor(z0), iz1 = (int) Math.floor(z1);

        // Check the four top cells
        return level.isSolidTile(ix0, yAbove, iz0) || level.isSolidTile(ix0, yAbove, iz1)
                || level.isSolidTile(ix1, yAbove, iz0) || level.isSolidTile(ix1, yAbove, iz1);
    }
}
