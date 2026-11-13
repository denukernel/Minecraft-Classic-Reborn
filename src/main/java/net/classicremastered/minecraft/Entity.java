package net.classicremastered.minecraft;

import net.classicremastered.minecraft.entity.BoatEntity;
import net.classicremastered.minecraft.level.BlockMap;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.Tile$SoundType;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.Spider;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.net.PositionUpdate;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import java.io.Serializable;
import java.util.ArrayList;

public abstract class Entity implements Serializable {

    public static final long serialVersionUID = 0L;
    public Level level;
    public float xo;
    public float yo;
    public float zo;
    public float x;
    public float y;
    public float z;
    public float xd;
    public float yd;
    public float zd;
    public float yRot;
    public float xRot;
    public float yRotO;
    public float xRotO;
    public AABB bb;
    public boolean onGround = false;
    public boolean horizontalCollision = false;
    public boolean collision = false;
    public boolean slide = true;
    public boolean removed = false;
    public float heightOffset = 0.0F;
    public float bbWidth = 0.6F;
    public float bbHeight = 1.8F;
    public float walkDistO = 0.0F;
    public float walkDist = 0.0F;
    public boolean makeStepSound = true;
    public float fallDistance = 0.0F;
    private int nextStep = 1;
    public BlockMap blockMap;
    public float xOld;
    public float yOld;
    public float zOld;
    public int textureId = 0;
    public float ySlideOffset = 0.0F;
    public float footSize = 0.0F;
    public boolean noPhysics = false;
    public float pushthrough = 0.0F;
    public boolean hovered = false;

    // --- Riding Support ---
    public Entity riding; // entity this one is riding
    public Entity rider; // entity riding this one

    public Entity(Level var1) {
        this.level = var1;
        this.setPos(0.0F, 0.0F, 0.0F);
    }
    public int remainingFireTicks = 0;
    private transient int cactusDamageCooldown;
    private transient int suffocationCooldown;
    private int cactusStackHits;
    private int fireDamageCooldown;
    private transient boolean waterContactKnown;
    private transient boolean wasInWater;

    public boolean ignite(int ticks) {
        if (ticks <= 0
                || level == null
                || !isAliveEntity(this)
                || isInWater()
                || isUnderWater()
                || this instanceof net.classicremastered.minecraft.player.Player player
                        && (level.creativeMode || player.isSpectator())) return false;
        remainingFireTicks = Math.max(remainingFireTicks, ticks);
        return true;
    }

    public void extinguishFire() {
        if (remainingFireTicks <= 0) return;
        remainingFireTicks = 0;
        fireDamageCooldown = 0;
        if (level != null) {
            net.classicremastered.minecraft.level.tile.FireBlock.playExtinguishSound(
                    level, x, y, z);
        }
    }

    private void updateWaterContact(boolean inWater) {
        if (inWater
                && waterContactKnown
                && !wasInWater
                && !(this instanceof net.classicremastered.minecraft.player.Player player
                        && player.isSpectator())) {
            float speed = MathHelper.sqrt(xd * xd + yd * yd + zd * zd);
            level.playSound("liquid/splash", this, MathHelper.clamp(speed * .7f, .2f, 1f), 1f);
        }
        waterContactKnown = true;
        wasInWater = inWater;
    }

    // --- sound throttling (per tick) ---
    private static int STEP_SOUND_FRAME = -1;
    private static int STEP_SOUND_COUNT = 0;

    private static boolean isStepPool(String key) {
        return key != null && (key.startsWith("step/") || key.startsWith("step."));
    }

    /** True if the entity is alive (works for Player, Mob, and other Entities). */
    private static boolean isAliveEntity(Entity e) {
        if (e == null)
            return false;
        // Players
        if (e instanceof net.classicremastered.minecraft.player.Player) {
            return ((net.classicremastered.minecraft.player.Player) e).health > 0 && !e.removed;
        }
        // Mobs
        if (e instanceof net.classicremastered.minecraft.mob.Mob) {
            return ((net.classicremastered.minecraft.mob.Mob) e).health > 0 && !e.removed;
        }
        // Other entities: treat as alive if not removed
        return !e.removed;
    }

