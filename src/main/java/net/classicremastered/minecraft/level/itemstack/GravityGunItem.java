package net.classicremastered.minecraft.level.itemstack;
 
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.entity.HeldBlockEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import org.lwjgl.opengl.GL11;
 
public final class GravityGunItem extends ToolItem {
 
    public static final java.util.Map<Player, net.classicremastered.minecraft.mob.Mob> grabbedMobs = new java.util.WeakHashMap<>();
    public static final java.util.Map<Player, Float> holdDists = new java.util.WeakHashMap<>();
    public static final java.util.Map<net.classicremastered.minecraft.mob.Mob, Player> thrownBy = new java.util.WeakHashMap<>();
    public static final java.util.Map<net.classicremastered.minecraft.mob.Mob, Integer> armedMobs = new java.util.WeakHashMap<>();
 
    public GravityGunItem(int id) {
        super(id, "Gravity Gun", "/items/gravity_gun.png");
    }
 
    public static boolean isHoldingAnything(Player p) {
        if (p == null) return false;
        return grabbedMobs.containsKey(p);
    }
 
    public static void dropGently(Player p) {
        if (p == null) return;
        if (grabbedMobs.containsKey(p)) {
            net.classicremastered.minecraft.mob.Mob mob = grabbedMobs.remove(p);
            holdDists.remove(p);
            if (mob != null && mob.level != null) {
                mob.level.playSound("random/click", mob, 0.6f, 0.9f);
                mob.xd = 0f; mob.yd = 0f; mob.zd = 0f;
            }
            hud(p, "&7Mob released gently!");
        }
    }
 
