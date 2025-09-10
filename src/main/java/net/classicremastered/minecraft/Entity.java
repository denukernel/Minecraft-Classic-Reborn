package net.classicremastered.minecraft;

import net.classicremastered.minecraft.level.BlockMap;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.Tile$SoundType;
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
    public void playPositionalSound(String key, float baseVolume, float pitch) {
        if (this.level == null || key == null)
            return;

        // reset “10 per tick” counter each tick
        int frame = this.level.tickCount;
        if (frame != STEP_SOUND_FRAME) {
            STEP_SOUND_FRAME = frame;
            STEP_SOUND_COUNT = 0;
        }

        // throttle ONLY footsteps from non-players
        if (isStepPool(key) && !(this instanceof net.classicremastered.minecraft.player.Player)) {
            if (STEP_SOUND_COUNT >= 10)
                return;
            STEP_SOUND_COUNT++;
        }

        // distance attenuation relative to nearest player
        net.classicremastered.minecraft.player.Player listener = this.level.getNearestPlayer(this.x, this.y, this.z, 64f); // search
                                                                                                                // radius
        if (listener == null)
            return;

        float dx = listener.x - this.x;
        float dy = listener.y - this.y;
        float dz = listener.z - this.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // full volume within 8 blocks, fade to 0 by 32 blocks
        final float fullVolDist = 8f;
        final float maxHearDist = 32f;
        if (dist >= maxHearDist)
            return;

        float vol = baseVolume;
        if (dist > fullVolDist) {
            float t = (dist - fullVolDist) / (maxHearDist - fullVolDist); // 0..1
            vol *= (1f - t); // linear fade (simple and cheap)
        }

        // hand off to level -> Paulscode
        this.level.playSound(key, this, vol, pitch);
    }

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
        if (this.rider != null) {
            this.rider.unmount();
            this.rider = null;
        }
        if (this.riding != null) {
            this.unmount();
        }
        this.syncBBToPos();
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
        if (target == null || target == this)
            return;
        if (this.riding != null)
            this.unmount();

        this.riding = target;
        target.rider = this;

        float ny = target.getSeatY() + this.riderYOffset;

        // Set BOTH previous and current to avoid first-frame pop
        this.xo = this.x = target.x;
        this.yo = this.y = ny;
        this.zo = this.z = target.z;

        this.yRotO = this.yRot = target.yRot;

        this.xd = target.xd;
        this.yd = target.yd;
        this.zd = target.zd;
        this.syncBBToPos();
    }

    // ---- Per-mount & per-rider tuning (already have these fields if you followed
    // earlier steps)
    public float seatFactor = 0.75f; // set on the MOUNT
    public float riderYOffset = 0.00f; // set on the RIDER

    // Bottom (feet) Y now/prev, derived from y/yo and heightOffset
    private float getBottomY() {
        return this.y - this.heightOffset + this.ySlideOffset;
    }

    private float getBottomYPrev() {
        return this.yo - this.heightOffset + this.ySlideOffset;
    }

    // Seat Y computed from true bottom so heightOffset is respected
    public float getSeatY() {
        return getBottomY() + this.bbHeight * seatFactor;
    }

    public float getSeatYPrev() {
        return getBottomYPrev() + this.bbHeight * seatFactor;
    }

    public void unmount() {
        if (this.riding == null)
            return;
        Entity mount = this.riding;

        float nx = mount.x;
        float ny = mount.y + mount.bbHeight + 0.05f;
        float nz = mount.z;

        if (mount.rider == this)
            mount.rider = null;
        this.riding = null;

        this.xo = nx;
        this.yo = ny;
        this.zo = nz;
        this.x = nx;
        this.y = ny;
        this.z = nz;

        this.xd = mount.xd * 0.6f;
        this.yd = Math.max(0.0f, mount.yd);
        this.zd = mount.zd * 0.6f;

        this.onGround = false;
        this.syncBBToPos(); // <— important
    }

    // --- keep BB centered on current x/y/z (no allocs) ---
    // com/mojang/minecraft/Entity.java
    private void syncBBToPos() {
        float hw = this.bbWidth * 0.5F;
        float hh = this.bbHeight * 0.5F;
        if (this.bb == null) {
            this.bb = new AABB(this.x - hw, this.y - hh, this.z - hw, this.x + hw, this.y + hh, this.z + hw);
        } else {
            this.bb.x0 = this.x - hw;
            this.bb.y0 = this.y - hh;
            this.bb.z0 = this.z - hw;
            this.bb.x1 = this.x + hw;
            this.bb.y1 = this.y + hh;
            this.bb.z1 = this.z + hw;
        }
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
        if (this.riding != null) {
            float seatPrev = this.riding.getSeatYPrev() + this.riderYOffset;
            float seatCurr = this.riding.getSeatY() + this.riderYOffset;

            // keep OUR previous for smooth interpolation regardless of tick order
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.yRotO = this.yRot;

            this.x = this.riding.x;
            this.y = seatCurr;
            this.z = this.riding.z;

            // smooth yaw toward mount
            float dyaw = this.riding.yRot - this.yRot;
            while (dyaw < -180.0F)
                dyaw += 360.0F;
            while (dyaw >= 180.0F)
                dyaw -= 360.0F;
            this.yRot += dyaw * 0.6F;

            this.xd = this.riding.xd;
            this.yd = this.riding.yd;
            this.zd = this.riding.zd;
            this.syncBBToPos();
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
        // === Per-tick fire damage (fast crossing burns) ===
        if (this.level != null && this.isInFire()) {
            // Skip creative-invulnerable players
            if (!(this instanceof net.classicremastered.minecraft.player.Player
                    && net.classicremastered.minecraft.player.Player.creativeInvulnerable)) {

                // Shorten i-frames for fire so repeated ticks can land quickly
                if (this instanceof net.classicremastered.minecraft.mob.Mob) {
                    net.classicremastered.minecraft.mob.Mob m = (net.classicremastered.minecraft.mob.Mob) this;
                    if (m.invulnerableTime > 7)
                        m.invulnerableTime = 7; // ~0.1s window
                }

                this.hurt(null, 1); // 1 HP every game tick while inside fire
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

    public void move(float var1, float var2, float var3) {
        if (this.noPhysics) {
            this.bb.move(var1, var2, var3);
            this.x = (this.bb.x0 + this.bb.x1) / 2.0F;
            this.y = this.bb.y0 + this.heightOffset - this.ySlideOffset;
            this.z = (this.bb.z0 + this.bb.z1) / 2.0F;
        } else {
            float var4 = this.x;
            float var5 = this.z;
            float var6 = var1;
            float var7 = var2;
            float var8 = var3;
            AABB var9 = this.bb.copy();

            // --- Sneak edge clamp: trim motion that would step over air when on ground ---
            if (this instanceof net.classicremastered.minecraft.player.Player) {
                net.classicremastered.minecraft.player.Player p = (net.classicremastered.minecraft.player.Player) this;
                if (p.sneaking && !p.isFlying && this.onGround) {
                    final float step = 0.05F; // iterative trim amount
                    // Use true feet Y from the current AABB
                    final int feetBelow = (int) Math.floor(this.bb.y0 - 0.01F) - 1; // block immediately under feet

                    // reduce X motion until there's support under the future x,z
                    while (var1 != 0.0F) {
                        float nx = this.x + var1;
                        int bx = (int) Math.floor(nx);
                        int bz = (int) Math.floor(this.z);
                        if (!this.level.isSolidTile(bx, feetBelow, bz)) {
                            if (Math.abs(var1) <= step) {
                                var1 = 0.0F;
                                break;
                            }
                            var1 += (var1 > 0 ? -step : step);
                            continue;
                        }
                        break;
                    }

                    // reduce Z motion similarly
                    while (var3 != 0.0F) {
                        float nz = this.z + var3;
                        int bx = (int) Math.floor(this.x);
                        int bz = (int) Math.floor(nz);
                        if (!this.level.isSolidTile(bx, feetBelow, bz)) {
                            if (Math.abs(var3) <= step) {
                                var3 = 0.0F;
                                break;
                            }
                            var3 += (var3 > 0 ? -step : step);
                            continue;
                        }
                        break;
                    }
                }
            }

            ArrayList var10 = this.level.getCubes(this.bb.expand(var1, var2, var3));

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
                var10 = this.level.getCubes(this.bb.expand(var6, var2, var8));

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

    protected void causeFallDamage(float var1) {
    }

    public boolean isInWater() {
        return this.level.containsLiquid(this.bb.grow(0.0F, -0.4F, 0.0F), LiquidType.WATER);
    }

    public boolean isUnderWater() {
        int var1;
        return (var1 = this.level.getTile((int) this.x, (int) (this.y + 0.12F), (int) this.z)) != 0
                ? Block.blocks[var1].getLiquidType().equals(LiquidType.WATER)
                : false;
    }

    public boolean isInLava() {
        return this.level.containsLiquid(this.bb.grow(0.0F, -0.4F, 0.0F), LiquidType.LAVA);
    }

    /** True if any tile overlapped by my feet/legs is Fire. */
    private boolean isInFire() {
        // sample from feet to a little above ankles
        final float y0 = this.bb.y0; // bottom of BB
        final float y1 = this.bb.y0 + 0.4f; // ~knees; adjust as needed

        int minX = (int) Math.floor(this.bb.x0);
        int maxX = (int) Math.floor(this.bb.x1 - 1e-6f);
        int minY = (int) Math.floor(y0);
        int maxY = (int) Math.floor(y1);
        int minZ = (int) Math.floor(this.bb.z0);
        int maxZ = (int) Math.floor(this.bb.z1 - 1e-6f);

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
            if (p.sneaking && !p.isFlying) var3 *= 0.30F;
        }
        float var4;
        if ((var4 = MathHelper.sqrt(var1 * var1 + var2 * var2)) >= 0.01F) {
            if (var4 < 1.0F) var4 = 1.0F;
            var4 = var3 / var4;
            var1 *= var4; var2 *= var4;
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

    public void render(TextureManager var1, float var2) {
    }

    public void setLevel(Level var1) {
        this.level = var1;
    }

    public void playSound(String key, float volume, float pitch) {
        if (this.level == null)
            return;

        Player nearest = this.level.getNearestPlayer(this.x, this.y, this.z, 32f); // 32 block radius
        if (nearest == null)
            return;

        float dx = nearest.x - this.x;
        float dy = nearest.y - this.y;
        float dz = nearest.z - this.z;
        float distSq = dx * dx + dy * dy + dz * dz;

        float maxDist = 16f; // full volume within 16 blocks
        float fadeDist = 32f; // beyond this, silent

        float dist = (float) Math.sqrt(distSq);
        if (dist > fadeDist)
            return; // too far, don't play

        float attenuatedVol = volume * (1f - Math.min(1f, (dist - maxDist) / (fadeDist - maxDist)));

        this.level.playSound(key, this, attenuatedVol, pitch);
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