    /**
     * Play a positional sound with distance attenuation and (for steps) throttling.
     */

    public void resetPos() {
        if (this.level != null) {
            float var1 = (float) this.level.xSpawn + 0.5F;
            float var2 = (float) this.level.ySpawn;

            for (float var3 = (float) this.level.zSpawn + 0.5F; var2 > 0.0F; ++var2) {
                this.setPos(var1, var2, var3);
                if (this.level.getCubes(this.bb).size() == 0) {
                    break;
                }
            }

            this.xd = this.yd = this.zd = 0.0F;
            this.yRot = this.level.rotSpawn;
            this.xRot = 0.0F;
        }
    }

    public void remove() {
        ejectPassengers();
        if (this.riding != null) {
            this.unmount();
        }
        this.removed = true;
    }

    public void setSize(float var1, float var2) {
        this.bbWidth = var1;
        this.bbHeight = var2;
    }

    // ---- Mount-side seat height (where riders sit on me)
    // absolute blocks (+up, -down)
    public float getRiderYOffset() {
        return riderYOffset;
    }

    public void mount(Entity target) {
        if (target == null
                || target == this
                || target.level != level
                || !target.canAcceptPassenger(this)
                || target.riding != null
                || rider != null
                || !isAliveEntity(target)
                || !isAliveEntity(this)) return;
        if (!target.hasRiderClearance(this))
            return;
        if (riding != null)
            unmount();
        riding = target;
        target.addPassenger(this);
        if (this instanceof Player && target instanceof Mob mount) {
            mount.yRotO = mount.yRot = yRot;
            mount.yBodyRotO = mount.yBodyRot = mount.yRot;
        } else if (!(this instanceof Player)) {
            yRot = target.yRot;
        }
        syncRidingPosition();
        resetInterpolation();
        if (blockMap != null)
            blockMap.moved(this);
    }

    protected boolean canAcceptPassenger(Entity passenger) {
        if (this instanceof net.classicremastered.minecraft.mob.Cow cow)
            return rider == null && cow.isSaddled();
        return rider == null && !(passenger instanceof Player);
    }

    protected void addPassenger(Entity passenger) {
        rider = passenger;
    }

    protected boolean hasPassenger(Entity passenger) {
        return rider == passenger;
    }

    protected void removePassenger(Entity passenger) {
        if (rider == passenger) rider = null;
    }

    public void ejectPassengers() {
        while (rider != null) {
            if (rider.riding == this) rider.unmount();
            else removePassenger(rider);
        }
    }

    public float seatFactor = 0.75f;
    public float riderYOffset;

    public float getSeatY() {
        return bb.y0 + bbHeight * seatFactor;
    }

    private float passengerFeet(Entity passenger) {
        if (this instanceof Spider && passenger instanceof Skeleton skeleton) {
            // The seated skeleton's bent legs leave its hips above its collision origin.
            float seatOffset = skeleton.isBaby() ? .36f : .54f;
            return Math.max(bb.y0, getSeatY() - seatOffset) + passenger.riderYOffset;
        }
        float babyAdjustment = this instanceof net.classicremastered.minecraft.mob.Cow
                        && passenger instanceof Mob mob && mob.isBaby()
                ? .25f
                : 0f;
        return getSeatY() + passenger.riderYOffset + babyAdjustment;
    }

    protected float ridingWidth(Entity passenger) {
        float width = Math.max(bbWidth, passenger.bbWidth);
        return this instanceof net.classicremastered.minecraft.mob.Cow ? Math.max(1.15f, width) : width;
    }

    protected float ridingHeight(Entity passenger) {
        return Math.max(bbHeight, passengerFeet(passenger) - bb.y0 + passenger.bbHeight);
    }

    protected AABB ridingBounds(Entity passenger) {
        float half = ridingWidth(passenger) * .5f;
        return new AABB(x - half, bb.y0, z - half, x + half, bb.y0 + ridingHeight(passenger), z + half);
    }

    public boolean hasRiderClearance(Entity passenger) {
        return level.getCubes(ridingBounds(passenger)).isEmpty();
    }

    public float getSeatYPrev() {
        return getSeatY() + yo - y;
    }

