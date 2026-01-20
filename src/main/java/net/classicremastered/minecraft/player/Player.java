package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.sound.PaulsCodeSoundManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

public class Player extends Mob {
    public Player(Level var1) {
        super(var1);
        if (var1 != null) {
            var1.player = this;
            var1.removeEntity(this);
            var1.addEntity(this);
        }

        this.heightOffset = 1.62F;
        this.health = 20;
        this.modelName = "humanoid";
        this.rotOffs = 180.0F;
        this.ai = new Player$1(this);
    }

    private int portalCooldown = 0; // ticks before re-using a portal

    // --- Void damage tracking ---
    private int voidDamageCounter = 0; // counts ticks under y = -30

    public boolean canFly = false; // set by GameMode.apply(...)
    public boolean isFlying = false; // toggled by key in Creative
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

    // poison deals damage every poisonEvery ticks, for poisonTicks total ticks
    public int poisonTicks = 0;
    public int poisonEvery = 0;
    private int poisonCounter = 0;
    // --- Void handling ---
    private boolean voidWarned = false; // one-shot warning when crossing below y<0 (Creative)
    private int lastJumpTap = 0;
    private int jumpTicks = 0;

    // --- Sneak (crouch) ---
    public boolean sneaking = false;
    private static final float SNEAK_HOFFSET = 1.42F; // eye height while sneaking
    private static final float STAND_HOFFSET = 1.62F;
    private static final float SNEAK_HEIGHT = 1.50F; // bb height while sneaking
    private static final float STAND_HEIGHT = 1.80F; // bb height while standing

    public boolean isSneaking() {
        return sneaking;
    }

    // Player.java
    public void dropSelectedItem() {
        if (this.level == null || this.inventory == null)
            return;

        int id = this.inventory.getSelected();
        if (id < 0)
            return; // nothing selected

        int count = this.inventory.getCount(this.inventory.selected);
        if (count <= 0)
            return;

        // remove the full stack from inventory
        this.inventory.removeSelected(count);

        // --- Modern-style drop spawn point ---
        float eyeHeight = this.heightOffset;
        float dirX = -MathHelper.sin(this.yRot * (float) Math.PI / 180.0F)
                * MathHelper.cos(this.xRot * (float) Math.PI / 180.0F);
        float dirY = -MathHelper.sin(this.xRot * (float) Math.PI / 180.0F);
        float dirZ = MathHelper.cos(this.yRot * (float) Math.PI / 180.0F)
                * MathHelper.cos(this.xRot * (float) Math.PI / 180.0F);

        // spawn ~0.5 blocks in front of eyes
        float spawnX = this.x + dirX * 0.5F;
        float spawnY = this.y + eyeHeight - 0.3F; // a bit below eyes, like modern MC
        float spawnZ = this.z + dirZ * 0.5F;

        Entity dropped;
        if (id >= 256) {
            dropped = new net.classicremastered.minecraft.entity.DroppedItem(this.level, spawnX, spawnY, spawnZ,
                    id - 256);
        } else {
            dropped = new net.classicremastered.minecraft.entity.DroppedBlock(this.level, spawnX, spawnY, spawnZ, id);
        }

        // --- Modern throw velocity ---
        float throwPower = 0.35F;
        dropped.xd = dirX * throwPower;
        dropped.yd = dirY * throwPower + 0.1F;
        dropped.zd = dirZ * throwPower;

        this.level.addEntity(dropped);

        // play sound
        this.level.playSound("random/pop", this, 0.2F, 1.0F);
    }

