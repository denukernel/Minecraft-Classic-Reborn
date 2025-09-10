package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.util.MathHelper;

import java.util.WeakHashMap;

/**
 * Centralized controller for "mob influences" on a Player:
 * - mind-control steering (pre-physics)
 * - forced actions (post-physics): TNT placement by camera ray, 1 feather/sec
 *
 * Usage from Player:
 *   MobControl.start(this, tx, ty, tz, ticks, nudge);
 *   if (mcTicks > 0) MobControl.prePhysicsSteer(this);
 *   if (mcTicks > 0) MobControl.postPhysicsActions(this);
 */
public final class MobControl {

    private MobControl() {}

    // Per-player transient state (cooldowns etc.)
    private static final class State {
        int tntCooldown;
        int featherCooldown;
    }

    private static final WeakHashMap<Player, State> STATE = new WeakHashMap<>();

    private static State S(Player p) {
        State s = STATE.get(p);
        if (s == null) {
            s = new State();
            STATE.put(p, s);
        }
        return s;
    }

    // ---- API ----

    public static void start(Player p, float tx, float ty, float tz, int ticks, float nudge) {
        if (p == null) return;
        p.mcTx = tx; p.mcTy = ty; p.mcTz = tz;
        p.mcTicks = Math.max(0, ticks);
        if (nudge > 0f) p.mcNudge = nudge;

        State s = S(p);
        s.tntCooldown = 0;
        s.featherCooldown = 0;
    }

    public static void end(Player p) {
        if (p == null) return;
        p.mcTicks = 0;
    }

    /** Call BEFORE physics in Player.aiStep() */
    public static void prePhysicsSteer(Player p) {
        if (p == null || p.mcTicks <= 0) return;

        float dx = p.mcTx - p.x, dz = p.mcTz - p.z;
        float d  = MathHelper.sqrt(dx*dx + dz*dz);
        if (d <= 0.001f) return;

        // face target subtly
        float targetYaw = (float)(Math.atan2(dz, dx) * 180.0/Math.PI) - 90.0F;
        float yawDelta  = targetYaw - p.yRot;
        while (yawDelta <= -180) yawDelta += 360;
        while (yawDelta >   180) yawDelta -= 360;
        p.yRot += clamp(yawDelta * 0.25f, -6f, 6f);

        // nudge horizontal
        float nx = dx / d, nz = dz / d;
        p.xd += nx * p.mcNudge;
        p.zd += nz * p.mcNudge;

        // hop small steps if target is higher
        if (p.mcTy > p.y + 0.1f && p.onGround) p.yd = 0.12f;
    }

    /** Call AFTER physics in Player.aiStep() */
    public static void postPhysicsActions(Player p) {
        if (p == null || p.mcTicks <= 0) return;
        State s = S(p);
        if (s.tntCooldown > 0)     s.tntCooldown--;
        if (s.featherCooldown > 0) s.featherCooldown--;

        tryPlaceTNTByLook(p, s);
        consumeOneFeatherPerSecond(p, s);
    }

    // ---- Actions ----

    private static void tryPlaceTNTByLook(Player p, State s) {
        if (p == null || p.level == null) return;

        // Only if we have TNT selected or in hotbar (auto-select)
        final int TNT = Block.TNT.id;
        if (!selectSlotWith(p, TNT)) return;
        if (s.tntCooldown > 0) return;

        // Camera forward (renderer basis)
        final float RAD = (float)Math.PI / 180.0f;
        float pyaw   = p.yRot * RAD;
        float ppitch = p.xRot * RAD;

        float lookX = -MathHelper.sin(pyaw) * MathHelper.cos(ppitch);
        float lookY = -MathHelper.sin(ppitch);
        float lookZ =  MathHelper.cos(pyaw) * MathHelper.cos(ppitch);

        Vec3D eye = new Vec3D(p.x, p.y + p.heightOffset, p.z);

        float reach = 5.0f;
        Minecraft mc = p.minecraft;
        if (mc != null && mc.gamemode != null) reach = mc.gamemode.getReachDistance();

        Vec3D end = eye.add(lookX * reach, lookY * reach, lookZ * reach);
        MovingObjectPosition hit = p.level.clip(eye, end);

        int px, py, pz;
        if (hit != null && hit.entityPos == 0) {
            px = hit.x; py = hit.y; pz = hit.z;
            switch (hit.face) {
                case 0: py -= 1; break;
                case 1: py += 1; break;
                case 2: pz -= 1; break;
                case 3: pz += 1; break;
                case 4: px -= 1; break;
                case 5: px += 1; break;
                default: return;
            }
        } else {
            float tx = p.x + lookX * 1.0f;
            float tz = p.z + lookZ * 1.0f;
            px = MathHelper.floor(tx);
            py = MathHelper.floor(p.y);
            pz = MathHelper.floor(tz);
        }

        if (!inBounds(p.level, px, py, pz)) return;
        if (p.level.getTile(px, py, pz) != 0) return; // air only

        if (p.level.netSetTile(px, py, pz, TNT)) {
            Block.blocks[TNT].onPlace(p.level, px, py, pz);
            if (!(mc != null && mc.gamemode instanceof net.classicremastered.minecraft.gamemode.CreativeGameMode)) {
                p.inventory.removeSelected(1);
            }
            p.level.playSound("step.stone", px + 0.5f, py + 0.5f, pz + 0.5f, 0.7f, 1.1f);
            s.tntCooldown = 10; // ~0.5s
        }
    }

    private static void consumeOneFeatherPerSecond(Player p, State s) {
        if (p == null || p.level == null) return;
        if (s.featherCooldown > 0) return;

        var FEATHER = net.classicremastered.minecraft.level.itemstack.Item.FEATHER;
        if (FEATHER == null) return;

        int fid = 256 + net.classicremastered.minecraft.level.itemstack.Item.FEATHER.id;
        int slot = findSlotWith(p, fid);
        if (slot < 0) return;

        p.inventory.selected = slot;
        FEATHER.use(p, p.level); // 1 HP in Survival; decrements 1

        if (p.inventory.count[slot] <= 0) {
            p.inventory.slots[slot] = -1;
            p.inventory.count[slot] = 0;
        }

        s.featherCooldown = 20; // 1 second @ 20 TPS
    }

    // ---- tiny helpers ----

    private static boolean inBounds(Level lvl, int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < lvl.width && y < lvl.depth && z < lvl.height;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int findSlotWith(Player p, int id) {
        if (p == null || p.inventory == null) return -1;
        for (int i = 0; i < p.inventory.slots.length; i++) {
            if (p.inventory.slots[i] == id && p.inventory.count[i] > 0) return i;
        }
        return -1;
    }

    private static boolean selectSlotWith(Player p, int id) {
        int s = findSlotWith(p, id);
        if (s >= 0) {
            p.inventory.selected = s;
            return true;
        }
        return false;
    }
}