    public void resetInterpolation() {
        xo = x;
        yo = y;
        zo = z;
        yRotO = yRot;
        xRotO = xRot;
    }

    public void syncRidingPosition() {
        if (riding == null)
            return;
        if (!riding.hasPassenger(this) || !isAliveEntity(riding)) {
            unmount();
            return;
        }
        Vec3D seat = riding.passengerPosition(this);
        bb.move(seat.x - x, seat.y - bb.y0, seat.z - z);
        x = seat.x;
        y = seat.y + heightOffset;
        z = seat.z;
        ySlideOffset = 0f;
        xd = yd = zd = 0f;
        fallDistance = 0f;
        onGround = false;
    }

    protected Vec3D passengerPosition(Entity passenger) {
        return new Vec3D(x, passengerFeet(passenger), z);
    }

    public void unmount() {
        if (riding == null)
            return;
        Entity mount = riding;
        riding = null;
        mount.removePassenger(this);
        float gap = (mount.bbWidth + bbWidth) * .5f + .1f;
        for (int side = 0; side < 4; side++) {
            float dx = side == 0 ? gap : side == 1 ? -gap : 0f;
            float dz = side == 2 ? gap : side == 3 ? -gap : 0f;
            AABB landing = bb.cloneMove(mount.x + dx - x, mount.bb.y0 - bb.y0, mount.z + dz - z);
            if (level.getCubes(landing).stream().anyMatch(box -> landing.intersects((AABB) box)))
                continue;
            bb = landing;
            x = mount.x + dx;
            y = bb.y0 + heightOffset;
            z = mount.z + dz;
            break;
        }
        xd = yd = zd = 0f;
        fallDistance = 0f;
        onGround = false;
        resetInterpolation();
        if (blockMap != null)
            blockMap.moved(this);
    }

    public void setPos(PositionUpdate var1) {
        if (var1.position) {
            this.setPos(var1.x, var1.y, var1.z);
        } else {
            this.setPos(this.x, this.y, this.z);
        }

        if (var1.rotation) {
            this.setRot(var1.yaw, var1.pitch);
        } else {
            this.setRot(this.yRot, this.xRot);
        }
    }

    protected void setRot(float var1, float var2) {
        this.yRot = var1;
        this.xRot = var2;
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float hw = this.bbWidth * 0.5F, hh = this.bbHeight * 0.5F;
        this.bb = new AABB(x - hw, y - hh, z - hw, x + hw, y + hh, z + hw);
    }

    public void turn(float var1, float var2) {
        float var3 = this.xRot;
        float var4 = this.yRot;
        this.yRot = (float) ((double) this.yRot + (double) var1 * 0.15D);
        this.xRot = (float) ((double) this.xRot - (double) var2 * 0.15D);
        if (this.xRot < -90.0F) {
            this.xRot = -90.0F;
        }

        if (this.xRot > 90.0F) {
            this.xRot = 90.0F;
        }

        this.xRotO += this.xRot - var3;
        this.yRotO += this.yRot - var4;
    }

    public void interpolateTurn(float var1, float var2) {
        this.yRot = (float) ((double) this.yRot + (double) var1 * 0.15D);
        this.xRot = (float) ((double) this.xRot - (double) var2 * 0.15D);
        if (this.xRot < -90.0F) {
            this.xRot = -90.0F;
        }

        if (this.xRot > 90.0F) {
            this.xRot = 90.0F;
        }

    }