    /**
     * Switch hitbox + eye height while keeping FEET glued and preventing stand-up
     * in tight spaces.
     */
    public void setSneaking(boolean s) {
        if (this.sneaking == s)
            return;

        // Keep current feet Y (true bottom of the AABB)
        final float feetY = (this.bb != null) ? this.bb.y0 : (this.y - this.heightOffset + this.ySlideOffset);

        if (!s) {
            // Attempt to UNSNEAK: make sure there's room for a standing AABB at current
            // feet
            final float hw = 0.6F * 0.5F;
            final float targetHeight = STAND_HEIGHT;

            net.classicremastered.minecraft.phys.AABB test = new net.classicremastered.minecraft.phys.AABB(this.x - hw,
                    feetY, this.z - hw, this.x + hw, feetY + targetHeight, this.z + hw);

            // If any solid cubes intersect, refuse to stand up
            if (this.level != null && !this.level.getCubes(test).isEmpty()) {
                // stay sneaking
                s = true;
            }
        }

        // Apply state
        this.sneaking = s;

        // Set new eye height + bb size
        this.heightOffset = s ? SNEAK_HOFFSET : STAND_HOFFSET;
        this.setSize(0.6F, s ? SNEAK_HEIGHT : STAND_HEIGHT);

        // Reposition so FEET stay where they were (prevents tiny “falls” and damage)
        // y = feet + eyeHeight - ySlideOffset (matches Entity.move write-back)
        this.y = feetY + this.heightOffset - this.ySlideOffset;

        // Rebuild AABB around new dimensions without changing x/z
        if (this.bb == null) {
            this.setPos(this.x, this.y, this.z);
        } else {
            float hw = this.bbWidth * 0.5F;
            float hh = this.bbHeight * 0.5F;
            this.bb.x0 = this.x - hw;
            this.bb.x1 = this.x + hw;
            this.bb.y0 = feetY;
            this.bb.y1 = feetY + this.bbHeight;
            this.bb.z0 = this.z - hw;
            this.bb.z1 = this.z + hw;
        }
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
        if (len < 1e-4f)
            return;
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

    @Override
    public void aiStep() {
        this.inventory.tick();
        this.oBob = this.bob;
        this.input.updateMovement();
        SimpleChunkManager.checkFarLandsWarning(minecraft, this);

        // ===== difficulty debuffs =====
        if (frozenTicks > 0) {
            frozenTicks--;

            // Suppress input this tick
            this.input.resetKeys();

            // Kill horizontal motion so you don't slide
            this.xd = 0.0F;
            this.zd = 0.0F;

            // Block jump ascent while frozen (let gravity still act)
            if (this.yd > 0.0F)
                this.yd = 0.0F;
        }

        // ===== Keys =====
        boolean descendHeld = false;
        boolean boostHeld = false;
        boolean sneakHeld = false;

        // 🚫 Skip movement keybinds while any GUI screen is open (e.g. ChatInputScreen)
        if (this.minecraft != null && this.minecraft.currentScreen == null) {
            if (this.minecraft.settings != null) {
                int descendKeyCode = (this.minecraft.settings.descendKey != null
                        ? this.minecraft.settings.descendKey.key
                        : Keyboard.KEY_LCONTROL);

                int runKeyCode = (this.minecraft.settings.runKey != null ? this.minecraft.settings.runKey.key
                        : Keyboard.KEY_LSHIFT);

                descendHeld = Keyboard.isKeyDown(descendKeyCode); // CTRL by default
                boostHeld = Keyboard.isKeyDown(runKeyCode); // SHIFT by default
                sneakHeld = descendHeld; // Sneak = CTRL
            } else {
                descendHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                boostHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                sneakHeld = descendHeld;
            }
        }

        // === Mind control: steering FIRST (so velocity feeds into physics) ===
        if (this.mcTicks > 0) {
            MobControl.prePhysicsSteer(this);
        }

        // --- VOID WARNING & DAMAGE ---
        if (this.level != null) {
            if (this.y < 0.0f) {
                if (!voidWarned && this.level.creativeMode && this.minecraft != null && this.minecraft.hud != null) {
                    this.minecraft.hud.addChat(
                            "YOU ARE FALLING THOUGH THE WORLD! CREATIVE PLAYER, PLEASE RETURN OR YOU'LL BE KILLED");
                    voidWarned = true;
                }
            } else {
                voidWarned = false;
            }

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

        // ===== Modern Creative Flight Controls =====
        boolean jumpHeld = this.input.jumping; // Space
        boolean ctrlHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL); // Ctrl
                                                                                                                   // =
                                                                                                                   // down
        boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT); // Shift
                                                                                                                // =
                                                                                                                // boost


        if (this.isFlying && this.canFly) {
            this.fallDistance = 0.0F;
            this.onGround = false;

            // --- Speeds ---
            float base = Math.max(0.05F, this.flySpeed);
            float accel = shiftHeld ? base * 3.0F : base * 2.0F; // Shift = faster

            float strafe = this.input.xxa;
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
            float max = shiftHeld ? 1.4F : 0.9F;
            float mag = MathHelper.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
            if (mag > max) {
                float s = max / mag;
                this.xd *= s;
                this.yd *= s;
                this.zd *= s;
            }
        } else {
            super.aiStep();
        }

        // ===== Structure Delta curse effect =====
        if (deltaCurseTicks > 0) {
            deltaCurseCounter++;
            if (deltaCurseCounter >= 1) {
                deltaCurseCounter = 0;
                this.hurt(null, 1);
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
                this.setPos(this.level.xSpawn + 0.5F, this.level.ySpawn + 1, this.level.zSpawn + 0.5F);

                if (this.minecraft != null && this.minecraft.hud != null) {
                    this.minecraft.hud.addChat("&5[Portal]&7 Teleported to spawn!");
                }

                portalCooldown = 100; // ~5s cooldown
            }
        }

        // ===== Classic bob/tilt & entity touches =====
        float var1 = MathHelper.sqrt(this.xd * this.xd + this.zd * this.zd);
        float var2 = (float) Math.atan((double) (-this.yd * 0.2F)) * 15.0F;
        if (var1 > 0.1F)
            var1 = 0.1F;
        if (!this.onGround || this.health <= 0)
            var1 = 0.0F;
        if (this.onGround || this.health <= 0)
            var2 = 0.0F;
        this.bob += (var1 - this.bob) * 0.4F;
        this.tilt += (var2 - this.tilt) * 0.8F;

        // poison tick
        if (!this.level.creativeMode && poisonTicks > 0) {
            poisonCounter++;
            if (poisonCounter >= poisonEvery) {
                poisonCounter = 0;
                this.hurt(null, 1);
            }
            poisonTicks--;
        }

        List var3;
        if (this.health > 0 && (var3 = this.level.findEntities(this, this.bb.grow(1.0F, 0.0F, 1.0F))) != null) {
            for (int i = 0; i < var3.size(); ++i) {
                ((Entity) var3.get(i)).playerTouch(this);
            }
            if (this.health > 0) {
                List nearby = this.level.findEntities(this, this.bb.grow(0.5F, 0.5F, 0.5F));
                for (int i = 0; i < nearby.size(); i++) {
                    ((Entity) nearby.get(i)).playerTouch(this);
                }
            }
        }
    }

