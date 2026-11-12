package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.GrapplingHookItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.LadderBlock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

public class Player extends Mob {
    public transient java.util.Map<Integer, java.util.List<Mob>> mobWandSelections;

    private transient boolean deathMessageSent;
    private transient int frostbiteSlowTicks;
    private transient net.classicremastered.minecraft.mob.Zombie environmentalDeathAttacker;
    private transient int environmentalDeathCreditTicks;
    public transient net.classicremastered.minecraft.mob.StickFigure bossComboOwner;

    public Player(Level var1) {
        this(var1, true);
    }

    public Player(Level var1, boolean isLocal) {
        super(var1);
        if (isLocal && var1 != null) {
            var1.player = this;
            var1.removeEntity(this);
            var1.addEntity(this);
        }

        this.heightOffset = 1.62F;
        this.health = 20;
        this.modelName = "humanoid";
        this.ai = new PlayerInputAI(this);
    }

    public boolean helmet = false;
    public boolean armor = false;

    private int portalCooldown = 0; // ticks before re-using a portal

    // --- Void damage tracking ---
    private int voidDamageCounter = 0; // counts ticks under y = -30

    private transient boolean spectator;
    private transient boolean autoJumpQueued;

    public boolean canFly = false; // set by GameMode.apply(...)
    public boolean isFlying = false; // toggled by key in Creative

    public float aimYaw(float partial) {
        return yRotO + (yRot - yRotO) * partial + 180f;
    }

    public Vec3D aimDirection(float partial) {
        float pitch = -(xRotO + (xRot - xRotO) * partial) * 0.017453292F;
        float yaw = -aimYaw(partial) * 0.017453292F - 3.1415927F;
        float cosPitch = MathHelper.cos(pitch);
        return new Vec3D(
                MathHelper.sin(yaw) * cosPitch,
                MathHelper.sin(pitch),
                MathHelper.cos(yaw) * cosPitch);
    }

    public float movementStrafe() {
        return -input.xxa;
    }

    private boolean canAutoJump() {
        return minecraft != null
                && minecraft.settings != null
                && minecraft.settings.autoJump
                && minecraft.currentScreen == null
                && onGround
                && health > 0
                && !spectator
                && !noPhysics
                && !sneaking
                && !isFlying
                && riding == null
                && frozenTicks == 0
                && mcTicks == 0
                && (input.yya != 0 || input.xxa != 0)
                && !isInWater()
                && !isInLava()
                && !LadderBlock.isLadder(
                        level.getTile(
                                (int) Math.floor(x),
                                (int) Math.floor(bb.y0 + 0.05F),
                                (int) Math.floor(z)));
    }

    boolean consumeAutoJump() {
        boolean jump = autoJumpQueued && canAutoJump();
        autoJumpQueued = false;
        return jump;
    }

    @Override
    public void travel(float strafe, float forward) {
        float previousX = x;
        float previousZ = z;
        super.travel(strafe, forward);
        autoJumpQueued =
                canAutoJump()
                        && !input.jumping
                        && findAutoJumpObstacle(x - previousX, z - previousZ, strafe, forward);
    }

