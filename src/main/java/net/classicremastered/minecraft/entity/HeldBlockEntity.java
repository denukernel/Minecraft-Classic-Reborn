// com/mojang/minecraft/item/HeldBlockEntity.java
package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

import java.util.List;

public final class HeldBlockEntity extends Entity {
    public static final long serialVersionUID = 0L;

    public enum State { HELD, THROWN }

    private final Player owner;
    private final int blockId;
    private State state = State.HELD;

    private float holdDist = 3.5f; // optional: keep a bit further away
    private float followLerp = 0.15f; // less aggressive snap
    private float gravity = 0.03f;
    private float drag = 0.98f;
    private int   life = 20 * 20;

    private int   age;
    private float targetX, targetY, targetZ;
    private float lastX, lastY, lastZ;

    // spin 10 rev / 5s => 36 deg per tick @20 TPS
    private static final float SPIN_DEG_PER_TICK = 36f;

    public HeldBlockEntity(Level lvl, Player owner, int blockId) {
        super(lvl);
        this.owner = owner;
        this.blockId = blockId;
        this.setSize(0.45f, 0.45f);
        this.heightOffset = 0.0f;
        this.makeStepSound = false;

        recalcHeldTarget(true);
        this.x = targetX; this.y = targetY; this.z = targetZ;
        this.lastX = this.x; this.lastY = this.y; this.lastZ = this.z;
    }

    public boolean isHeld() { return state == State.HELD; }

    public void throwNow(float power) {
        if (state == State.THROWN) return;
        recalcHeldTarget(false);
        float dx = targetX - this.x, dy = targetY - this.y, dz = targetZ - this.z;
        float inv = invLen(dx, dy, dz);
        this.xd = dx * inv * power;
        this.yd = dy * inv * power;
        this.zd = dz * inv * power;
        state = State.THROWN;
    }

    @Override
    public void tick() {
        ++age;

        // spin every tick
        this.yRotO = this.yRot;
        this.yRot += SPIN_DEG_PER_TICK;

        lastX = this.x; lastY = this.y; lastZ = this.z;

        if (state == State.HELD) {
            if (owner == null || owner.removed) { state = State.THROWN; }
            recalcHeldTarget(true);

            this.xd *= 0.6f; this.yd *= 0.6f; this.zd *= 0.6f;
            this.xd += (targetX - this.x) * followLerp;
            this.yd += (targetY - this.y) * (followLerp * 0.8f);
            this.zd += (targetZ - this.z) * followLerp;

            this.move(this.xd, this.yd, this.zd);
            return;
        }

        // THROWN
        this.yd -= gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= drag; this.yd *= 0.98f; this.zd *= drag;

        // world contact via ray last->now
        MovingObjectPosition hit = rayFromLast();
        if (hit != null) {
            if (hit.vec != null) { this.x = (float)hit.vec.x; this.y = (float)hit.vec.y; this.z = (float)hit.vec.z; }
            tryPlaceAtImpact(hit);
            this.removed = true;
            return;
        }

        // entity hit → 5 damage
        damageMobsOnHit(5);

        if (age > life) {
            tryPlaceBlockSimple();
            this.removed = true;
        }
    }

    private MovingObjectPosition rayFromLast() {
        if (level == null) return null;
        return level.clip(new Vec3D(lastX, lastY, lastZ), new Vec3D(this.x, this.y, this.z));
    }

    private void tryPlaceAtImpact(MovingObjectPosition mop) {
        if (level == null || blockId <= 0 || mop == null) return;
        int x = mop.x, y = mop.y, z = mop.z;
        switch (mop.face) { // 0/1=Y-,Y+ 2/3=Z-,Z+ 4/5=X-,X+
            case 0: y--; break; case 1: y++; break;
            case 2: z--; break; case 3: z++; break;
            case 4: x--; break; case 5: x++; break;
        }
        if (inBounds(x,y,z) && level.getTile(x,y,z) == 0) {
            level.netSetTile(x,y,z, blockId);
        } else {
            tryPlaceBlockSimple();
        }
    }

    private void damageMobsOnHit(int dmg) {
        if (level == null) return;
        float pad = 0.1f;
        List list = level.findEntities(this, this.bb.grow(pad, pad, pad));
        for (int i = 0; i < list.size(); ++i) {
            Object o = list.get(i);
            if (!(o instanceof Entity)) continue;
            Entity e = (Entity)o;
            if (e == this || e == owner) continue;
            if (e instanceof Mob || e instanceof Player) {
                e.hurt(this, dmg);
                float kb = 0.35f;
                e.xd += this.xd * kb; e.yd += 0.08f; e.zd += this.zd * kb;
                tryPlaceBlockSimple();
                this.removed = true;
                return;
            }
        }
    }