    public void tick() {
        if (riding != null) {
            resetInterpolation();
            syncRidingPosition();
        }
        if (this.level != null && this.level.tickCount % 1 == 0) {
            stepSoundsThisTick = 0; // reset every tick
        }

        this.walkDistO = this.walkDist;

        // Only overwrite previous pos for free entities
        if (this.riding == null) {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
        }
        if (this.level != null
                && this instanceof net.classicremastered.minecraft.mob.Mob
                && isAliveEntity(this)) {
            if (suffocationCooldown > 0) suffocationCooldown--;
            if (!level.isNetworkMode()
                    && !noPhysics
                    && suffocationCooldown == 0
                    && getBlockAtEyes() != null) {
                hurt(null, 1, "in_wall");
                suffocationCooldown = 10;
            }
            if (cactusDamageCooldown > 0) cactusDamageCooldown--;
            if (!level.isNetworkMode() && cactusDamageCooldown == 0 && touchesCactus()) {
                hurt(null, 1, "cactus");
                cactusDamageCooldown = 10;
            }
            boolean inWater =
                    isInWater()
                            || isUnderWater()
                            || this instanceof net.classicremastered.minecraft.mob.Zombie undead
                                    && undead.isInWaterOrOnWater();
            updateWaterContact(inWater);
            if (inWater
                    || this instanceof net.classicremastered.minecraft.player.Player player
                            && player.isSpectator()) {
                extinguishFire();
                fireDamageCooldown = 0;
            } else {
                boolean lava = isInLava();
                boolean contact = lava || isInFire();
                if (contact)
                    ignite(lava ? 300 : 100);

                if (remainingFireTicks > 0) {
                    if (fireDamageCooldown > 0)
                        fireDamageCooldown--;
                    if (fireDamageCooldown == 0) {
                        hurt(null, lava ? 4 : 1, lava ? "lava" : contact ? "fire" : "burn");
                        fireDamageCooldown = contact ? 10 : 20;
                    }
                    remainingFireTicks--;
                } else {
                    fireDamageCooldown = 0;
                }
            }
        }

        this.xRotO = this.xRot;
        this.yRotO = this.yRot;
    }

    public boolean isFree(float var1, float var2, float var3, float var4) {
        AABB var5 = this.bb.grow(var4, var4, var4).cloneMove(var1, var2, var3);
        return this.level.getCubes(var5).size() > 0 ? false : !this.level.containsAnyLiquid(var5);
    }

    public boolean isFree(float var1, float var2, float var3) {
        AABB var4 = this.bb.cloneMove(var1, var2, var3);
        return this.level.getCubes(var4).size() > 0 ? false : !this.level.containsAnyLiquid(var4);
    }

    private ArrayList<AABB> movementColliders(AABB swept) {
        ArrayList<AABB> collisions = level.getCubes(swept);
        if (level.blockMap == null) {
            return collisions;
        }
        if (this instanceof BoatEntity) {
            for (Entity entity : level.blockMap.getEntities(this, swept, new ArrayList<>())) {
                if (entity instanceof BoatEntity && !entity.removed) collisions.add(entity.bb);
            }
            return collisions;
        }
        if (!(this instanceof Mob mover) || mover.health <= 0) return collisions;
        for (Object entity : level.findEntities(this, swept)) {
            if (entity instanceof Mob other && other.health > 0 && !other.removed && !other.noPhysics
                    && other != rider && other != riding
                    && (mover instanceof CrossbowTurret || other instanceof CrossbowTurret)) {
                collisions.add(other.bb);
            }
        }
        return collisions;
    }

    public void move(float var1, float var2, float var3) {
        if (rider == null || noPhysics) {
            moveWithBounds(var1, var2, var3);
            return;
        }
        // Travel with room for both bodies while keeping their individual hitboxes for combat.
        AABB body = bb;
        bb = ridingBounds(rider);
        float startX = bb.x0, startY = bb.y0, startZ = bb.z0;
        try {
            moveWithBounds(var1, var2, var3);
        } finally {
            body.move(bb.x0 - startX, bb.y0 - startY, bb.z0 - startZ);
            bb = body;
        }
    }

