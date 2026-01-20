package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.AI;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.model.MD3Cache;
import net.classicremastered.minecraft.model.MD3Model;
import net.classicremastered.minecraft.model.MD3Renderer;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.model.ModelManager;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

public abstract class Mob extends Entity {

    public static final long serialVersionUID = 0L;
    public static final int ATTACK_DURATION = 5;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static ModelManager modelCache;

    public int invulnerableDuration = 20;

    public float rot;
    public float timeOffs;
    public float speed;
    public float rotA = (float) (Math.random() + 1.0D) * 0.01F;

    public float yBodyRot = 0.0F;
    public float yBodyRotO = 0.0F;
    public float oRun;
    public float run;
    public float animStep;
    public float animStepO;

    public int tickCount = 0;
    // --- AI Corruption (Physics Gun upgrade) ---
    public boolean corruptedToDefendPlayer = false; // added

    public boolean hasHair = true;
    protected String textureName = "/char.png";
    public boolean allowAlpha = true;
    public float rotOffs = 0.0F;
    public String modelName = null;
    protected float bobStrength = 1.0F;
    protected int deathScore = 0;
    public float renderOffset = 0.0F;
    public float modelGroundOffset = 23.0F;

    public int health = 20;
    public int lastHealth;
    public int invulnerableTime = 0;
    public int airSupply = 300;
    public int hurtTime;
    public int hurtDuration;
    public float hurtDir = 0.0F;
    public int deathTime = 0;
    public int attackTime = 0;
    public float oTilt;
    public float tilt;
    protected boolean dead = false;

    public transient AI ai;
    
    public Mob(Level var1) {
        super(var1);
        this.setPos(this.x, this.y, this.z);
        this.timeOffs = (float) Math.random() * 12398.0F;
        this.rot = (float) (Math.random() * Math.PI * 2.0D);
        this.speed = 1.0F;
        this.ai = new BasicAI();
        this.footSize = 0.5F;
    }

    // Mob.java (inside class)
    protected String soundIdle = null;
    protected String soundHurt = null;
    protected String soundDeath = null;
    protected float idlePitchMin = 1.0F, idlePitchMax = 1.0F;
    protected float hurtPitchMin = 1.0F, hurtPitchMax = 1.0F;
    protected float deathPitchMin = 1.0F, deathPitchMax = 1.0F;

    // helper: get random pitch between min/max
    protected float randPitch(float min, float max) {
        return (min == max) ? min : (min + level.random.nextFloat() * (max - min));
    }

    // idle timing
    private int idleSoundDelay = 0;

    /**
     * Keep entities pickable so the ray can select them in Creative and Survival.
     */
    public boolean isPickable() {
        return !this.removed;
    }

    public boolean isPushable() {
        return !this.removed;
    }

    public boolean isBaby() {
        return false;
    }

    public short getRegistryId() {
        return net.classicremastered.minecraft.mob.MobRegistry.idOf(this);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount < Integer.MAX_VALUE)
            this.tickCount++;
        // play idle sound every few seconds
        if (this.health > 0 && this.soundIdle != null) {
            if (--idleSoundDelay <= 0) {
                float pitch = randPitch(idlePitchMin, idlePitchMax);
                this.level.playSound(this.soundIdle, this, 1.0F, pitch);
                idleSoundDelay = 80; // 80 ticks ≈ 4 seconds at 20 TPS
            }
        }
        this.oTilt = this.tilt;
        if (this.attackTime > 0)
            --this.attackTime;
        if (this.hurtTime > 0)
            --this.hurtTime;
        if (this.invulnerableTime > 0)
            --this.invulnerableTime;

        // ===== Death countdown & corpse physics =====
        if (this.health <= 0) {
            ++this.deathTime;

            // Light gravity + drag so the corpse settles instead of floating
            this.yd -= 0.08F; // gravity
            move(this.xd, this.yd, this.zd);
            this.xd *= 0.60F; // strong damping
            this.yd *= 0.60F;
            this.zd *= 0.60F;
            if (this.onGround) { // extra friction if resting
                this.xd *= 0.70F;
                this.zd *= 0.70F;
            }

            if (this.deathTime > 20) {
                if (this.ai != null)
                    this.ai.beforeRemove();
                this.remove();
            }
            // Skip normal AI/animation driving while dying
            return;
        }

        // ===== Normal living logic =====