    public static net.classicremastered.minecraft.mob.Mob getGrabbedMob(Player p) {
        return grabbedMobs.get(p);
    }
 
    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null) return;
 
        // If holding a mob, throw it
        net.classicremastered.minecraft.mob.Mob grabbed = grabbedMobs.get(player);
        if (grabbed != null) {
            throwMob(player, grabbed);
            grabbedMobs.remove(player);
            holdDists.remove(player);
            hud(player, "&7Mob launched!");
            return;
        }
 
        // Pick mob
        net.classicremastered.minecraft.mob.Mob targetMob = pickMob(player, level, 12.0f);
        if (targetMob != null) {
            grabbedMobs.put(player, targetMob);
            holdDists.put(player, 3.5f);
            level.playSound("random/pop", targetMob, 0.8f, 1.1f);
            hud(player, "&7Mob grabbed!");
            return;
        }
 
        level.playSound("random/click", player, 0.5f, 0.9f);
    }
 
    @Override
    public void tick(Player player, Level level) {
        if (player == null || level == null) return;
 
        // Update hold distance for J/K control
        float dist = holdDists.containsKey(player) ? holdDists.get(player) : 3.5f;
        if (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_J)) dist += 0.2f;
        if (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_K)) dist -= 0.2f;
        if (dist < 2.5f) dist = 2.5f;
        if (dist > 12.0f) dist = 12.0f;
        holdDists.put(player, dist);
 
        net.classicremastered.minecraft.mob.Mob m = grabbedMobs.get(player);
        if (m != null) {
            if (m.removed || m.health <= 0 || tooFar(player, m, 18.0f)) {
                grabbedMobs.remove(player);
                holdDists.remove(player);
                return;
            }
 
            float yaw = player.yRot, pitch = player.xRot;
            float cy = net.classicremastered.util.MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
            float sy = net.classicremastered.util.MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
            float cp = net.classicremastered.util.MathHelper.cos(-pitch * 0.017453292F);
            float sp = net.classicremastered.util.MathHelper.sin(-pitch * 0.017453292F);
 
            float lx = sy * cp;
            float ly = sp;
            float lz = cy * cp;
 
            float eyeY = player.y + 0.12f;
            float tx = player.x + lx * dist;
            float ty = eyeY + ly * dist;
            float tz = player.z + lz * dist;
 
            float dx = tx - m.x;
            float dy = ty - m.y;
            float dz = tz - m.z;
 
            float strength = 0.18f;
            m.xd += dx * strength;
            m.yd += dy * strength;
            m.zd += dz * strength;
 
            m.xd *= 0.78f;
            m.yd *= 0.78f;
            m.zd *= 0.78f;
 
            m.move(m.xd, m.yd, m.zd);
            m.fallDistance = 0f;
 
            float v2 = m.xd * m.xd + m.yd * m.yd + m.zd * m.zd;
            if (v2 > 0.64f) {
                if (m.horizontalCollision || m.onGround || hitCeiling(level, m)) {
                    m.hurt(player, 4);
                    level.playSound("random/anvil_land", m, 0.8f, 1.6f + level.random.nextFloat() * 0.3f);
                }
            }
        }
    }
 
    private static void throwMob(Player player, net.classicremastered.minecraft.mob.Mob m) {
        float yaw = player.yRot, pitch = player.xRot;
        float cy = net.classicremastered.util.MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = net.classicremastered.util.MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = net.classicremastered.util.MathHelper.cos(-pitch * 0.017453292F);
        float sp = net.classicremastered.util.MathHelper.sin(-pitch * 0.017453292F);
        float lx = sy * cp, ly = sp, lz = cy * cp;
 
        float force = 3.20f;
        m.xd += lx * force;
        m.yd += ly * force;
        m.zd += lz * force;
        m.fallDistance = 0f;
 
        thrownBy.put(m, player);
        armedMobs.put(m, 20);
        if (m.level != null) {
            m.level.playSound("random/explode", player, 0.8f, 1.6f + m.level.random.nextFloat() * 0.2f);
        }
    }
 
    private static net.classicremastered.minecraft.mob.Mob pickMob(Player player, Level level, float range) {
        if (player == null || player.minecraft == null) return null;
        MovingObjectPosition sel = player.minecraft.selected;
        if (sel != null && sel.entityPos == 1 && sel.entity instanceof net.classicremastered.minecraft.mob.Mob) {
            net.classicremastered.minecraft.mob.Mob m = (net.classicremastered.minecraft.mob.Mob) sel.entity;
            if (!tooFar(player, m, range)) return m;
        }
 
        if (level == null || level.blockMap == null) return null;
        float yaw = player.yRot, pitch = player.xRot;
        float cy = net.classicremastered.util.MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = net.classicremastered.util.MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = net.classicremastered.util.MathHelper.cos(-pitch * 0.017453292F);
        float sp = net.classicremastered.util.MathHelper.sin(-pitch * 0.017453292F);
        float lx = sy * cp, ly = sp, lz = cy * cp;
 
        Vec3D start = new Vec3D(player.x, player.y + 0.12f, player.z);
        Vec3D end = start.add(lx * range, ly * range, lz * range);
 
        java.util.List list = level.blockMap.getEntities(player, player.bb.expand(lx * range, ly * range, lz * range));
        float best = 0f;
        net.classicremastered.minecraft.mob.Mob bestMob = null;
 
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!(o instanceof net.classicremastered.minecraft.mob.Mob)) continue;
            net.classicremastered.minecraft.mob.Mob m = (net.classicremastered.minecraft.mob.Mob) o;
            if (!m.isPickable()) continue;
            AABB grown = m.bb.grow(0.15f, 0.15f, 0.15f);
            MovingObjectPosition hit = grown.clip(start, end);
            if (hit == null) continue;
 
            float dist = start.distance(hit.vec);
            if (dist < best || best == 0f) {
                best = dist;
                bestMob = m;
            }
        }
        return bestMob;
    }
 
    private static HeldBlockEntity findHeld(Level lvl, Player p) {
        if (lvl == null || p == null) return null;
        var list = lvl.findEntities(p, p.bb.grow(16, 16, 16));
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof HeldBlockEntity) {
                HeldBlockEntity hb = (HeldBlockEntity) o;
                if (hb.isHeld() && hb.getOwner() == p) return hb;
            }
        }
        return null;
    }
 
    private static int[] pickBlock(Level lvl, Player p, float maxDist) {
        float ex = p.x, ey = p.y + p.heightOffset, ez = p.z;
        float yaw = p.yRot * (float) Math.PI / 180f;
        float pitch = p.xRot * (float) Math.PI / 180f;
 
        float fx = -net.classicremastered.util.MathHelper.sin(yaw) * net.classicremastered.util.MathHelper.cos(pitch);
        float fy = -net.classicremastered.util.MathHelper.sin(pitch);
        float fz = net.classicremastered.util.MathHelper.cos(yaw) * net.classicremastered.util.MathHelper.cos(pitch);
 
        var mop = lvl.clip(new Vec3D(ex, ey, ez), new Vec3D(ex + fx * maxDist, ey + fy * maxDist, ez + fz * maxDist));
        if (mop == null) return null;
        int x = mop.x, y = mop.y, z = mop.z;
        return new int[]{x, y, z, lvl.getTile(x, y, z)};
    }
 
    private static void hud(Player p, String msg) {
        if (p != null && p.level != null && p.level.minecraft != null) {
            p.level.minecraft.hud.addChat(msg);
        }
    }
 
    private static boolean tooFar(Player p, Entity e, float max) {
        float dx = e.x - p.x, dy = e.y - p.y, dz = e.z - p.z;
        return dx * dx + dy * dy + dz * dz > max * max;
    }
 
    private static boolean hitCeiling(Level level, net.classicremastered.minecraft.mob.Mob m) {
        if (level == null || m == null || m.bb == null) return false;
        final float eps = 0.02f;
        final int yAbove = (int) Math.floor(m.bb.y1 + eps);
        float x0 = m.bb.x0 + 0.01f, x1 = m.bb.x1 - 0.01f;
        float z0 = m.bb.z0 + 0.01f, z1 = m.bb.z1 - 0.01f;
        int ix0 = (int) Math.floor(x0), ix1 = (int) Math.floor(x1);
        int iz0 = (int) Math.floor(z0), iz1 = (int) Math.floor(z1);
        return level.isSolidTile(ix0, yAbove, iz0) || level.isSolidTile(ix0, yAbove, iz1)
                || level.isSolidTile(ix1, yAbove, iz0) || level.isSolidTile(ix1, yAbove, iz1);
    }
 
    public static void renderMobBeam(Player owner, net.classicremastered.minecraft.mob.Mob mob, TextureManager tm, float partial) {
        if (owner == null || mob == null) return;
 
        float ex = mob.xo + (mob.x - mob.xo) * partial;
        float ey = mob.yo + (mob.y - mob.yo) * partial - mob.heightOffset + mob.bbHeight / 2.0F;
        float ez = mob.zo + (mob.z - mob.zo) * partial;
 
        float px = owner.xo + (owner.x - owner.xo) * partial;
        float py = owner.yo + (owner.y - owner.yo) * partial - 0.2f;
        float pz = owner.zo + (owner.z - owner.zo) * partial;
 
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
 
        // Outer glowing beam
        GL11.glColor4f(0.0f, 0.6f, 1.0f, 0.4f);
        GL11.glLineWidth(8.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(ex, ey, ez);
        GL11.glVertex3f(px, py, pz);
        GL11.glEnd();
 
        // Inner bright core
        GL11.glColor4f(0.8f, 0.95f, 1.0f, 0.9f);
        GL11.glLineWidth(3.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(ex, ey, ez);
        GL11.glVertex3f(px, py, pz);
        GL11.glEnd();
 
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
}