    private void moveWithBounds(float var1, float var2, float var3) {
        if (this.noPhysics) {
            this.bb.move(var1, var2, var3);
            this.x = (this.bb.x0 + this.bb.x1) / 2.0F;
            this.y = this.bb.y0 + this.heightOffset - this.ySlideOffset;
            this.z = (this.bb.z0 + this.bb.z1) / 2.0F;
        } else {
            float var4 = this.x;
            float var5 = this.z;
            if (this instanceof net.classicremastered.minecraft.player.Player player
                    && player.sneaking
                    && !player.isFlying
                    && var2 <= 0
                    && (onGround || fallDistance < .6f && hasSneakSupport(0, 0))) {
                if (!Float.isFinite(var1)) var1 = 0;
                if (!Float.isFinite(var3)) var3 = 0;
                while (var1 != 0 && !hasSneakSupport(var1, 0)) var1 = trimSneakStep(var1);
                while (var3 != 0 && !hasSneakSupport(0, var3)) var3 = trimSneakStep(var3);
                while (var1 != 0 && var3 != 0 && !hasSneakSupport(var1, var3)) {
                    var1 = trimSneakStep(var1);
                    var3 = trimSneakStep(var3);
                }
            }
            float var6 = var1;
            float var7 = var2;
            float var8 = var3;
            AABB var9 = this.bb.copy();

            ArrayList var10 = movementColliders(this.bb.expand(var1, var2, var3));

            for (int var11 = 0; var11 < var10.size(); ++var11) {
                var2 = ((AABB) var10.get(var11)).clipYCollide(this.bb, var2);
            }

            this.bb.move(0.0F, var2, 0.0F);
            if (!this.slide && var7 != var2) {
                var3 = 0.0F;
                var2 = 0.0F;
                var1 = 0.0F;
            }

            boolean var16 = this.onGround || var7 != var2 && var7 < 0.0F;

            int var12;
            for (var12 = 0; var12 < var10.size(); ++var12) {
                var1 = ((AABB) var10.get(var12)).clipXCollide(this.bb, var1);
            }

            this.bb.move(var1, 0.0F, 0.0F);
            if (!this.slide && var6 != var1) {
                var3 = 0.0F;
                var2 = 0.0F;
                var1 = 0.0F;
            }

            for (var12 = 0; var12 < var10.size(); ++var12) {
                var3 = ((AABB) var10.get(var12)).clipZCollide(this.bb, var3);
            }

            this.bb.move(0.0F, 0.0F, var3);
            if (!this.slide && var8 != var3) {
                var3 = 0.0F;
                var2 = 0.0F;
                var1 = 0.0F;
            }

            float var17, var18;
            if (this.footSize > 0.0F && var16 && this.ySlideOffset < 0.05F && (var6 != var1 || var8 != var3)) {
                var18 = var1;
                var17 = var2;
                float var13 = var3;
                var1 = var6;
                var2 = this.footSize;
                var3 = var8;
                AABB var14 = this.bb.copy();
                this.bb = var9.copy();
                var10 = movementColliders(this.bb.expand(var6, var2, var8));

                int var15;
                for (var15 = 0; var15 < var10.size(); ++var15) {
                    var2 = ((AABB) var10.get(var15)).clipYCollide(this.bb, var2);
                }

                this.bb.move(0.0F, var2, 0.0F);
                if (!this.slide && var7 != var2) {
                    var3 = 0.0F;
                    var2 = 0.0F;
                    var1 = 0.0F;
                }

                for (var15 = 0; var15 < var10.size(); ++var15) {
                    var1 = ((AABB) var10.get(var15)).clipXCollide(this.bb, var1);
                }

                this.bb.move(var1, 0.0F, 0.0F);
                if (!this.slide && var6 != var1) {
                    var3 = 0.0F;
                    var2 = 0.0F;
                    var1 = 0.0F;
                }

                for (var15 = 0; var15 < var10.size(); ++var15) {
                    var3 = ((AABB) var10.get(var15)).clipZCollide(this.bb, var3);
                }

                this.bb.move(0.0F, 0.0F, var3);
                if (!this.slide && var8 != var3) {
                    var3 = 0.0F;
                    var2 = 0.0F;
                    var1 = 0.0F;
                }

                if (var18 * var18 + var13 * var13 >= var1 * var1 + var3 * var3) {
                    var1 = var18;
                    var2 = var17;
                    var3 = var13;
                    this.bb = var14.copy();
                } else {
                    this.ySlideOffset = (float) ((double) this.ySlideOffset + 0.5D);
                }
            }

            this.horizontalCollision = var6 != var1 || var8 != var3;
            this.onGround = var7 != var2 && var7 < 0.0F;
            this.collision = this.horizontalCollision || var7 != var2;

            if (this.onGround) {
                if (this.fallDistance > 0.0F) {
                    this.causeFallDamage(this.fallDistance);
                    this.fallDistance = 0.0F;
                }
            } else if (var2 < 0.0F) {
                this.fallDistance -= var2;
            }

            if (var6 != var1)
                this.xd = 0.0F;
            if (var7 != var2)
                this.yd = 0.0F;
            if (var8 != var3)
                this.zd = 0.0F;

            this.x = (this.bb.x0 + this.bb.x1) / 2.0F;
            this.y = this.bb.y0 + this.heightOffset - this.ySlideOffset;
            this.z = (this.bb.z0 + this.bb.z1) / 2.0F;

            var18 = this.x - var4;
            var17 = this.z - var5;
            this.walkDist = (float) ((double) this.walkDist
                    + (double) MathHelper.sqrt(var18 * var18 + var17 * var17) * 0.6D);

            if (this.makeStepSound) {
                int blockId = this.level.getTile((int) this.x, (int) (this.y - 0.2F - this.heightOffset), (int) this.z);
                if (this.walkDist > (float) this.nextStep && blockId > 0) {
                    this.nextStep = (int) this.walkDist + 1;
                    Tile$SoundType sound = Block.blocks[blockId].stepsound;
                    if (sound != Tile$SoundType.none) {
                        this.playPositionalSound(sound.pool, sound.getVolume() * 0.60F, sound.getPitch());
                    }
                }
            }

            this.ySlideOffset *= 0.4F;
        }
    }

