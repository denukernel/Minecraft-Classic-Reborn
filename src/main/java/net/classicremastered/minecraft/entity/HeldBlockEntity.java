// com/mojang/minecraft/item/HeldBlockEntity.java
package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.BlockItemRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

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
        startAt(this.x, this.y, this.z);

        recalcHeldTarget(true);
    }

    public boolean isHeld() {
        return state == State.HELD;
    }

    public void startAt(float x, float y, float z) {
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
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
        Vec3D direction = GravityGunItem.launchDirection(owner, this);
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

        xo = x;
        yo = y;
        zo = z;
        yRotO = yRot;

        if (state == State.THROWN) {
            tickThrown();
            return;
        }

        if (state == State.HELD) {
            yRot += SPIN_DEG_PER_TICK;
            if (owner == null
                    || owner.removed
                    || owner.health <= 0
                    || owner.level != level
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

        if (onGround) {
            int bx = MathHelper.floor(x),
                    by = MathHelper.floor(bb.y0 + .01f),
                    bz = MathHelper.floor(z);
            AABB support = new AABB(bx + .01f, by - .02f, bz + .01f, bx + .99f, by, bz + .99f);
            if (level.getCubes(support).isEmpty() || !place(bx, by, bz)) dropPickup();
            remove();
        } else if (age > life) {
            dropPickup();
            remove();
        }
    }

    private void tickThrown() {
        ProjectileSweep.Impact impact = ProjectileSweep.move(this, owner);
        if (impact == null) {
            if (age <= life) return;
            tryPlaceBlockSimple();
            remove();
            return;
        }

        Mob target = impact.mob();
        boolean dodged =
                target instanceof net.classicremastered.minecraft.mob.Enderman enderman
                        && enderman.tryEvadeProjectile();
        if (blockId == Block.TNT.id) {
            PrimedTnt.detonate(this, owner, 4f);
            return;
        }

        Block block = Block.blocks[blockId];
        if (level.particleEngine != null) {
            block.spawnBreakParticles(
                    level,
                    MathHelper.floor(x),
                    MathHelper.floor(y),
                    MathHelper.floor(z),
                    level.particleEngine);
        }
        if (block.stepsound != null) {
            level.playSound(block.stepsound.pool, x, y, z, .8f, 1f);
        }
        if (target == null) {
            tryPlaceBlockSimple();
        } else {
            if (!dodged) {
                target.hurt(this, 8);
                target.xd += xd * .35f;
                target.yd += .08f;
                target.zd += zd * .35f;
                if (target.health > 0) target.markForcedFall(owner);
            }
            dropPickup();
        }
        remove();
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
            if (o instanceof Mob m && !m.removed && m.bb.intersects(box)) return false;
        return level.setTile(x, y, z, blockId);
    }

    private void recalcHeldTarget(boolean trace) {
        if (owner == null) return;

        float currentHoldDist = 3.5f;
        if (net.classicremastered.minecraft.level.itemstack.GravityGunItem.holdDists.containsKey(
                owner)) {
            currentHoldDist =
                    net.classicremastered.minecraft.level.itemstack.GravityGunItem.holdDists.get(
                            owner);
        } else {
            currentHoldDist = this.holdDist;
        }

        Vec3D eye = net.classicremastered.minecraft.level.itemstack.GravityGunItem.eye(owner);
        Vec3D direction =
                net.classicremastered.minecraft.level.itemstack.GravityGunItem.look(owner);
        float ex = eye.x,
                ey = eye.y,
                ez = eye.z,
                fx = direction.x,
                fy = direction.y,
                fz = direction.z;

        targetX = ex + fx * currentHoldDist;
        targetY = ey + fy * currentHoldDist;
        targetZ = ez + fz * currentHoldDist;

        if (!trace || level == null) return;
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

    // ===== simple renderer so you SEE the block =====
    @Override
    public void render(TextureManager tm, float partial) {
        if (blockId <= 0 || Block.blocks[blockId] == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Block block = Block.blocks[blockId];

            float ex = xo + (x - xo) * partial;
            float ey = yo + (y - yo) * partial;
            float ez = zo + (z - zo) * partial;

            if (owner != null && state == State.HELD)
                net.classicremastered.minecraft.render.GunBeamRenderer.render(
                        owner, this, partial, true);

            GL11.glPushMatrix();
            GL11.glTranslatef(ex, ey, ez);
            if (state != State.THROWN) GL11.glRotatef(yRotO + (yRot - yRotO) * partial, 0, 1, 0);
            BlockItemRenderer.renderCube(block, tm, getRenderBrightness(partial), 1, bbWidth);
            GL11.glPopMatrix();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public int getBlockId() {
        return blockId;
    }
}