        // fluids / damage
        if (this.isUnderWater()) {
            if (this.airSupply > 0) {
                --this.airSupply;
            }
            if (this.airSupply == 0 && (this.tickCount % 20) == 0) {
                if (!(this instanceof net.classicremastered.minecraft.player.Player
                        && net.classicremastered.minecraft.player.Player.creativeInvulnerable)) {
                    this.hurt(null, 2);
                }
            }
        } else {
            if (this.airSupply < TOTAL_AIR_SUPPLY) {
                this.airSupply = Math.min(TOTAL_AIR_SUPPLY, this.airSupply + 4);
            }
        }
        if (this.isInWater())
            this.fallDistance = 0.0F;
        if (this.isInLava())
            this.hurt(null, 10);

        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;

        // Always run physics
        if (this.rider == null) {
            this.aiStep();
        }

        // Walk/run animation bookkeeping
        float dx = this.x - this.xo, dz = this.z - this.zo;
        float dist = net.classicremastered.util.MathHelper.sqrt(dx * dx + dz * dz);
        float bodyYaw = this.yBodyRot, walk = 0.0F;
        this.oRun = this.run;
        float moving = 0.0F;
        if (dist > 0.05F) {
            moving = 1.0F;
            walk = dist * 3.0F;
            bodyYaw = (float) (Math.atan2((double) dz, (double) dx) * 180.0D / (float) Math.PI) - 90.0F;
        }
        if (!this.onGround)
            moving = 0.0F;
        this.run += (moving - this.run) * 0.3F;

        float dyaw = bodyYaw - this.yBodyRot;
        while (dyaw < -180.0F)
            dyaw += 360.0F;
        while (dyaw >= 180.0F)
            dyaw -= 360.0F;
        this.yBodyRot += dyaw * 0.1F;

        float headDiff = this.yRot - this.yBodyRot;
        while (headDiff < -180.0F)
            headDiff += 360.0F;
        while (headDiff >= 180.0F)
            headDiff -= 360.0F;
        boolean flip = headDiff < -90.0F || headDiff >= 90.0F;
        if (headDiff < -75.0F)
            headDiff = -75.0F;
        if (headDiff >= 75.0F)
            headDiff = 75.0F;
        this.yBodyRot = this.yRot - headDiff;
        this.yBodyRot += headDiff * 0.1F;
        if (flip)
            walk = -walk;

        while (this.yRot - this.yRotO < -180.0F)
            this.yRotO -= 360.0F;
        while (this.yRot - this.yRotO >= 180.0F)
            this.yRotO += 360.0F;
        while (this.yBodyRot - this.yBodyRotO < -180.0F)
            this.yBodyRotO -= 360.0F;
        while (this.yBodyRot - this.yBodyRotO >= 180.0F)
            this.yBodyRotO += 360.0F;
        while (this.xRot - this.xRotO < -180.0F)
            this.xRotO += 360.0F;
        while (this.xRot - this.xRotO >= 180.0F)
            this.xRotO -= 360.0F;
        this.animStep += walk;