    private boolean hasSneakSupport(float dx, float dz) {
        float inset = .001f;
        AABB probe = new AABB(bb.x0 + dx + inset, bb.y0 - .601f, bb.z0 + dz + inset,
                bb.x1 + dx - inset, bb.y0 + inset, bb.z1 + dz - inset);
        return !level.getCubes(probe).isEmpty();
    }

    private static float trimSneakStep(float motion) {
        return Math.abs(motion) <= .05f ? 0 : motion - Math.copySign(.05f, motion);
    }

    protected void causeFallDamage(float var1) {}

    final Block getBlockAtEyes() {
        float eye =
                this instanceof net.classicremastered.minecraft.player.Player
                        ? bb.y0 + heightOffset
                        : bb.y0 + (bb.y1 - bb.y0) * .85f;
        float radius = bbWidth * .4f;
        AABB eyes =
                new AABB(
                        x - radius, eye - .0001f, z - radius, x + radius, eye + .0001f, z + radius);
        int y = MathHelper.floor(eye);
        for (int bx = MathHelper.floor(eyes.x0); bx <= MathHelper.floor(eyes.x1); bx++) {
            for (int bz = MathHelper.floor(eyes.z0); bz <= MathHelper.floor(eyes.z1); bz++) {
                Block block = Block.blocks[level.getTile(bx, y, bz)];
                if (block == null || !block.isSolid() || !block.isOpaque() || !block.isCube())
                    continue;
                AABB collisionBox = block.getCollisionBox(bx, y, bz);
                if (collisionBox != null && eyes.intersectsInner(collisionBox)) return block;
            }
        }
        return null;
    }

    protected final boolean fireDestroysStack(boolean fireResistant) {
        if (fireResistant || level.isNetworkMode()) return false;
        // Drops are shorter than the inset used by the mob lava check.
        if (remainingFireTicks > 0 || isInFire() || level.containsLiquid(bb, LiquidType.LAVA)) {
            remove();
            return true;
        }
        return false;
    }

    protected final boolean cactusDamagesStack() {
        if (removed || level.isNetworkMode() || !touchesCactus() || ++cactusStackHits < 5)
            return false;
        remove();
        if (level.particleEngine != null) {
            for (int i = 0; i < 8; i++) {
                level.particleEngine.spawnParticle(
                        new net.classicremastered.minecraft.particle.TerrainParticle(
                                level,
                                x,
                                y,
                                z,
                                (level.random.nextFloat() - .5f) * .2f,
                                level.random.nextFloat() * .15f,
                                (level.random.nextFloat() - .5f) * .2f,
                                Block.CACTUS));
            }
        }
        return true;
    }

    public final int getCactusStackHits() {
        return cactusStackHits;
    }

