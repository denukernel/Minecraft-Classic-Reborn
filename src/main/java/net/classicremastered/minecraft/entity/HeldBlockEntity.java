// com/mojang/minecraft/item/HeldBlockEntity.java
package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
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

    public enum State {
        HELD,
        THROWN,
        DROPPED
    }

    private final Player owner;
    private final int blockId;
    private State state = State.HELD;

    private float holdDist = 3.5f; // optional: keep a bit further away
    private float followLerp = 0.15f; // less aggressive snap
    private float gravity = 0.03f;
    private float drag = 0.98f;
    private int life = 20 * 20;

    private int age;
    private float targetX, targetY, targetZ;
    private float lastX, lastY, lastZ;

    // spin 10 rev / 5s => 36 deg per tick @20 TPS
    private static final float SPIN_DEG_PER_TICK = 36f;

    public HeldBlockEntity(Level lvl, Player owner, int blockId, int bx, int by, int bz) {
        super(lvl);
        this.owner = owner;
        this.blockId = blockId;
        this.setSize(0.98f, 0.98f);
        this.heightOffset = this.bbHeight / 2;
        this.makeStepSound = false;

        this.x = bx + 0.5f;
        this.y = by + 0.5f;
        this.z = bz + 0.5f;
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.setPos(this.x, this.y, this.z);

        recalcHeldTarget(true);
    }

    public boolean isHeld() {
        return state == State.HELD;
    }

    public void startAt(float x, float y, float z) {
        setPos(x, y, z);
        lastX = xo = x;
        lastY = yo = y;
        lastZ = zo = z;
    }

    public Player getOwner() {
        return owner;
    }

    public void dropGently() {
        if (removed || !isHeld()) return;
        state = State.DROPPED;
        xd = yd = zd = 0;
        age = 0;
        GravityGunItem.forgetBlock(owner, this);
    }

    public void throwNow(float power) {
        if (removed || !isHeld()) return;
        Vec3D direction =
                net.classicremastered.minecraft.level.itemstack.GravityGunItem.look(owner);
        this.xd = direction.x * power;
        this.yd = direction.y * power;
        this.zd = direction.z * power;
        state = State.THROWN;
        age = 0;
    }

    @Override
    public void tick() {
        if (removed) return;
        ++age;

        // spin every tick
        this.yRotO = this.yRot;
        this.yRot += SPIN_DEG_PER_TICK;

        lastX = this.x;
        lastY = this.y;
        lastZ = this.z;

        if (state == State.HELD) {
            if (owner == null || owner.removed || owner.health <= 0 || owner.level != level
                    || owner.inventory.getSelected() != GravityGunItem.RAW_ID) {
                dropGently();
                return;
            }
            recalcHeldTarget(true);

            this.xd *= 0.6f;
            this.yd *= 0.6f;
            this.zd *= 0.6f;
            this.xd += (targetX - this.x) * followLerp;
            this.yd += (targetY - this.y) * (followLerp * 0.8f);
            this.zd += (targetZ - this.z) * followLerp;

            this.move(this.xd, this.yd, this.zd);
            this.xo = lastX;
            this.yo = lastY;
            this.zo = lastZ;
            return;
        }

        this.yd -= gravity;
        this.move(this.xd, this.yd, this.zd);
        boolean submerged =
                level.containsLiquid(
                        bb, net.classicremastered.minecraft.level.liquid.LiquidType.WATER);
        this.xd *= submerged ? .75f : drag;
        this.yd *= submerged ? .8f : .98f;
        this.zd *= submerged ? .75f : drag;

        if (state == State.DROPPED) {
            if (onGround) {
                int bx = MathHelper.floor(x),
                        by = MathHelper.floor(bb.y0 + .01f),
                        bz = MathHelper.floor(z);
                AABB support = new AABB(bx + .01f, by - .02f, bz + .01f, bx + .99f, by, bz + .99f);
                if (level.getCubes(support).isEmpty() || !place(bx, by, bz)) dropPickup();
                removed = true;
            } else if (age > life) {
                dropPickup();
                removed = true;
            }
            return;
        }

        // world contact via ray last->now or collision flags
        MovingObjectPosition hit = rayFromLast();
        if (damageMobsOnHit(8)) return;
        if (hit != null || this.collision || this.onGround || this.horizontalCollision) {
            if (hit != null && hit.vec != null) {
                this.x = (float) hit.vec.x;
                this.y = (float) hit.vec.y;
                this.z = (float) hit.vec.z;
            }
            Block block = Block.blocks[blockId];
            if (block != null && level.particleEngine != null) {
                block.spawnBreakParticles(level, MathHelper.floor(this.x), MathHelper.floor(this.y),
                        MathHelper.floor(this.z), level.particleEngine);
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

        if (age > life) {
            tryPlaceBlockSimple();
            this.removed = true;
        }
    }

    private MovingObjectPosition rayFromLast() {
        if (level == null)
            return null;
        return level.clip(new Vec3D(lastX, lastY, lastZ), new Vec3D(this.x, this.y, this.z), true);
    }

    private void tryPlaceAtImpact(MovingObjectPosition mop) {
        if (level == null || blockId <= 0 || mop == null)
            return;
        int x = mop.x, y = mop.y, z = mop.z;
        switch (mop.face) { // 0/1=Y-,Y+ 2/3=Z-,Z+ 4/5=X-,X+
            case 0 :
                y--;
                break;
            case 1 :
                y++;
                break;
            case 2 :
                z--;
                break;
            case 3 :
                z++;
                break;
            case 4 :
                x--;
                break;
            case 5 :
                x++;
                break;
        }
        if (!place(x, y, z)) {
            tryPlaceBlockSimple();
        }
    }

    private boolean damageMobsOnHit(int dmg) {
        if (level == null)
            return false;
        Vec3D start = new Vec3D(lastX, lastY, lastZ), end = new Vec3D(x, y, z);
        MovingObjectPosition wall = level.clip(new Vec3D(lastX, lastY, lastZ), end, true);
        float nearest = wall == null ? Float.MAX_VALUE : start.distance(wall.vec);
        Mob target = null;
        List list = level.findEntities(this, this.bb.expand(lastX - x, lastY - y, lastZ - z).grow(.6f, .6f, .6f));
        for (int i = 0; i < list.size(); ++i) {
            Object o = list.get(i);
            if (!(o instanceof Entity)) continue;
            Entity e = (Entity) o;
            if (e == this || e == owner) continue;
            if (e instanceof Mob m && m.health > 0 && !m.removed) {
                AABB expanded = e.bb.grow(.5f, .5f, .5f);
                MovingObjectPosition intersection = expanded.clip(start, end);
                float distance =
                        expanded.intersects(
                                        new AABB(
                                                lastX - .01f,
                                                lastY - .01f,
                                                lastZ - .01f,
                                                lastX + .01f,
                                                lastY + .01f,
                                                lastZ + .01f))
                                ? 0
                                : intersection == null
                                        ? Float.MAX_VALUE
                                        : start.distance(intersection.vec);
                if (distance < nearest) {
                    nearest = distance;
                    target = m;
                }
            }
        }
        if (target != null) {
            float length = start.distance(end);
            if (length > .0001f) {
                float fraction = Math.min(1, nearest / length);
                setPos(
                        lastX + (x - lastX) * fraction,
                        lastY + (y - lastY) * fraction,
                        lastZ + (z - lastZ) * fraction);
            }
            target.hurt(this, dmg);
            float kb = 0.35f;
            target.xd += this.xd * kb;
            target.yd += 0.08f;
            target.zd += this.zd * kb;
            if (target.health > 0) target.markForcedFall(owner);
            Block block = Block.blocks[blockId];
            if (block != null && level.particleEngine != null) {
                block.spawnBreakParticles(level, MathHelper.floor(this.x), MathHelper.floor(this.y),
                        MathHelper.floor(this.z), level.particleEngine);
            }
            if (block != null && block.stepsound != null) {
                level.playSound(block.stepsound.pool, this.x, this.y, this.z, 0.8f, 1.0f);
            }
            // A mob impact shatters the projectile immediately, leaving one pickup.
            level.addEntity(new DroppedBlock(level, x, y, z, blockId));
            this.removed = true;
            return true;
        }
        return false;
    }

    private void tryPlaceBlockSimple() {
        if (level == null || blockId <= 0) return;
        int bx = MathHelper.floor(this.x);
        int by = MathHelper.floor(this.y + 0.1f); // slight lift
        int bz = MathHelper.floor(this.z);
        for (int dy = 0; dy <= 2; dy++) {
            int y = by + dy;
            if (place(bx, y, bz)) return;
        }
        dropPickup();
    }

    private void dropPickup() {
        float dropX = x, dropY = Math.max(.5f, Math.min(level.depth - 1f, y)), dropZ = z;
        if (!(level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat)
                && !(level instanceof net.classicremastered.minecraft.level.LevelInfiniteTerrain)) {
            dropX = Math.max(.5f, Math.min(level.width - .5f, dropX));
            dropZ = Math.max(.5f, Math.min(level.height - .5f, dropZ));
        }
        level.addEntity(new DroppedBlock(level, dropX, dropY, dropZ, blockId));
    }

    private boolean place(int x, int y, int z) {
        if (!inBounds(x, y, z)) return false;
        if (level.getTile(x, y, z) != 0 && !(state == State.THROWN && level.isWater(x, y, z)))
            return false;
        AABB box = new AABB(x, y, z, x + 1, y + 1, z + 1);
        if (owner != null && owner.level == level && !owner.removed && owner.bb.intersects(box))
            return false;
        for (Object o : level.findEntities(this, box))
            if (o instanceof Mob m && !m.removed && m.bb.intersects(box))
                return false;
        return level.setTile(x, y, z, blockId);
    }

    private void recalcHeldTarget(boolean trace) {
        if (owner == null)
            return;

        float currentHoldDist = 3.5f;
        if (net.classicremastered.minecraft.level.itemstack.GravityGunItem.holdDists.containsKey(owner)) {
            currentHoldDist = net.classicremastered.minecraft.level.itemstack.GravityGunItem.holdDists.get(owner);
        } else {
            currentHoldDist = this.holdDist;
        }

        Vec3D eye = net.classicremastered.minecraft.level.itemstack.GravityGunItem.eye(owner);
        Vec3D direction = net.classicremastered.minecraft.level.itemstack.GravityGunItem.look(owner);
        float ex = eye.x, ey = eye.y, ez = eye.z, fx = direction.x, fy = direction.y, fz = direction.z;

        targetX = ex + fx * currentHoldDist;
        targetY = ey + fy * currentHoldDist;
        targetZ = ez + fz * currentHoldDist;

        if (!trace || level == null)
            return;
        var mop = level.clip(new Vec3D(ex, ey, ez), new Vec3D(targetX, targetY, targetZ), true);
        if (mop != null && mop.vec != null) {
            float distance = Math.max(0, eye.distance(mop.vec) - .9f);
            targetX = ex + fx * distance;
            targetY = ey + fy * distance;
            targetZ = ez + fz * distance;
        }
    }

    private boolean inBounds(int x, int y, int z) {
        return level != null && level.isInBounds(x, y, z);
    }

    private static float invLen(float x, float y, float z) {
        float d = (float) Math.sqrt(x * x + y * y + z * z);
        return d > 1e-6f ? 1.0f / d : 0f;
    }

    // ===== simple renderer so you SEE the block =====
    @Override
    public void render(TextureManager tm, float partial) {
        if (blockId <= 0 || Block.blocks[blockId] == null)
            return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Block block = Block.blocks[blockId];

            float ex = this.lastX + (this.x - this.lastX) * partial;
            float ey = this.lastY + (this.y - this.lastY) * partial;
            float ez = this.lastZ + (this.z - this.lastZ) * partial;

            if (owner != null && state == State.HELD)
                net.classicremastered.minecraft.render.GunBeamRenderer.render(owner, this, partial, true);

            GL11.glPushMatrix();
            GL11.glTranslatef(ex, ey, ez);
            GL11.glRotatef(this.yRotO + (this.yRot - this.yRotO) * partial, 0f, 1f, 0f);
            GL11.glScalef(0.98f, 0.98f, 0.98f); // slight shrink to avoid z-fight

            block.bindTexture(tm);

            ShapeRenderer sr = ShapeRenderer.instance;
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
            net.classicremastered.minecraft.render.BlockItemRenderer.render(block, tm, getRenderBrightness(partial));
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glPopMatrix();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public int getBlockId() {
        return blockId;
    }
}