    private boolean findAutoJumpObstacle(float dx, float dz, float strafe, float forward) {
        float yaw = yRot * ((float) Math.PI / 180);
        float sin = MathHelper.sin(yaw);
        float cos = MathHelper.cos(yaw);
        float inputX = strafe * cos - forward * sin;
        float inputZ = forward * cos + strafe * sin;
        float speed = MathHelper.sqrt(dx * dx + dz * dz);
        // A clipped movement vector can point along a ledge the player is walking into.
        if (horizontalCollision || speed * speed <= 0.001F) {
            dx = inputX * 0.10F;
            dz = inputZ * 0.10F;
            speed = MathHelper.sqrt(dx * dx + dz * dz);
        }
        if (speed == 0 || forward < -0.15F || (-sin * dx + cos * dz) / speed < -0.15F) {
            return false;
        }

        float lookAhead = speed + (input.running ? 0.98F : 0.70F);
        float sweepX = dx / speed * lookAhead;
        float sweepZ = dz / speed * lookAhead;
        float halfWidth = (bb.x1 - bb.x0) * 0.5F - 0.001F;
        float feet = bb.y0;
        float jumpHeight = 1.20F;
        float probeY = feet + 0.51F;
        Vec3D start = new Vec3D(x, probeY, z);
        Vec3D end = new Vec3D(x + sweepX, probeY, z + sweepZ);
        List<?> collisions = level.getCubes(bb.expand(0, jumpHeight, 0).expand(sweepX, 0, sweepZ));

        AABB obstacle = null;
        float nearest = Float.POSITIVE_INFINITY;
        for (Object collision : collisions) {
            AABB box = (AABB) collision;
            float distance = autoJumpContact(box, halfWidth, start, end);
            if (distance < nearest) {
                nearest = distance;
                obstacle = box;
            }
        }
        if (obstacle == null || obstacle.y1 - feet > jumpHeight) return false;

        // A ceiling can clip the jump apex while still leaving room to clear the ledge.
        float requiredHeadroom = bb.y1 + (obstacle.y1 - feet) + 0.01F;
        float contact = Math.min(lookAhead, MathHelper.sqrt(nearest) + 0.05F);
        Vec3D clearanceEnd = new Vec3D(x + dx / speed * contact, probeY, z + dz / speed * contact);
        for (Object collision : collisions) {
            AABB box = (AABB) collision;
            if (box.y1 <= obstacle.y1 || box.y0 >= requiredHeadroom) continue;
            AABB ceiling = new AABB(box.x0, probeY - 1, box.z0, box.x1, probeY + 1, box.z1);
            if (autoJumpContact(ceiling, halfWidth, start, clearanceEnd)
                    < Float.POSITIVE_INFINITY) {
                return false;
            }
        }
        return true;
    }

    private static float autoJumpContact(AABB box, float halfWidth, Vec3D start, Vec3D end) {
        if (start.y <= box.y0 || start.y >= box.y1) return Float.POSITIVE_INFINITY;
        AABB expanded = box.grow(halfWidth, 0, halfWidth);
        if (expanded.contains(start)) return 0;
        MovingObjectPosition contact = expanded.clip(start, end);
        return contact == null ? Float.POSITIVE_INFINITY : start.distanceSquared(contact.vec);
    }

    public float flySpeed = 1.12F; // vertical step per tick while flying

    // --- Mind control state (used by MobControl) ---
    public int mcTicks = 0; // ticks remaining of control
    public float mcTx, mcTy, mcTz; // target point to steer toward
    public float mcNudge = 0.08f; // per-tick steering strength
    private Random random;
    // --- difficulty debuffs ---
    public int frozenTicks = 0;
    // --- custom status effects ---
    public int deltaCurseTicks = 0; // ticks remaining
    public int deltaCurseCounter = 0;

    public int healthDrinkTimer = 0;
    public transient GrapplingHookItem.Tether grapple;

    // poison deals damage every poisonEvery ticks, for poisonTicks total ticks
    public int poisonTicks = 0;
    public int poisonEvery = 0;
    private int poisonCounter = 0;
    // --- Void handling ---
    private int lastJumpTap = 0;
    private int jumpTicks = 0;

    // --- Sneak (crouch) ---
    public boolean sneaking = false;
    private transient boolean ladderGripHeld;

    public boolean isHoldingLadder() {
        return !isFlying && (sneaking || ladderGripHeld);
    }

    private transient float sneakProgress;
    private transient float previousSneakProgress;
    private transient float previousHeightOffset;
    private static final float SNEAK_HOFFSET = 1.42F; // eye height while sneaking
    private static final float STAND_HOFFSET = 1.62F;
    private static final float SNEAK_HEIGHT = 1.50F; // bb height while sneaking
    private static final float STAND_HEIGHT = 1.80F; // bb height while standing

    public boolean isSneaking() {
        return sneaking;
    }