    public final void setCactusStackHits(int hits) {
        cactusStackHits = Math.max(0, Math.min(4, hits));
    }

    private boolean touchesCactus() {
        AABB contact = bb.grow(.001f, .001f, .001f);
        for (int x = MathHelper.floor(contact.x0); x <= MathHelper.floor(contact.x1); x++) {
            for (int y = MathHelper.floor(contact.y0); y <= MathHelper.floor(contact.y1); y++) {
                for (int z = MathHelper.floor(contact.z0); z <= MathHelper.floor(contact.z1); z++) {
                    if (level.getTile(x, y, z) == Block.CACTUS.id
                            && contact.intersectsInner(Block.CACTUS.getSelectionBox(x, y, z)))
                        return true;
                }
            }
        }
        return false;
    }

    public boolean isInWater() {
        return this.level.containsLiquid(this.bb.grow(0.0F, -0.4F, 0.0F), LiquidType.WATER);
    }

    public boolean isUnderWater() {
        float sampleY = this instanceof Player ? y : y + 0.12F;
        return level != null && level.getLiquidAt(x, sampleY, z) == LiquidType.WATER;
    }

    public boolean isInLava() {
        return this.level.containsLiquid(this.bb.grow(0.0F, -0.4F, 0.0F), LiquidType.LAVA);
    }

    /** True if fire overlaps the body, including flames beside the legs. */
    private boolean isInFire() {
        return overlapsFire(this.bb);
    }