        if (!this.horizontalCollision) {
            this.ySlideOffset *= 0.6F;
        }
    }

    public void aiStep() {
        if (this.ai != null) {
            this.ai.tick(this.level, this);
        }
    }

    protected void bindTexture(TextureManager tm) {
        if (textureName == null || textureName.isEmpty()) {
            System.err.println("[Mob] Missing textureName for " + this.getClass().getSimpleName());
            return;
        }
        this.textureId = tm.load(textureName);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }


    @Override
    public void render(TextureManager tm, float partial) {
        // Use registered renderer if available
        net.classicremastered.minecraft.render.entity.MobRenderer<Mob> r = net.classicremastered.minecraft.render.entity.RenderManager
                .getRenderer(this);
        if (r == null && this.modelName == null)
            return;

        // ---- orientation interpolation ----
        while (this.yBodyRotO - this.yBodyRot < -180.0F)
            this.yBodyRotO += 360.0F;
        while (this.yBodyRotO - this.yBodyRot >= 180.0F)
            this.yBodyRotO -= 360.0F;
        while (this.xRotO - this.xRot < -180.0F)
            this.xRotO += 360.0F;
        while (this.xRotO - this.xRot >= 180.0F)
            this.xRotO -= 360.0F;
        while (this.yRotO - this.yRot < -180.0F)
            this.yRotO += 360.0F;
        while (this.yRotO - this.yRot >= 180.0F)
            this.yRotO -= 360.0F;

        float bodyRot = this.yBodyRotO + (this.yBodyRot - this.yBodyRotO) * partial;
        float runAmt = this.oRun + (this.run - this.oRun) * partial;
        float yaw = this.yRotO + (this.yRot - this.yRotO) * partial - bodyRot;
        float pitch = this.xRotO + (this.xRot - this.xRotO) * partial;

        GL11.glPushMatrix();
        float anim = this.animStepO + (this.animStep - this.animStepO) * partial;

        // ---- base brightness color ----
        float baseBright = this.getBrightness(partial);
        GL11.glColor3f(baseBright, baseBright, baseBright);

        float scale = 0.0625F;

        // ---- model ground lift ----
        float groundLift = 23.0F;
        if (this.modelName != null && this.modelName.endsWith(".md3")) {
            groundLift = this.modelGroundOffset;
        } else if (modelCache != null && this.modelName != null) {
            Model mm = modelCache.getModel(this.modelName);
            if (mm != null)
                groundLift = mm.groundOffset;
        }

        float bob = -Math.abs(MathHelper.cos(anim * 0.6662F)) * 5.0F * runAmt * this.bobStrength - groundLift;

        // ---- move to entity ----
        GL11.glTranslatef(this.xo + (this.x - this.xo) * partial,
                this.yo + (this.y - this.yo) * partial - this.heightOffset + this.renderOffset,
                this.zo + (this.z - this.zo) * partial);

        // ---- hurt/death wobble ----
        float ht = (float) this.hurtTime - partial;
        if (ht > 0.0F || this.health <= 0) {
            if (ht < 0.0F)
                ht = 0.0F;
            else
                ht = MathHelper.sin((ht /= (float) this.hurtDuration) * ht * ht * ht * (float) Math.PI) * 14.0F;

            if (this.health <= 0) {
                float deathF = ((float) this.deathTime + partial) / 20.0F;
                ht += deathF * deathF * 800.0F;
                if (ht > 90.0F)
                    ht = 90.0F;
            }

            float dir = this.hurtDir;
            GL11.glRotatef(180.0F - bodyRot + this.rotOffs, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-dir, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-ht, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(dir, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-(180.0F - bodyRot + this.rotOffs), 0.0F, 1.0F, 0.0F);
        }

        GL11.glTranslatef(0.0F, -bob * scale, 0.0F);
        GL11.glScalef(1.0F, -1.0F, 1.0F);
        GL11.glRotatef(180.0F - bodyRot + this.rotOffs, 0.0F, 1.0F, 0.0F);

        // Alpha/Cull state
        if (!this.allowAlpha)
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        else
            GL11.glDisable(GL11.GL_CULL_FACE);

        // ==== Delegated render ====
        GL11.glScalef(-1.0F, 1.0F, 1.0F);

        if (r != null) {
            r.render(this, tm, partial);
        } else {
            // fallback legacy path
            this.bindTexture(tm);
            if (this.modelName != null && this.modelName.endsWith(".md3")) {
                MD3Model m = MD3Cache.getModel(this.modelName);
                if (m != null)
                    MD3Renderer.render(m, this.textureName, tm, scale);
            } else if (this.modelName != null) {
                this.renderModel(tm, anim, partial, runAmt, yaw, pitch, scale);
            }
        }

        // === DEBUG hitboxes (unchanged) ===
        if (level != null && level.minecraft != null && level.minecraft.debugHitboxes) {
            AABB b = this.bb;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glLineWidth(2.0F);
            GL11.glColor3f(0.0F, 1.0F, 0.0F);
            GL11.glBegin(GL11.GL_LINES);
            // ... (your hitbox drawing code unchanged)
            GL11.glEnd();
            GL11.glPopAttrib();
        }

        // === Hurt/dying overlay (with null-safe modelName) ===
        float hurtLeft = (float) this.hurtTime - partial;
        boolean hurtNow = (hurtLeft > 0.0F && this.hurtDuration > 0);
        boolean dying = (this.health <= 0);

        if (hurtNow || dying) {
            float s;
            if (hurtNow) {
                float t = hurtLeft / (float) this.hurtDuration;
                s = t * t;
            } else {
                float t = 1.0f - Math.min(1.0f, ((float) this.deathTime + partial) / 10.0f);
                s = 0.7f * t;
            }

            boolean alphaWas = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            if (alphaWas)
                GL11.glDisable(GL11.GL_ALPHA_TEST);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0f, 0.2f, 0.2f, Math.min(0.75f, 0.55f * s));

            if (this.modelName != null && this.modelName.endsWith(".md3")) {
                MD3Model m = MD3Cache.getModel(this.modelName);
                if (m != null)
                    MD3Renderer.render(m, this.textureName, tm, scale);
            } else if (this.modelName != null) {
                this.renderModel(tm, anim, partial, runAmt, yaw, pitch, scale);
            }

            try {
                int overlayTex = tm.load("/misc/hurt_overlay.png");
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, overlayTex);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glColor4f(0.95f, 0.25f, 0.15f, 0.50f * s);

                if (this.modelName != null && this.modelName.endsWith(".md3")) {
                    MD3Model m = MD3Cache.getModel(this.modelName);
                    if (m != null)
                        MD3Renderer.render(m, this.textureName, tm, scale);
                } else if (this.modelName != null) {
                    this.renderModel(tm, anim, partial, runAmt, yaw, pitch, scale);
                }
            } catch (Throwable ignore) {
            }

            GL11.glDisable(GL11.GL_BLEND);
            if (alphaWas)
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        if (this.allowAlpha)
            GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    public void renderModel(TextureManager tm, float anim, float partial, float runAmt, float yaw, float pitch,
            float scale) {
// MD3 path unchanged
        if (modelName != null && modelName.endsWith(".md3")) {
            MD3Model md3 = MD3Cache.getModel(modelName);
            if (md3 != null)
                MD3Renderer.render(md3, textureName, tm, scale);
            return;
        }

        // special: sync chicken flapping state

// Classic model path
        if (modelCache == null || this.modelName == null)
            return;
        Model m = modelCache.getModel(this.modelName);
        if (m == null)
            return;
        if (this instanceof net.classicremastered.minecraft.mob.Chicken
                && m instanceof net.classicremastered.minecraft.model.ChickenModel) {
            ((net.classicremastered.minecraft.model.ChickenModel) m)
                    .syncFromEntity((net.classicremastered.minecraft.mob.Chicken) this);
        }
// Let models (e.g., ZombieModel) read per-entity flags (builder poses, etc.)
        m.preAnimate(this);

// Drive animation BEFORE render (walk cycles, arms, head)
        float ageInTicks = (float) this.tickCount + partial;
        m.setRotationAngles(anim, runAmt, ageInTicks, yaw, pitch, scale);

// Draw
        m.render(anim, runAmt, ageInTicks, yaw, pitch, scale);
    }

    public void heal(int amt) {
        if (this.health > 0) {
            this.health += amt;
            if (this.health > 20)
                this.health = 20;
            this.invulnerableTime = this.invulnerableDuration / 2;
        }
    }

    /** DAMAGE ALWAYS ALLOWED (Creative and Survival). */
    /** DAMAGE ALLOWED in both Survival and Creative. */
    @Override
    public void hurt(Entity src, int dmg) {
        if (this.health <= 0)
            return;

        this.ai.hurt(src, dmg);

        if ((float) this.invulnerableTime > (float) this.invulnerableDuration / 2.0F) {
            if (this.lastHealth - dmg >= this.health)
                return;
            this.health = this.lastHealth - dmg;
        } else {
            this.lastHealth = this.health;
            this.invulnerableTime = this.invulnerableDuration;
            this.health -= dmg;
            this.hurtTime = this.hurtDuration = 10;
        }

        // ✅ Only play hurt sound if still alive after damage
        if (this.health > 0 && this.soundHurt != null && this.level != null) {
            float pitch = randPitch(hurtPitchMin, hurtPitchMax);
            this.level.playSound(this.soundHurt, this, 1.0F, pitch);
        }

        this.hurtDir = 0.0F;
        if (src != null) {
            float dx = src.x - this.x;
            float dz = src.z - this.z;
            this.hurtDir = (float) (Math.atan2((double) dz, (double) dx) * 180.0D / Math.PI) - this.yRot;
            this.knockback(src, dmg, dx, dz);
        } else {
            this.hurtDir = (float) ((int) (Math.random() * 2.0D) * 180);
        }

        // --- Retaliation against corrupted mobs ---
        if (src instanceof Mob) {
            Mob attacker = (Mob) src;
            if (attacker.corruptedToDefendPlayer) {
                // Only hostile mobs retaliate
                if (this instanceof net.classicremastered.minecraft.mob.Zombie
                        || this instanceof net.classicremastered.minecraft.mob.Skeleton
                        || this instanceof net.classicremastered.minecraft.mob.Spider
                        || this instanceof net.classicremastered.minecraft.mob.Enderman) {

                    if (this.ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI) {
                        ((net.classicremastered.minecraft.mob.ai.BasicAI) this.ai).attackTarget = attacker;
                    }
                }
            }
        }

        if (this.health <= 0) {
            this.die(src);
        }
    }

    // --- AI movement state ---
    public boolean running = false; // used by AIs to indicate chase/flee mode

    protected int coinDrop = 0; // default: no coins

    public void die(Entity killer) {
        if (this.deathScore > 0 && killer != null) {
            killer.awardKillScore(this, this.deathScore);
        }

        // --- drop parody coins if killed by a player ---
        if (killer instanceof net.classicremastered.minecraft.player.Player) {
            net.classicremastered.minecraft.player.Player p = (net.classicremastered.minecraft.player.Player) killer;
            if (coinDrop > 0) {
                p.coins += coinDrop;
                if (p.minecraft != null && p.minecraft.hud != null) {
                    p.minecraft.hud.addChat("&e+" + coinDrop + " coins");
                }
            }
        }

        // [rest of your existing death physics…]
        if (this.rider != null) {
            this.rider.unmount();
            this.rider = null;
        }
        if (this.riding != null) {
            this.unmount();
        }
        if (this.soundDeath != null && this.level != null) {
            float pitch = randPitch(deathPitchMin, deathPitchMax);
            this.level.playSound(this.soundDeath, this, 1.0F, pitch);
        }
        this.dead = true;
        this.attackTime = 0;
        this.invulnerableTime = 0;
        this.xd *= 0.2F;
        if (this.yd > 0.0F)
            this.yd = 0.0F;
        this.yd -= 0.10F;
        this.zd *= 0.2F;
    }

    public void knockback(Entity src, int amt, float dx, float dz) {
        float mag = MathHelper.sqrt(dx * dx + dz * dz);
        float power = 0.4F;
        this.xd /= 2.0F;
        this.yd /= 2.0F;
        this.zd /= 2.0F;
        this.xd -= dx / mag * power;
        this.yd += 0.4F;
        this.zd -= dz / mag * power;
        if (this.yd > 0.4F)
            this.yd = 0.4F;
    }

    /** Fall damage applies in all modes for consistency. */
    @Override
    protected void causeFallDamage(float dist) {
        int dmg = (int) Math.ceil((double) (dist - 3.0F));
        if (dmg > 0) {
            // Check if this mob was thrown by a player
            net.classicremastered.minecraft.player.Player thrower = net.classicremastered.minecraft.level.itemstack.TelekinesisItem.thrownBy
                    .get(this);

            if (thrower != null) {
                this.hurt(thrower, dmg); // credit the thrower
            } else {
                this.hurt((Entity) null, dmg); // environment damage
            }
        }
    }

    public void travel(float yya, float xxa) {
        float y1;

        // Defaults that mimic your current behavior
        float walkSpeed = 0.10f; // ground, onGround
        float airSpeed = 0.02f; // in air (not onGround)
        float swimSpeed = 0.02f; // water
        float lavaSpeed = 0.02f; // lava
        float runMul = 1.40f; // when AI is "running"
        float scale = 1.00f; // global per-mob multiplier

        // If the AI exposes knobs, use them
        if (ai instanceof BasicAI) {
            BasicAI a = (BasicAI) ai;
            // if you added these fields to BasicAI (recommended)
            if (a.walkSpeed > 0)
                walkSpeed = a.walkSpeed;
            if (a.airSpeed > 0)
                airSpeed = a.airSpeed;
            if (a.swimSpeed > 0)
                swimSpeed = a.swimSpeed;
            if (a.lavaSpeed > 0)
                lavaSpeed = a.lavaSpeed;
            if (a.runMultiplier > 0)
                runMul = a.runMultiplier;
            if (a.speedScale > 0)
                scale = a.speedScale;

            // apply run multiplier only when AI says it's chasing
            scale *= (a.running ? runMul : 1.0f);
        }

        if (isInWater()) {
            y1 = y;
            moveRelative(yya, xxa, swimSpeed * scale);
            move(xd, yd, zd);
            xd *= 0.80f;
            yd *= 0.80f;
            zd *= 0.80f;
            yd -= 0.02f;
            if (horizontalCollision && isFree(xd, yd + 0.6f - y + y1, zd))
                yd = 0.30f;

        } else if (isInLava()) {
            y1 = y;
            moveRelative(yya, xxa, lavaSpeed * scale);
            move(xd, yd, zd);
            xd *= 0.50f;
            yd *= 0.50f;
            zd *= 0.50f;
            yd -= 0.02f;
            if (horizontalCollision && isFree(xd, yd + 0.6f - y + y1, zd))
                yd = 0.30f;

        } else {
            // ground vs air
            float base = onGround ? walkSpeed : airSpeed;
            moveRelative(yya, xxa, base * scale);
            move(xd, yd, zd);
            xd *= 0.91f;
            yd *= 0.98f;
            zd *= 0.91f;
            yd -= 0.08f;
            if (onGround) {
                float f = 0.60f;
                xd *= f;
                zd *= f;
            }
        }
    }

    public boolean isShootable() {
        return true;
    }

    /** Optional helper used by some pickers to widen selection slightly. */
    public float getPickRadius() {
        return this.footSize > 0 ? this.footSize : 0.5F;
    }
}