    public void freezeFor(int ticks) {
        if (ticks > frozenTicks)
            frozenTicks = ticks;
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
        this.heightOffset = 1.62F;
        this.setSize(0.6F, 1.8F);

        if (this.level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat) {
            // Infinite: spawn directly at Level spawn Y
            this.setPos((float) this.level.xSpawn + 0.5F, (float) this.level.ySpawn, (float) this.level.zSpawn + 0.5F);
        } else {
            super.resetPos(); // vanilla safe fall logic
        }

        if (this.level != null) {
            this.level.player = this;
        }

        this.health = 20;
        this.deathTime = 0;
    }

    @Override
    public void die(Entity killer) {
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
        this.coins = 0;

        // --- clear inventory (lose all items) ---
        if (this.inventory != null) {
            for (int i = 0; i < this.inventory.slots.length; i++) {
                this.inventory.slots[i] = -1;
                this.inventory.count[i] = 0;
            }
            this.arrows = 0; // also clear arrows
        }

        // --- new death sound ---
        if (this.level != null) {
            this.level.playSound("random/classic_hurt", this, 1.0F, 1.0F);
        }
    }

    public int coins = 0;

    @Override
    public void hurt(Entity attacker, int damage) {
        if (!this.level.creativeMode) {
            super.hurt(attacker, damage);

            // --- new hurt sound ---
            if (this.level != null && this.health > 0) { // only if still alive
                this.level.playSound("random/classic_hurt", this, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public boolean isShootable() {
        return true;
    }

    @Override
    public void awardKillScore(Entity e, int value) {
        super.awardKillScore(e, value);
        int add = 0;
        if (e instanceof net.classicremastered.minecraft.mob.Zombie)
            add = 1;
        if (e instanceof net.classicremastered.minecraft.mob.Skeleton)
            add = 2;
        if (e instanceof net.classicremastered.minecraft.mob.Spider)
            add = 2;
        if (e instanceof net.classicremastered.minecraft.mob.Enderman)
            add = 4;
        this.coins += add;
    }

    @Override
    public void remove() {
    }

    @Override
    public boolean isCreativeModeAllowed() {
        return true;
    }

    public static final long serialVersionUID = 0L;
    public static final int MAX_HEALTH = 20;
    public static final int MAX_ARROWS = 99;
    public transient InputHandler input;
    public Inventory inventory = new Inventory();
    public byte userType = 0;
    public float oBob;
    public float bob;
    public int score = 0;
    public int arrows = 20;
    public transient net.classicremastered.minecraft.Minecraft minecraft;
    public static boolean creativeInvulnerable = false; // set by GameMode
    private static int newTextureId = -1;
    public static BufferedImage newTexture;
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