    public boolean overlapsFire(AABB bounds) {
        final float y0 = bounds.y0;
        final float y1 = bounds.y1 - 1e-6f;

        int minX = (int) Math.floor(bounds.x0);
        int maxX = (int) Math.floor(bounds.x1 - 1e-6f);
        int minY = (int) Math.floor(y0);
        int maxY = (int) Math.floor(y1);
        int minZ = (int) Math.floor(bounds.z0);
        int maxZ = (int) Math.floor(bounds.z1 - 1e-6f);

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int id = this.level.getTile(x, y, z);
                    if (id == net.classicremastered.minecraft.level.tile.Block.FIRE.id) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void moveRelative(float var1, float var2, float var3) {
        if (this instanceof net.classicremastered.minecraft.player.Player) {
            net.classicremastered.minecraft.player.Player p = (net.classicremastered.minecraft.player.Player) this;
            if (p.sneaking && !p.isFlying)
                var3 *= 0.30F;
        }
        float var4;
        if ((var4 = MathHelper.sqrt(var1 * var1 + var2 * var2)) >= 0.01F) {
            if (var4 < 1.0F)
                var4 = 1.0F;
            var4 = var3 / var4;
            var1 *= var4;
            var2 *= var4;
            var3 = MathHelper.sin(this.yRot * 3.1415927F / 180.0F);
            var4 = MathHelper.cos(this.yRot * 3.1415927F / 180.0F);
            this.xd += var1 * var4 - var2 * var3;
            this.zd += var2 * var4 + var1 * var3;
        }
    }

    public boolean isLit() {
        int var1 = (int) this.x;
        int var2 = (int) this.y;
        int var3 = (int) this.z;
        return this.level.isLit(var1, var2, var3);
    }

    public float getBrightness(float var1) {
        int var4 = (int) this.x;
        int var2 = (int) (this.y + this.heightOffset / 2.0F - 0.5F);
        int var3 = (int) this.z;
        return this.level.getBrightness(var4, var2, var3);
    }

    public float getRenderBrightness(float partial) {
        return level.getRenderBrightness((int) Math.floor(x), (int) Math.floor(y + heightOffset / 2f - .5f),
                (int) Math.floor(z));
    }

    public void render(TextureManager var1, float var2) {
    }

    public void setLevel(Level var1) {
        this.level = var1;
    }

    public void playPositionalSound(String key, float baseVolume, float pitch) {
        if (this.level == null || key == null)
            return;

        // Step sound throttling only
        int frame = this.level.tickCount;
        if (frame != STEP_SOUND_FRAME) {
            STEP_SOUND_FRAME = frame;
            STEP_SOUND_COUNT = 0;
        }
        if (isStepPool(key) && !(this instanceof net.classicremastered.minecraft.player.Player)) {
            if (STEP_SOUND_COUNT >= 10)
                return;
            STEP_SOUND_COUNT++;
        }

        // Hand off unmodified to the sound system (Paulscode handles attenuation itself)
        this.level.playSound(key, this, baseVolume, pitch);
    }

    public void playSound(String key, float volume, float pitch) {
        if (this.level == null)
            return;
        this.level.playSound(key, this, volume, pitch);
    }

    private static int stepSoundsThisTick = 0;

    public void moveTo(float var1, float var2, float var3, float var4, float var5) {
        this.xo = this.x = var1;
        this.yo = this.y = var2;
        this.zo = this.z = var3;
        this.yRot = var4;
        this.xRot = var5;
        this.setPos(var1, var2, var3);
    }

    public float distanceTo(Entity var1) {
        float var2 = this.x - var1.x;
        float var3 = this.y - var1.y;
        float var4 = this.z - var1.z;
        return MathHelper.sqrt(var2 * var2 + var3 * var3 + var4 * var4);
    }

    public float distanceTo(float var1, float var2, float var3) {
        var1 = this.x - var1;
        var2 = this.y - var2;
        float var4 = this.z - var3;
        return MathHelper.sqrt(var1 * var1 + var2 * var2 + var4 * var4);
    }

    public float distanceToSqr(Entity var1) {
        float var2 = this.x - var1.x;
        float var3 = this.y - var1.y;
        float var4 = this.z - var1.z;
        return var2 * var2 + var3 * var3 + var4 * var4;
    }

    public void playerTouch(Entity var1) {
    }

    public void push(Entity var1) {
        float var2 = var1.x - this.x;
        float var3 = var1.z - this.z;
        float var4;
        if ((var4 = var2 * var2 + var3 * var3) >= 0.01F) {
            var4 = MathHelper.sqrt(var4);
            var2 /= var4;
            var3 /= var4;
            var2 /= var4;
            var3 /= var4;
            var2 *= 0.05F;
            var3 *= 0.05F;
            var2 *= 1.0F - this.pushthrough;
            var3 *= 1.0F - this.pushthrough;
            this.push(-var2, 0.0F, -var3);
            var1.push(var2, 0.0F, var3);
        }

    }

    protected void push(float var1, float var2, float var3) {
        this.xd += var1;
        this.yd += var2;
        this.zd += var3;
    }

    protected transient String damageCause;

    public final void hurt(Entity attacker, int damage, String cause) {
        String previous = damageCause;
        damageCause = cause;
        try {
            hurt(attacker, damage);
        } finally {
            damageCause = previous;
        }
    }

    public void hurt(Entity var1, int var2) {
    }

    public boolean intersects(float var1, float var2, float var3, float var4, float var5, float var6) {
        return this.bb.intersects(var1, var2, var3, var4, var5, var6);
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public boolean isShootable() {
        return false;
    }

    public void awardKillScore(Entity var1, int var2) {
    }

    public boolean shouldRender(Vec3D cam) {
        final float px = (this.riding != null ? this.riding.x : this.x);
        final float py = (this.riding != null ? this.riding.y : this.y);
        final float pz = (this.riding != null ? this.riding.z : this.z);

        float dx = px - cam.x, dy = py - cam.y, dz = pz - cam.z;
        float d2 = dx * dx + dy * dy + dz * dz;
        return this.shouldRenderAtSqrDistance(d2);
    }

    public boolean shouldRenderAtSqrDistance(float d2) {
        float size = 1.0F;
        if (this.bb != null)
            size = Math.max(size, this.bb.getSize());
        if (this.riding != null && this.riding.bb != null)
            size = Math.max(size, this.riding.bb.getSize());

        // base 96-block radius minimum to avoid “camera up” pop; scale for giants
        float range = Math.max(146.0F, size * 64.0F);
        return d2 < range * range;
    }

    public int getTexture() {
        return this.textureId;
    }

    public boolean isCreativeModeAllowed() {
        return false;
    }

    public void renderHover(TextureManager var1, float var2) {
    }

}