    @Override
    public void tick() {
        if (packedTurretHealth > 0) {
            net.classicremastered.minecraft.level.itemstack.TurretItems.migrateLegacyCrate(this);
        }
        if (environmentalDeathCreditTicks > 0 && --environmentalDeathCreditTicks == 0) {
            environmentalDeathAttacker = null;
        }
        if (!developerPowers().aimingBlock(this)) developerPowers().cancelBlockAim();
        previousHeightOffset = heightOffset;
        previousSneakProgress = sneakProgress;
        GrapplingHookItem.updateTether(this);
        super.tick();
        if (frostbiteSlowTicks > 0) frostbiteSlowTicks--;
        sneakProgress =
                sneaking ? Math.min(1, sneakProgress + .5f) : Math.max(0, sneakProgress - .5f);
    }

    public float frostbiteMovementMultiplier() {
        return frostbiteSlowTicks > 0 ? .65f : 1f;
    }

    public float getSneakProgress(float partial) {
        return lerp(previousSneakProgress, sneakProgress, partial);
    }

    public float getRenderHeightOffset(float partial) {
        return previousHeightOffset > 0
                ? lerp(previousHeightOffset, heightOffset, partial)
                : heightOffset;
    }

    public void dropSelectedItem() {
        dropSelectedItem(false);
    }

    public void dropSelectedItem(boolean wholeStack) {
        if (spectator || level == null || inventory == null)
            return;

        int id = inventory.getSelected();
        int available = inventory.getCount(inventory.selected);
        if (id <= 0 || available <= 0) return;

        Vec3D direction = aimDirection(1);

        // Player.y already includes eye height. Spawn inside the player's
        // footprint so the drop cannot start on the other side of a wall.
        float spawnY = y - .3f;
        int count = wholeStack ? available : 1;
        Entity dropped;
        if (id >= 256) {
            int itemId = id - 256;
            if (itemId >= Item.items.length || Item.items[itemId] == null) return;
            dropped =
                    new net.classicremastered.minecraft.entity.DroppedItem(
                                    level, x, spawnY, z, itemId, count)
                            .withCrate(inventory.crate(inventory.selected));
        } else {
            if (Block.blocks[id] == null) return;
            dropped =
                    new net.classicremastered.minecraft.entity.DroppedBlock(
                            level, x, spawnY, z, id, count);
        }

        dropped.xd = direction.x * .3f;
        dropped.yd = direction.y * .3f + .1f;
        dropped.zd = direction.z * .3f;
        if (inventory.removeSelected(count)) level.addEntity(dropped);
    }

    /**
     * Switch hitbox + eye height while keeping FEET glued and preventing stand-up in tight spaces.
     */
    public void setSneaking(boolean s) {
        if (sneaking == s) return;
        if (!s && level != null) {
            float inset = .001f;
            AABB headroom =
                    new AABB(
                            bb.x0 + inset,
                            bb.y1 + inset,
                            bb.z0 + inset,
                            bb.x1 - inset,
                            bb.y0 + STAND_HEIGHT - inset,
                            bb.z1 - inset);
            if (!level.getCubes(headroom).isEmpty()) return;
        }
        sneaking = s;
        heightOffset = s ? SNEAK_HOFFSET : STAND_HOFFSET;
        setSize(.6f, s ? SNEAK_HEIGHT : STAND_HEIGHT);
        y = bb.y0 + heightOffset - ySlideOffset;
    }

    private void moveRelativeFlying(float strafe, float forward, float accel) {
        // Creative flight style: horizontal only (pitch ignored)
        float yawRad = this.yRot * (float) Math.PI / 180F;

        float sinYaw = MathHelper.sin(yawRad);
        float cosYaw = MathHelper.cos(yawRad);

        // horizontal forward/strafe
        float fx = -sinYaw * forward + cosYaw * strafe;
        float fz = cosYaw * forward + sinYaw * strafe;

        // normalize & scale
        float len = MathHelper.sqrt(fx * fx + fz * fz);
        if (len < 1e-4f) return;
        fx /= len;
        fz /= len;
        fx *= accel;
        fz *= accel;

        this.xd += fx;
        this.zd += fz;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        if (this.spectator == spectator)
            return;
        this.spectator = spectator;
        this.noPhysics = spectator;
        this.xd = this.yd = this.zd = 0;
        this.fallDistance = 0;
        this.onGround = false;
        if (spectator) {
            unmount();
            setSneaking(false);
            developerPowers().cancelBlockAim();
        } else if (level != null
                && level.getCubes(bb).stream().anyMatch(box -> bb.intersects((AABB) box))) {
            setPos(level.xSpawn + 0.5F, level.ySpawn, level.zSpawn + 0.5F);
        }
    }

