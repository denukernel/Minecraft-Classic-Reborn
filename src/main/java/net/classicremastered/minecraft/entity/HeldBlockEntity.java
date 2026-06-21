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

    public HeldBlockEntity(Level lvl, Player owner, int blockId, int bx, int by, int bz) {
        super(lvl);
        this.owner = owner;
        this.blockId = blockId;
        this.setSize(0.45f, 0.45f);
        this.heightOffset = 0.0f;
        this.makeStepSound = false;

        this.x = bx + 0.5f;
        this.y = by + 0.5f;
        this.z = bz + 0.5f;
        this.lastX = this.x; this.lastY = this.y; this.lastZ = this.z;
        this.setPos(this.x, this.y, this.z);

        recalcHeldTarget(true);
    }

    public boolean isHeld() { return state == State.HELD; }
    public Player getOwner() { return owner; }
    public void dropGently() {
        tryPlaceBlockSimple();
        this.removed = true;
    }

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
            if (owner == null || owner.removed || (owner.inventory.getSelected() - 256 != 11)) {
                dropGently();
                return;
            }
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

        // world contact via ray last->now or collision flags
        MovingObjectPosition hit = rayFromLast();
        if (hit != null || this.collision || this.onGround || this.horizontalCollision) {
            if (hit != null && hit.vec != null) { this.x = (float)hit.vec.x; this.y = (float)hit.vec.y; this.z = (float)hit.vec.z; }
            Block block = Block.blocks[blockId];
            if (block != null && level.particleEngine != null) {
                block.spawnBreakParticles(level, MathHelper.floor(this.x), MathHelper.floor(this.y), MathHelper.floor(this.z), level.particleEngine);
            }
            if (block != null && block.stepsound != null) {
                level.playSound(block.stepsound.pool, this.x, this.y, this.z, 0.8f, 1.0f);
            }
            if (hit != null) {
                tryPlaceAtImpact(hit);
            } else {
                tryPlaceBlockSimple();
            }
            this.removed = true;
            return;
        }

        // entity hit → 5 damage
        damageMobsOnHit(8);

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
                Block block = Block.blocks[blockId];
                if (block != null && level.particleEngine != null) {
                    block.spawnBreakParticles(level, MathHelper.floor(this.x), MathHelper.floor(this.y), MathHelper.floor(this.z), level.particleEngine);
                }
                if (block != null && block.stepsound != null) {
                    level.playSound(block.stepsound.pool, this.x, this.y, this.z, 0.8f, 1.0f);
                }
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

        float currentHoldDist = 3.5f;
        if (net.classicremastered.minecraft.level.itemstack.GravityGunItem.holdDists.containsKey(owner)) {
            currentHoldDist = net.classicremastered.minecraft.level.itemstack.GravityGunItem.holdDists.get(owner);
        } else {
            currentHoldDist = this.holdDist;
        }

        float ex = owner.x, ey = owner.y + owner.heightOffset, ez = owner.z;
        float yaw = owner.yRot * (float)Math.PI/180f;
        float pitch = owner.xRot * (float)Math.PI/180f;
        float fx = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float fy = -MathHelper.sin(pitch);
        float fz =  MathHelper.cos(yaw) * MathHelper.cos(pitch);

        targetX = ex + fx * currentHoldDist;
        targetY = ey + fy * currentHoldDist;
        targetZ = ez + fz * currentHoldDist;

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
        Block block = Block.blocks[blockId];

        float ex = this.lastX + (this.x - this.lastX) * partial;
        float ey = this.lastY + (this.y - this.lastY) * partial;
        float ez = this.lastZ + (this.z - this.lastZ) * partial;

        // Draw energy beam from player to block
        if (owner != null && state == State.HELD) {
            float px = owner.xo + (owner.x - owner.xo) * partial;
            float py = owner.yo + (owner.y - owner.yo) * partial + owner.heightOffset - 0.2f;
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

        GL11.glPushMatrix();
        GL11.glTranslatef(ex, ey, ez);
        GL11.glRotatef(this.yRotO + (this.yRot - this.yRotO) * partial, 0f, 1f, 0f);
        GL11.glScalef(0.98f, 0.98f, 0.98f); // slight shrink to avoid z-fight

        block.bindTexture(tm);

        ShapeRenderer sr = ShapeRenderer.instance;
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        block.renderPreview(sr);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    public int getBlockId() { return blockId; }
}