    private void tryPlaceBlockSimple() {
        if (level == null || blockId <= 0) return;
        int bx = MathHelper.floor(this.x);
        int by = MathHelper.floor(this.y + 0.1f);  // slight lift
        int bz = MathHelper.floor(this.z);
        for (int dy = 0; dy <= 2; dy++) {
            int y = by + dy;
            if (inBounds(bx, y, bz) && level.getTile(bx, y, bz) == 0) {
                level.netSetTile(bx, y, bz, blockId);
                return;
            }
        }
    }

    private void recalcHeldTarget(boolean trace) {
        if (owner == null) return;

        float ex = owner.x, ey = owner.y + owner.heightOffset, ez = owner.z;
        float yaw = owner.yRot * (float)Math.PI/180f;
        float pitch = owner.xRot * (float)Math.PI/180f;
        float fx = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float fy = -MathHelper.sin(pitch);
        float fz =  MathHelper.cos(yaw) * MathHelper.cos(pitch);

        targetX = ex + fx * holdDist;
        targetY = ey + fy * holdDist;
        targetZ = ez + fz * holdDist;

        if (!trace || level == null) return;
        var mop = level.clip(new Vec3D(ex, ey, ez), new Vec3D(targetX, targetY, targetZ));
        if (mop != null && mop.vec != null) {
            targetX = (float)mop.vec.x; targetY = (float)mop.vec.y; targetZ = (float)mop.vec.z;
        }
    }

    private boolean inBounds(int x,int y,int z){
        return level != null && x>=0 && y>=0 && z>=0 && x<level.width && y<level.depth && z<level.height;
    }

    private static float invLen(float x, float y, float z) {
        float d = (float)Math.sqrt(x*x + y*y + z*z);
        return d > 1e-6f ? 1.0f/d : 0f;
    }

    // ===== simple renderer so you SEE the block =====
    @Override
    public void render(TextureManager tm, float partial) {
        if (blockId <= 0 || Block.blocks[blockId] == null) return;

        GL11.glPushMatrix();
        GL11.glTranslatef(this.x, this.y, this.z);
        GL11.glRotatef(this.yRotO + (this.yRot - this.yRotO) * partial, 0f, 1f, 0f);
        GL11.glScalef(0.98f, 0.98f, 0.98f); // slight shrink to avoid z-fight

        // Try to bind terrain atlas if present
        int tex = 0;
        try { tex = tm.load("/terrain.png"); } catch (Throwable ignored) {}
        if (tex != 0) GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        // Draw a unit cube centered at origin with very simple UVs.
        // (You can swap this with your block renderer later.)
        ShapeRenderer sr = ShapeRenderer.instance;
        GL11.glDisable(GL11.GL_CULL_FACE);
        sr.begin(); // +X
        sr.vertexUV( 0.5f,-0.5f,-0.5f, 0,1);
        sr.vertexUV( 0.5f, 0.5f,-0.5f, 0,0);
        sr.vertexUV( 0.5f, 0.5f, 0.5f, 1,0);
        sr.vertexUV( 0.5f,-0.5f, 0.5f, 1,1);
        sr.end();

        sr.begin(); // -X
        sr.vertexUV(-0.5f,-0.5f, 0.5f, 0,1);
        sr.vertexUV(-0.5f, 0.5f, 0.5f, 0,0);
        sr.vertexUV(-0.5f, 0.5f,-0.5f, 1,0);
        sr.vertexUV(-0.5f,-0.5f,-0.5f, 1,1);
        sr.end();

        sr.begin(); // +Y
        sr.vertexUV(-0.5f, 0.5f,-0.5f, 0,1);
        sr.vertexUV(-0.5f, 0.5f, 0.5f, 0,0);
        sr.vertexUV( 0.5f, 0.5f, 0.5f, 1,0);
        sr.vertexUV( 0.5f, 0.5f,-0.5f, 1,1);
        sr.end();

        sr.begin(); // -Y
        sr.vertexUV( 0.5f,-0.5f,-0.5f, 0,1);
        sr.vertexUV( 0.5f,-0.5f, 0.5f, 0,0);
        sr.vertexUV(-0.5f,-0.5f, 0.5f, 1,0);
        sr.vertexUV(-0.5f,-0.5f,-0.5f, 1,1);
        sr.end();

        sr.begin(); // +Z
        sr.vertexUV(-0.5f,-0.5f, 0.5f, 0,1);
        sr.vertexUV( 0.5f,-0.5f, 0.5f, 1,1);
        sr.vertexUV( 0.5f, 0.5f, 0.5f, 1,0);
        sr.vertexUV(-0.5f, 0.5f, 0.5f, 0,0);
        sr.end();

        sr.begin(); // -Z
        sr.vertexUV( 0.5f,-0.5f,-0.5f, 0,1);
        sr.vertexUV(-0.5f,-0.5f,-0.5f, 1,1);
        sr.vertexUV(-0.5f, 0.5f,-0.5f, 1,0);
        sr.vertexUV( 0.5f, 0.5f,-0.5f, 0,0);
        sr.end();

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    public int getBlockId() { return blockId; }
}