    @Override
    public boolean isPickable() {
        return !spectator && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !spectator && super.isPushable();
    }

    @Override
    public void aiStep() {
        if (spectator) {
            autoJumpQueued = false;
            this.input.updateMovement();
            boolean controlsActive = minecraft != null && minecraft.currentScreen == null;
            fly(controlsActive && this.input.jumping, controlsActive && controlHeld(),
                    controlsActive && shiftHeld());
            return;
        }
        this.inventory.tick();

        // Handle Health Drink healing timer
        if (healthDrinkTimer > 0) {
            healthDrinkTimer--;
            if (healthDrinkTimer % 25 == 0) {
                // Heals 2 hearts total over 5 seconds (4 hp total).
                // Since 100 ticks / 25 ticks = 4 pulses, healing 1 hp per pulse = 4 hp (2
                // hearts) total.
                this.heal(1);
            }
        }

        this.oBob = this.bob;
        this.input.updateMovement();
        SimpleChunkManager.checkFarLandsWarning(minecraft, this);

        // ===== difficulty debuffs =====
        boolean movementFrozen = frozenTicks > 0;
        if (movementFrozen) {
            autoJumpQueued = false;
            frozenTicks--;

            // Suppress input this tick
            this.input.resetKeys();

            // Kill horizontal motion so you don't slide
            this.xd = 0.0F;
            this.zd = 0.0F;

            // Block jump ascent while frozen (let gravity still act)
            if (this.yd > 0.0F) this.yd = 0.0F;
        }

        // ===== Keys =====
        boolean descendHeld = false;
        boolean boostHeld = false;
        boolean sneakHeld = false;

        // 🚫 Skip movement keybinds while any GUI screen is open (e.g. ChatInputScreen)
        if (this.minecraft != null && this.minecraft.currentScreen == null) {
            if (this.minecraft.settings != null) {
                int descendKeyCode =
                        (this.minecraft.settings.descendKey != null
                                ? this.minecraft.settings.descendKey.key
                                : Keyboard.KEY_LCONTROL);

                int runKeyCode =
                        (this.minecraft.settings.runKey != null
                                ? this.minecraft.settings.runKey.key
                                : Keyboard.KEY_LSHIFT);

                descendHeld = Keyboard.isKeyDown(descendKeyCode); // CTRL by default
                boostHeld = Keyboard.isKeyDown(runKeyCode); // SHIFT by default
                sneakHeld = descendHeld; // Sneak = CTRL
            } else {
                descendHeld =
                        Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                boostHeld =
                        Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                sneakHeld = descendHeld;
            }
        }

        // === Mind control: steering FIRST (so velocity feeds into physics) ===
        if (this.mcTicks > 0) {
            MobControl.prePhysicsSteer(this);
        }

        // --- VOID DAMAGE ---
        if (this.level != null) {
            if (this.y < -30.0f) {
                voidDamageCounter++;
                if (voidDamageCounter >= 20) {
                    voidDamageCounter = 0;
                    this.invulnerableTime = 0;
                    try {
                        super.hurt(null, 2);
                    } catch (Throwable ignored) {
                    }
                    if (this.level.creativeMode && this.health > 0) {
                        this.health -= 2;
                        if (this.health <= 0) {
                            try {
                                this.die(null);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            } else {
                voidDamageCounter = 0;
            }
        }

        // ---- Sneak only if no GUI is open ----
        this.setSneaking(sneakHeld && !this.isFlying);

        // ===== Movement / physics =====

        this.ladderGripHeld = (minecraft == null || minecraft.currentScreen == null) && shiftHeld();
        if (this.isFlying && this.canFly) {
            autoJumpQueued = false;
            fly(this.input.jumping, controlHeld(), shiftHeld());
        } else {
            super.aiStep();
            if (movementFrozen) autoJumpQueued = false;
        }

        // ===== Structure Delta curse effect =====
        if (deltaCurseTicks > 0) {
            deltaCurseCounter++;
            if (deltaCurseCounter >= 1) {
                deltaCurseCounter = 0;
                this.hurt(null, 1, "magic");
            }
            deltaCurseTicks--;
        }

        // === Mind control: FORCED ACTIONS ===
        if (this.mcTicks > 0) {
            MobControl.postPhysicsActions(this);
            this.mcTicks--;
            if (this.mcTicks == 0 && this.minecraft != null && this.minecraft.hud != null) {
                this.minecraft.hud.addChat("&5[MC]&7 control end");
            }
        }

        // --- Force instant lighting update when player is inside a block ---
        if (this.level != null) {
            int px = (int) Math.floor(this.x);
            int py = (int) Math.floor(this.y + 0.12F);
            int pz = (int) Math.floor(this.z);
            this.level.light_insideOpaque = this.level.isLightBlocker(px, py, pz);
        }

        // --- Portal collision check ---
        if (portalCooldown > 0) {
            portalCooldown--;
        } else if (this.level != null) {
            int bx = (int) Math.floor(this.x);
            int by = (int) Math.floor(this.y + 0.12F); // head level
            int bz = (int) Math.floor(this.z);
            int bid = this.level.getTile(bx, by, bz);
            if (bid == net.classicremastered.minecraft.level.tile.Block.PORTAL.id) {
                // Teleport to spawn for now
                this.setPos(
                        this.level.xSpawn + 0.5F, this.level.ySpawn + 1, this.level.zSpawn + 0.5F);

                if (this.minecraft != null && this.minecraft.hud != null) {
                    this.minecraft.hud.addChat("&5[Portal]&7 Teleported to spawn!");
                }

                portalCooldown = 100; // ~5s cooldown
            }
        }

        // ===== Classic bob/tilt & entity touches =====
        float var1 =
                sneaking
                        ? (float) Math.hypot(this.x - this.xo, this.z - this.zo)
                        : MathHelper.sqrt(this.xd * this.xd + this.zd * this.zd);
        float var2 = (float) Math.atan((double) (-this.yd * 0.2F)) * 15.0F;
        if (var1 > 0.1F) var1 = 0.1F;
        if (!this.onGround || this.health <= 0) var1 = 0.0F;
        if (this.onGround || this.health <= 0) var2 = 0.0F;
        this.bob += (var1 - this.bob) * 0.4F;
        this.tilt += (var2 - this.tilt) * 0.8F;

        // poison tick
        if (!this.level.creativeMode && poisonTicks > 0) {
            poisonCounter++;
            if (poisonCounter >= poisonEvery) {
                poisonCounter = 0;
                this.hurt(null, 1, "magic");
            }
            poisonTicks--;
        }

        List var3;
        if (this.health > 0
                && (var3 = this.level.findEntities(this, this.bb.grow(1.0F, 0.0F, 1.0F))) != null) {
            for (int i = 0; i < var3.size(); ++i) {
                Entity entity = (Entity) var3.get(i);
                if (!net.classicremastered.minecraft.level.itemstack.GunEntityDrag.isHeld(entity))
                    entity.playerTouch(this);
            }
            if (this.health > 0) {
                List nearby = this.level.findEntities(this, this.bb.grow(0.5F, 0.5F, 0.5F));
                for (int i = 0; i < nearby.size(); i++) {
                    Entity entity = (Entity) nearby.get(i);
                    if (!net.classicremastered.minecraft.level.itemstack.GunEntityDrag.isHeld(
                            entity)) entity.playerTouch(this);
                }
            }
        }
    }

    private boolean controlHeld() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private boolean shiftHeld() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private void fly(boolean jumpHeld, boolean ctrlHeld, boolean shiftHeld) {
        this.fallDistance = 0.0F;
        this.onGround = false;

        // --- Speeds ---
        float boost = spectator ? 1.0F : developerPowers().movementMultiplier(this);
        float base = Math.max(0.05F, this.flySpeed) * boost;
        float accel = shiftHeld ? base * 3.0F : base * 2.0F; // Shift = faster

        float strafe = movementStrafe();
        float forward = this.input.yya;
        boolean up = jumpHeld;
        boolean down = ctrlHeld;

        // --- Directional flight (yaw/pitch aware) ---
        this.moveRelativeFlying(strafe, forward, accel * 0.35F);

        // --- Vertical control (fixed) ---
        float vertSpeed = accel * 0.55F;
        if (up && !down)
            this.yd = lerp(this.yd, vertSpeed, 0.3F);
        if (down && !up)
            this.yd = lerp(this.yd, -vertSpeed, 0.3F);
        if (!up && !down)
            this.yd *= 0.6F; // hover

        // --- Move ---
        this.move(this.xd, this.yd, this.zd);

        // --- Stop instantly if no input ---
        if (strafe == 0.0F && forward == 0.0F && !up && !down) {
            this.xd = 0.0F;
            this.yd = 0.0F;
            this.zd = 0.0F;
        } else {
            // otherwise apply normal damping
            this.xd *= 0.90F;
            this.yd *= 0.90F;
            this.zd *= 0.90F;
        }

        // --- Clamp speed ---
        float max = (shiftHeld ? 1.4F : 0.9F) * boost;
        float mag = MathHelper.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        if (mag > max) {
            float s = max / mag;
            this.xd *= s;
            this.yd *= s;
            this.zd *= s;
        }
    }

    public void freezeFor(int ticks) {
        if (ticks > frozenTicks) frozenTicks = ticks;
    }

    public void applyPoison(int ticks, int everyNTicks) {
        poisonTicks = Math.max(poisonTicks, ticks);
        poisonEvery = Math.max(1, everyNTicks);
        // poisonCounter intentionally not reset to avoid re-sync on re-application
    }

    @Override
    public void bindTexture(TextureManager var1) {
        if (newTexture != null) {
            newTextureId = var1.load(newTexture);
            newTexture = null;
        }

        int var2;
        if (newTextureId < 0) {
            var2 = var1.load("/char.png");
            GL11.glBindTexture(3553, var2);
        } else {
            var2 = newTextureId;
            GL11.glBindTexture(3553, var2);
        }
    }

    @Override
    public void render(TextureManager var1, float var2) {
        if (spectator)
            return;
        if (minecraft != null
                && minecraft.player == this
                && minecraft.renderer != null
                && !minecraft.renderer.shouldRenderLocalPlayer()) return;
        if (this.getClass() != Player.class
                || (this.minecraft != null && this.minecraft.cameraMode > 0)) {
            super.render(var1, var2);
        }
    }

    // --- Hard kill that bypasses Creative's "no damage" guard ---
    private void killInVoid() {
        // Clear any temporary invulnerability frames
        this.invulnerableTime = 0;

        // If hurt() is blocked by Creative, fall back to a hard kill.
        // Try normal damage path first so sounds/flows run:
        try {
            // Use a big number; attacker = null so it's environmental
            super.hurt(null, 9999);
        } catch (Throwable ignored) {
        }

        if (this.health > 0) {
            // Ensure death even if Creative blocked super.hurt()
            this.health = 0;
            try {
                this.die(null);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void resetPos() {
        unmount();
        this.heightOffset = 1.62F;
        this.setSize(0.6F, 1.8F);

        if (this.level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat) {
            // Infinite: spawn directly at Level spawn Y
            this.setPos(
                    (float) this.level.xSpawn + 0.5F,
                    (float) this.level.ySpawn,
                    (float) this.level.zSpawn + 0.5F);
        } else {
            super.resetPos(); // vanilla safe fall logic
        }

        if (this.level != null) {
            this.level.player = this;
        }

        clearStatusEffects();
        this.health = this.lastHealth = MAX_HEALTH;
        this.dead = false;
        this.deathTime = 0;
        this.hurtTime = this.hurtDuration = this.invulnerableTime = this.attackTime = 0;
        this.hurtDir = 0;
        this.airSupply = TOTAL_AIR_SUPPLY;
        this.fallDistance = this.ySlideOffset = 0;
        this.xd = this.yd = this.zd = 0;
        this.onGround = this.horizontalCollision = this.collision = false;
        this.damageCause = null;
        resetInterpolation();
        if (this.blockMap != null) this.blockMap.moved(this);
        this.deathMessageSent = false;
        this.environmentalDeathAttacker = null;
        this.environmentalDeathCreditTicks = 0;
    }

    private void clearStatusEffects() {
        net.classicremastered.minecraft.entity.Arrow.removeStuckArrows(this);
        remainingFireTicks = 0;
        poisonTicks = poisonCounter = poisonEvery = 0;
        deltaCurseTicks = deltaCurseCounter = 0;
        frozenTicks = frostbiteSlowTicks = 0;
        healthDrinkTimer = 0;
        voidDamageCounter = 0;
        endMindControl();
    }

    @Override
    public void die(Entity killer) {
        if (!deathMessageSent) {
            deathMessageSent = true;
            if (minecraft != null && minecraft.hud != null) {
                minecraft.hud.addChat(
                        DeathMessages.message(
                                this,
                                killer,
                                y < -30 ? "void" : damageCause,
                                environmentalDeathAttacker));
            }
        }
        clearStatusEffects();
        unmount();
        ejectPassengers();
        // Clear grappling hook anchor on death
        GrapplingHookItem.detach(this);
        // existing physics
        this.setSize(0.2F, 0.2F);
        this.setPos(this.x, this.y, this.z);
        this.yd = 0.1F;
        if (killer != null) {
            this.xd = -MathHelper.cos((this.hurtDir + this.yRot) * 3.1415927F / 180.0F) * 0.1F;
            this.zd = -MathHelper.sin((this.hurtDir + this.yRot) * 3.1415927F / 180.0F) * 0.1F;
        } else {
            this.xd = this.zd = 0.0F;
        }
        this.heightOffset = 0.1F;

        // --- lose all coins ---
        if (level == null || !level.keepInventory) this.coins = 0;

        // Equipment follows the survival inventory death policy.
        if (level != null && !level.isNetworkMode() && !level.keepInventory)
            for (int i = 0; i < equipment.length; i++) {
                if (equipment[i] >= 256)
                    level.addEntity(
                            new net.classicremastered.minecraft.entity.DroppedItem(
                                    level, x, y, z, equipment[i] - 256));
                equipment[i] = 0;
            }
        // --- drop all inventory items/blocks ---
        if (this.inventory != null && this.level != null && !level.keepInventory) {
            java.util.Random rnd = this.level.random;
            for (int i = 0; i < this.inventory.totalSize(); i++) {
                int id = this.inventory.getId(i);
                int count = this.inventory.getCount(i);
                if (id > 0 && count > 0) {
                    // Random scatter offset around the player's feet
                    float rx = (rnd.nextFloat() - 0.5F) * 0.4F;
                    float ry = rnd.nextFloat() * 0.3F + 0.1F;
                    float rz = (rnd.nextFloat() - 0.5F) * 0.4F;

                    Entity dropped;
                    if (id >= 256) {
                        dropped =
                                new net.classicremastered.minecraft.entity.DroppedItem(
                                                this.level,
                                                this.x + rx,
                                                this.y + ry,
                                                this.z + rz,
                                                id - 256,
                                                count)
                                        .withCrate(inventory.crate(i));
                    } else {
                        dropped =
                                new net.classicremastered.minecraft.entity.DroppedBlock(
                                        this.level,
                                        this.x + rx,
                                        this.y + ry,
                                        this.z + rz,
                                        id,
                                        count);
                    }

                    // Scatter velocity
                    dropped.xd = (rnd.nextFloat() - 0.5F) * 0.2F;
                    dropped.yd = rnd.nextFloat() * 0.2F + 0.1F;
                    dropped.zd = (rnd.nextFloat() - 0.5F) * 0.2F;

                    this.level.addEntity(dropped);

                    // Clear slot
                    this.inventory.setIdCount(i, -1, 0);
                }
            }
        }

        // Use the modern player damage sound on death as well.
        if (this.level != null) {
            this.level.playSound("damage/hit", this, 1.0F, 1.0F);
        }
    }

    public int coins = 0;
    public int packedTurretHealth;
    public int packedTurretAmmo;
    public boolean packedTurretEnabled;

    @Override
    public void hurt(Entity attacker, int damage) {
        if (spectator || health <= 0 || dead || removed) return;
        if (attacker != null && developerPowers().dodging(this)) return;
        damage = developerPowers().incomingDamage(this, attacker, damage);
        if (!this.level.creativeMode) {
            int previousHealth = this.health;
            super.hurt(attacker, damage);
            if (attacker instanceof net.classicremastered.minecraft.mob.Frostbite
                    && health > 0
                    && health < previousHealth) {
                frostbiteSlowTicks = 60;
            }
            Entity owner = DeathMessages.resolveAttacker(attacker);
            if (owner != null && this.health > 0 && this.health < previousHealth) {
                environmentalDeathAttacker =
                        owner instanceof net.classicremastered.minecraft.mob.Zombie zombie
                                ? zombie
                                : null;
                environmentalDeathCreditTicks = environmentalDeathAttacker == null ? 0 : 100;
            }

            if (this.level != null && this.health > 0 && this.health < previousHealth) {
                this.level.playSound("damage/hit", this, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public boolean isShootable() {
        return true;
    }

    @Override
    public void awardKillScore(Entity e, int value) {
        this.score += value;
    }

    @Override
    public void remove() {}

    @Override
    public boolean isCreativeModeAllowed() {
        return true;
    }

    public static final long serialVersionUID = 0L;

    // Serialized by old Java-format worlds before ammo became an inventory item.
    private int arrows;

    public void migrateLegacyArrows() {
        if (arrows <= 0) return;
        net.classicremastered.minecraft.level.itemstack.ItemRegistry.bootstrap();
        int itemId = net.classicremastered.minecraft.level.itemstack.Equipment.ARROW.id;
        while (arrows > 0 && inventory.addResource(itemId + 256)) arrows--;
        if (arrows > 0) {
            level.addEntity(
                    new net.classicremastered.minecraft.entity.DroppedItem(
                            level, x, y, z, itemId, arrows));
            arrows = 0;
        }
    }

    public static final int MAX_HEALTH = 20;
    private transient DeveloperPowers developerPowers;

    public DeveloperPowers developerPowers() {
        if (developerPowers == null) developerPowers = new DeveloperPowers();
        return developerPowers;
    }

    public transient InputHandler input;
    public Inventory inventory = new Inventory();
    public byte userType = 0;
    public float oBob;
    public float bob;
    public int score = 0;
    public transient net.classicremastered.minecraft.Minecraft minecraft;
    public static boolean creativeInvulnerable = false; // set by GameMode
    private static int newTextureId = -1;
    public static volatile BufferedImage newTexture;
    // --- Villager trust system ---
    // --- Villager interaction stats ---
    public int villagerReputation = 0; // -100 .. +100
    public int villagerKills = 0;

    public void releaseAllKeys() {
        this.input.resetKeys();
    }

    public void startMindControl(float tx, float ty, float tz, int ticks, float nudge) {
        MobControl.start(this, tx, ty, tz, ticks, nudge);
    }

    public void endMindControl() {
        MobControl.end(this);
    }

    public void setKey(int var1, boolean var2) {
        this.input.setKeyState(var1, var2);
    }

    public boolean addResource(int var1) {
        return this.inventory.addResource(var1);
    }

    public int getScore() {
        return this.score;
    }

    public HumanoidModel getModel() {
        return (HumanoidModel) modelCache.getModel(this.modelName);
    }
}
