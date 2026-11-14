package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.ai.AI;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.model.MD3Cache;
import net.classicremastered.minecraft.model.MD3Model;
import net.classicremastered.minecraft.model.MD3Renderer;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.model.ModelManager;
import net.classicremastered.minecraft.model.SkeletonModel;
import net.classicremastered.minecraft.player.DeathMessages;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.render.entity.BabyZombieRenderer;
import net.classicremastered.minecraft.render.entity.MobRenderer;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

public abstract class Mob extends Entity {
    private transient Entity forcedFallAttacker;

    public transient net.classicremastered.minecraft.level.itemstack.MobBattle.Fight battle;
    public boolean persistent;
    public String customName = "";
    public int despawnIdleTicks;

    public boolean isPersistenceRequired() {
        return persistent
                || customName != null && !customName.isEmpty()
                || rider != null
                || riding != null
                || this instanceof net.classicremastered.minecraft.mob.StickFigure
                || this instanceof net.classicremastered.minecraft.mob.BlueForestGolem;
    }

    public void savePersistence(net.classicremastered.nbt.CompoundTag tag) {
        tag.putBoolean("Persistent", persistent);
        tag.putBoolean("TurretDefender", corruptedToDefendPlayer);
        tag.putString("CustomName", customName == null ? "" : customName);
        tag.putInt("DespawnIdleTicks", despawnIdleTicks);
    }

    public float movementClearanceHeight() {
        return rider == null ? bbHeight : ridingHeight(rider);
    }

    public float movementClearanceWidth() {
        return rider == null ? bbWidth : ridingWidth(rider);
    }

    public void loadPersistence(net.classicremastered.nbt.CompoundTag tag) {
        persistent = tag.getBoolean("Persistent");
        if (tag.getBoolean("TurretDefender")) DefenderAI.restore(this);
        customName = tag.hasKey("CustomName") ? tag.getString("CustomName") : "";
        despawnIdleTicks = Math.max(0, tag.getInt("DespawnIdleTicks"));
    }

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
    private transient float playerStrideSpeed;

    private transient net.classicremastered.minecraft.mob.ai.movement.MobLookControl lookControl;

    public float renderBodyYaw(float partial) {
        return yBodyRotO + (yBodyRot - yBodyRotO) * partial;
    }

    public float getEyeHeight(float partial) {
        return this instanceof net.classicremastered.minecraft.player.Player player
                ? player.getRenderHeightOffset(partial)
                : (bb.y1 - bb.y0) * .85f;
    }

    public float renderHeadYaw(float partial) {
        if (lookControl != null) return lookControl.yaw(this, partial);
        return yRotO + (yRot - yRotO) * partial - (yBodyRotO + (yBodyRot - yBodyRotO) * partial);
    }

    public float renderHeadPitch(float partial) {
        return lookControl == null ? xRotO + (xRot - xRotO) * partial : lookControl.pitch(partial);
    }

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

    public int[] equipment = new int[5];
    public int[] armorWear;
    public boolean equipmentRolled;
    public boolean legacyArmor;
    public int armorRemainder;
    private transient boolean explosionDamage;

    public void hurtExplosion(Entity source, int damage) {
        explosionDamage = true;
        try {
            hurt(source, damage, "explosion");
        } finally {
            explosionDamage = false;
        }
    }

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

    /** Resize the live collision box immediately while retaining its feet position. */
    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        if (bb != null) {
            float half = width * .5f;
            bb.x0 = x - half;
            bb.x1 = x + half;
            bb.z0 = z - half;
            bb.z1 = z + half;
            bb.y1 = bb.y0 + height;
        }
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Older saves can contain a stale box left over from the superclass
        // constructor.
        setSize(bbWidth, bbHeight);
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

    /** Keep entities pickable so the ray can select them in Creative and Survival. */
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

    private transient float previousFeetY;
    private transient boolean hasPreviousFeetY;

    @Override
    public void setPos(float x, float y, float z) {
        super.setPos(x, y, z);
        hasPreviousFeetY = false;
        forcedFallAttacker = null;
    }

    public void markForcedFall(Entity attacker) {
        forcedFallAttacker =
                level != null && attacker != this && attacker != null && attacker.level == level
                        ? attacker
                        : null;
    }

    @Override
    public void move(float dx, float dy, float dz) {
        super.move(dx, dy, dz);
        // A horizontal move can leave the supporting edge after vertical collision resolves.
        if (forcedFallAttacker != null
                && onGround
                && !level.getCubes(bb.cloneMove(0, -.001f, 0)).isEmpty()) {
            forcedFallAttacker = null;
        }
    }

    @Override
    public void resetInterpolation() {
        super.resetInterpolation();
        previousFeetY = bb.y0;
        hasPreviousFeetY = true;
    }

    public float renderFeetY(float partial) {
        if (!hasPreviousFeetY) return bb.y0;
        return previousFeetY + (bb.y0 - previousFeetY) * partial;
    }

    @Override
    public void tick() {
        previousFeetY = bb.y0;
        hasPreviousFeetY = true;
        super.tick();
        net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.beforeMobTick(this);

        if (this.tickCount < Integer.MAX_VALUE) this.tickCount++;
        // play idle sound every few seconds
        if (this.health > 0 && !this.dead && !this.removed && this.soundIdle != null) {
            if (--idleSoundDelay <= 0) {
                if (this.level == null
                        || this.level.minecraft == null
                        || this.level.minecraft.settings == null
                        || this.level.minecraft.settings.allowMobSounds) {
                    float pitch = randPitch(idlePitchMin, idlePitchMax);
                    this.level.playSound(this.soundIdle, this, 1.0F, pitch);
                }
                idleSoundDelay = 80; // 80 ticks ≈ 4 seconds at 20 TPS
            }
        }
        this.oTilt = this.tilt;
        if (this.attackTime > 0) --this.attackTime;
        if (this.hurtTime > 0) --this.hurtTime;
        if (this.invulnerableTime > 0) --this.invulnerableTime;

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
                if (this.ai != null) this.ai.beforeRemove();
                this.remove();
            }
            // Skip normal AI/animation driving while dying
            return;
        }

        // ===== Normal living logic =====

        // fluids / damage
        if (!(this instanceof StickFigure || this instanceof Skeleton) && this.isUnderWater()) {
            if (this.airSupply > 0) {
                --this.airSupply;
            }
            if (this.airSupply == 0 && (this.tickCount % 20) == 0) {
                if (!(this instanceof net.classicremastered.minecraft.player.Player
                        && net.classicremastered.minecraft.player.Player.creativeInvulnerable)) {
                    this.hurt(null, 2, "drown");
                }
            }
        } else {
            if (this.airSupply < TOTAL_AIR_SUPPLY) {
                this.airSupply = Math.min(TOTAL_AIR_SUPPLY, this.airSupply + 4);
            }
        }
        if (this.isInWater()) {
            this.fallDistance = 0.0F;
            forcedFallAttacker = null;
        }
        if (this.isInLava()) this.hurt(null, 10, "lava");
        if (onGround
                && riding == null
                && !(this instanceof Player player && player.isSneaking())
                && level.getTile(
                                MathHelper.floor(x),
                                MathHelper.floor(bb.y0 - .01f),
                                MathHelper.floor(z))
                        == Block.MAGMA.id) {
            this.hurt(null, 1, "hot floor");
        }

        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;

        // Always run physics
        if (riding != null) {
            net.classicremastered.minecraft.mob.ai.MobRiding.tickRider(this);
        } else if (!net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.moveHeld(this)
                && !net.classicremastered.minecraft.level.itemstack.GravityGunItem.moveHeld(this)
                && !net.classicremastered.minecraft.mob.ai.MobRiding.driveMount(this)) {
            this.aiStep();
            net.classicremastered.minecraft.mob.ai.MobRiding.findJockeyMount(this);
        }

        if (remainingFireTicks > 0 && isWetForFire()) extinguishFire();

        // Walk/run animation bookkeeping
        float dx = this.x - this.xo, dz = this.z - this.zo;
        float dist = net.classicremastered.util.MathHelper.sqrt(dx * dx + dz * dz);
        float bodyYaw = this.yBodyRot, walk = 0.0F;
        this.oRun = this.run;
        float moving = 0.0F;
        if (dist > 0.05F) {
            moving = 1.0F;
            walk = dist * 3.0F;
            bodyYaw =
                    (float) (Math.atan2((double) dz, (double) dx) * 180.0D / (float) Math.PI)
                            - 90.0F;
        }
        if (!this.onGround) moving = 0.0F;
        this.run += (moving - this.run) * 0.3F;

        boolean flip;
        if (this instanceof net.classicremastered.minecraft.player.Player player
                && player.input != null
                && riding == null) {
            flip = player.input.yya < 0f;
            float strafe = player.movementStrafe() * (flip ? -1f : 1f);
            float turn = (float) Math.toDegrees(Math.atan2(-strafe, Math.abs(player.input.yya)));
            float targetYaw = this.yRot + Math.max(-45f, Math.min(45f, turn));
            float delta = (float) Math.IEEEremainder(targetYaw - this.yBodyRot, 360f);
            this.yBodyRot += delta * 0.3f;
            float headDiff = (float) Math.IEEEremainder(this.yRot - this.yBodyRot, 360f);
            this.yBodyRot += headDiff - Math.max(-50f, Math.min(50f, headDiff));
        } else {
            float delta = (float) Math.IEEEremainder(bodyYaw - this.yBodyRot, 360f);
            this.yBodyRot += delta * 0.1f;
            float headDiff = (float) Math.IEEEremainder(this.yRot - this.yBodyRot, 360f);
            flip = headDiff < -90f || headDiff >= 90f;
            float turnLimit =
                    this instanceof net.classicremastered.minecraft.player.Player ? 45f : 75f;
            headDiff = Math.max(-turnLimit, Math.min(turnLimit, headDiff));
            this.yBodyRot = this.yRot - headDiff * 0.9f;
        }
        if (rider instanceof net.classicremastered.minecraft.player.Player)
            this.yBodyRot = this.yRot;
        if (flip) walk = -walk;
        if (this instanceof net.classicremastered.minecraft.player.Player && riding == null) {
            playerStrideSpeed += (walk - playerStrideSpeed) * 0.4F;
            walk = playerStrideSpeed;
        } else {
            playerStrideSpeed = 0.0F;
        }

        while (this.yRot - this.yRotO < -180.0F) this.yRotO -= 360.0F;
        while (this.yRot - this.yRotO >= 180.0F) this.yRotO += 360.0F;
        while (this.yBodyRot - this.yBodyRotO < -180.0F) this.yBodyRotO -= 360.0F;
        while (this.yBodyRot - this.yBodyRotO >= 180.0F) this.yBodyRotO += 360.0F;
        while (this.xRot - this.xRotO < -180.0F) this.xRotO += 360.0F;
        while (this.xRot - this.xRotO >= 180.0F) this.xRotO -= 360.0F;
        this.animStep += walk;
        if (net.classicremastered.minecraft.mob.ai.movement.MobLookControl.supports(this)) {
            if (lookControl == null)
                lookControl = new net.classicremastered.minecraft.mob.ai.movement.MobLookControl();
            lookControl.tick(this);
        }

        if (!this.horizontalCollision) {
            this.ySlideOffset *= 0.6F;
        }
    }

    public void aiStep() {
        net.classicremastered.minecraft.level.itemstack.MobBattle.tick(this);
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
        net.classicremastered.minecraft.render.entity.MobRenderer<Mob> r =
                net.classicremastered.minecraft.render.entity.RenderManager.getRenderer(this);
        if (r == null && this.modelName == null) return;

        // ---- orientation interpolation ----
        while (this.yBodyRotO - this.yBodyRot < -180.0F) this.yBodyRotO += 360.0F;
        while (this.yBodyRotO - this.yBodyRot >= 180.0F) this.yBodyRotO -= 360.0F;
        while (this.xRotO - this.xRot < -180.0F) this.xRotO += 360.0F;
        while (this.xRotO - this.xRot >= 180.0F) this.xRotO -= 360.0F;
        while (this.yRotO - this.yRot < -180.0F) this.yRotO += 360.0F;
        while (this.yRotO - this.yRot >= 180.0F) this.yRotO -= 360.0F;

        float bodyRot = renderBodyYaw(partial);
        float runAmt = this.oRun + (this.run - this.oRun) * partial;
        float yaw = renderHeadYaw(partial);
        float pitch = renderHeadPitch(partial);
        float modelYawOffset = rotOffs;
        if (this instanceof net.classicremastered.minecraft.player.Player) {
            modelYawOffset = 0f;
            if (riding instanceof Mob mount) {
                bodyRot =
                        mount.yBodyRotO
                                + (float) Math.IEEEremainder(mount.yBodyRot - mount.yBodyRotO, 360f)
                                        * partial
                                - mount.rotOffs;
            } else if (riding instanceof net.classicremastered.minecraft.entity.BoatEntity boat) {
                bodyRot = boat.renderYaw(partial);
            }
            float look =
                    (float) Math.IEEEremainder(yRotO + (yRot - yRotO) * partial - bodyRot, 360f);
            yaw = Math.max(-75f, Math.min(75f, look));
        } else if (this instanceof Zombie
                && isBaby()
                && !(this instanceof Skeleton)
                && riding instanceof Mob mount) {
            float look = bodyRot + yaw;
            bodyRot = mount.renderBodyYaw(partial) - mount.rotOffs;
            yaw = (float) Math.IEEEremainder(look - bodyRot, 360);
            yaw = Math.max(-75f, Math.min(75f, yaw));
        }

        GL11.glPushMatrix();
        float anim = this.animStepO + (this.animStep - this.animStepO) * partial;

        // ---- base brightness color ----
        float baseBright = this.getRenderBrightness(partial);
        GL11.glColor3f(baseBright, baseBright, baseBright);

        float scale = 0.0625F;

        // ---- model ground lift ----
        float groundLift = 23.0F;
        boolean groundSkeletonStride = false;
        if (this.modelName != null && this.modelName.endsWith(".md3")) {
            groundLift = this.modelGroundOffset;
        } else if (modelCache != null && this.modelName != null) {
            Model mm = modelCache.getModel(this.modelName);
            if (mm != null) groundLift = mm.groundOffset;
            if (mm instanceof SkeletonModel skeletonModel && riding == null && health > 0) {
                groundSkeletonStride = true;
                if (onGround)
                    groundLift -= skeletonModel.groundedStrideOffset(anim, runAmt, isBaby());
            }
        }

        float walkBob =
                groundSkeletonStride
                        ? 0f
                        : Math.abs(MathHelper.cos(anim * .6662F))
                                * 5.0F
                                * runAmt
                                * this.bobStrength;
        float bob = -walkBob - groundLift;

        // ---- move to entity ----
        float renderHeightOffset =
                riding == null
                                && this
                                        instanceof
                                        net.classicremastered.minecraft.player.Player player
                        ? player.getRenderHeightOffset(partial)
                        : this.heightOffset;
        float anchorY =
                this instanceof net.classicremastered.minecraft.player.Player || riding != null
                        ? this.yo + (this.y - this.yo) * partial - renderHeightOffset
                        : renderFeetY(partial);
        if (riding instanceof net.classicremastered.minecraft.entity.BoatEntity
                && (this instanceof HumanoidMob
                        || this instanceof net.classicremastered.minecraft.player.Player)) {
            anchorY -= isBaby() ? .19f : .30f;
        } else if (riding != null
                && !(riding instanceof Cow)
                && !(this instanceof Skeleton && riding instanceof Spider)
                && (this instanceof Zombie
                        || this instanceof net.classicremastered.minecraft.player.Player)) {
            anchorY -= isBaby() ? .43f : .62f;
        }
        GL11.glTranslatef(
                this.xo + (this.x - this.xo) * partial,
                anchorY + this.renderOffset,
                this.zo + (this.z - this.zo) * partial);

        boolean showFireOverlay = remainingFireTicks > 0 && !isWetForFire();
        if (!showFireOverlay && this instanceof Zombie) {
            Zombie z = (Zombie) this;
            if (z.canBurnInSunlight()
                    && z.level != null
                    && z.level.shouldUndeadBurnAt(z)
                    && !z.isInWaterOrOnWater()) {
                showFireOverlay = true;
            }
        }

        if (showFireOverlay) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
            try {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glBindTexture(
                        GL11.GL_TEXTURE_2D,
                        tm.load(net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE));
                float width = Math.max(.65f, this.bbWidth * 1.35f);
                float layerHeight = Math.min(1f, this.bbHeight);
                ShapeRenderer flames = ShapeRenderer.instance;
                flames.begin();
                flames.color(1, 1, 1);
                for (float layer = 0; layer < this.bbHeight * .8f; layer += .55f) {
                    net.classicremastered.minecraft.level.tile.FireBlock.renderFlames(
                            flames, -width * .5f, layer, -width * .5f, width, layerHeight);
                    width *= .85f;
                }
                flames.end();
            } finally {
                GL11.glPopAttrib();
            }
        }

        // ---- hurt/death wobble ----
        float ht = (float) this.hurtTime - partial;
        if (ht > 0.0F || this.health <= 0) {
            if (ht < 0.0F) ht = 0.0F;
            else
                ht =
                        MathHelper.sin(
                                        (ht /= (float) this.hurtDuration)
                                                * ht
                                                * ht
                                                * ht
                                                * (float) Math.PI)
                                * 14.0F;

            if (this.health <= 0) {
                float deathF = ((float) this.deathTime + partial) / 20.0F;
                ht += deathF * deathF * 800.0F;
                if (ht > 90.0F) ht = 90.0F;
            }

            float dir = this.hurtDir;
            GL11.glRotatef(180.0F - bodyRot + modelYawOffset, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-dir, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-ht, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(dir, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-(180.0F - bodyRot + modelYawOffset), 0.0F, 1.0F, 0.0F);
        }

        GL11.glTranslatef(0.0F, -bob * scale, 0.0F);
        GL11.glScalef(1.0F, -1.0F, 1.0F);
        GL11.glRotatef(180.0F - bodyRot + modelYawOffset, 0.0F, 1.0F, 0.0F);

        // Alpha/Cull state
        if (!this.allowAlpha) GL11.glDisable(GL11.GL_ALPHA_TEST);
        else GL11.glDisable(GL11.GL_CULL_FACE);

        // ==== Delegated render ====
        GL11.glScalef(-1.0F, 1.0F, 1.0F);

        if (this instanceof BabyZombie baby
                && (MobRenderer<?>) r instanceof BabyZombieRenderer babyRenderer) {
            // Keep the visible head and the hurt passes on the same pose while tracking a
            // target.
            babyRenderer.render(baby, tm, partial, yaw, pitch);
        } else if (r != null) {
            r.render(this, tm, partial);
        } else {
            // fallback legacy path
            this.bindTexture(tm);
            if (this.modelName != null && this.modelName.endsWith(".md3")) {
                MD3Model m = MD3Cache.getModel(this.modelName);
                if (m != null) MD3Renderer.render(m, this.textureName, tm, scale);
            } else if (this.modelName != null) {
                var humanoid =
                        this instanceof net.classicremastered.minecraft.player.Player
                                        && modelCache != null
                                        && modelCache.getModel(modelName)
                                                instanceof
                                                net.classicremastered.minecraft.model.HumanoidModel
                                                                model
                                ? model
                                : null;
                if (humanoid != null) humanoid.setAttachmentTextures(tm);
                try {
                    this.renderModel(tm, anim, partial, runAmt, yaw, pitch, scale);
                } finally {
                    if (humanoid != null) humanoid.setAttachmentTextures(null);
                }
                if (modelCache != null
                        && modelCache.getModel(this.modelName)
                                instanceof
                                net.classicremastered.minecraft.model.StickFigureModel
                                                stickFigure) {
                    stickFigure.renderHeldBlock(tm, scale, baseBright);
                }
            }
        }

        if (modelCache != null
                && modelName != null
                && modelCache.getModel(modelName)
                        instanceof net.classicremastered.minecraft.model.HumanoidModel h) {
            net.classicremastered.minecraft.render.EquipmentRenderer.armor(
                    this, h, tm, scale, baseBright);
            if (this instanceof net.classicremastered.minecraft.player.Player player) {
                net.classicremastered.minecraft.render.HeldItemRenderer.renderInHand(
                        player, h, tm, scale, baseBright);
            } else {
                net.classicremastered.minecraft.render.EquipmentRenderer.held(
                        this, h, tm, scale, baseBright);
            }
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
            if (alphaWas) GL11.glDisable(GL11.GL_ALPHA_TEST);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0f, 0.2f, 0.2f, Math.min(0.75f, 0.55f * s));

            if (this instanceof Skeleton || this instanceof Zombie && isBaby()) {
                this.bindTexture(tm);
            }
            if (this.modelName != null && this.modelName.endsWith(".md3")) {
                MD3Model m = MD3Cache.getModel(this.modelName);
                if (m != null) MD3Renderer.render(m, this.textureName, tm, scale);
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
                    if (m != null) MD3Renderer.render(m, this.textureName, tm, scale);
                } else if (this.modelName != null) {
                    this.renderModel(tm, anim, partial, runAmt, yaw, pitch, scale);
                }
            } catch (Throwable ignore) {
            }

            GL11.glDisable(GL11.GL_BLEND);
            if (alphaWas) GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        if (this.allowAlpha) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    public void renderModel(
            TextureManager tm,
            float anim,
            float partial,
            float runAmt,
            float yaw,
            float pitch,
            float scale) {
        // MD3 path unchanged
        if (modelName != null && modelName.endsWith(".md3")) {
            MD3Model md3 = MD3Cache.getModel(modelName);
            if (md3 != null) MD3Renderer.render(md3, textureName, tm, scale);
            return;
        }

        // special: sync chicken flapping state

        // Classic model path
        if (modelCache == null || this.modelName == null) return;
        Model m = modelCache.getModel(this.modelName);
        if (m == null) return;
        if (m instanceof net.classicremastered.minecraft.model.SkeletonModel skeletonModel) {
            skeletonModel.setRenderTextures(tm);
            skeletonModel.setRenderPartial(partial);
        }
        if (m instanceof net.classicremastered.minecraft.model.SkeletonTrapModel trapModel)
            trapModel.setRenderContext(tm, partial);
        if (this instanceof net.classicremastered.minecraft.mob.Chicken
                && m instanceof net.classicremastered.minecraft.model.ChickenModel) {
            ((net.classicremastered.minecraft.model.ChickenModel) m)
                    .syncFromEntity((net.classicremastered.minecraft.mob.Chicken) this);
        }
        // Let models (e.g., ZombieModel) read per-entity flags (builder poses, etc.)
        m.preAnimate(this);
        if (m instanceof net.classicremastered.minecraft.model.HumanoidModel humanoid) {
            humanoid.setSneakProgress(
                    this instanceof net.classicremastered.minecraft.player.Player player
                            ? player.getSneakProgress(partial)
                            : 0);
        }

        // Drive animation BEFORE render (walk cycles, arms, head)
        float ageInTicks = (float) this.tickCount + partial;
        m.setRotationAngles(anim, runAmt, ageInTicks, yaw, pitch, scale);

        // Draw
        if (this instanceof net.classicremastered.minecraft.player.Player player
                && m instanceof net.classicremastered.minecraft.model.HumanoidModel humanoid
                && net.classicremastered.minecraft.model.GunModel.isGun(
                        player.inventory.getSelected() - 256)) {
            humanoid.renderHoldingGun(anim, runAmt, ageInTicks, yaw, pitch, scale);
        } else if (this instanceof net.classicremastered.minecraft.player.Player player
                && m instanceof net.classicremastered.minecraft.model.HumanoidModel humanoid
                && player.inventory.getSelected() == 262) {
            var bow =
                    (net.classicremastered.minecraft.level.itemstack.BowItem)
                            net.classicremastered.minecraft.level.itemstack.Item.BOW;
            humanoid.renderHoldingBow(yaw, pitch, scale, bow.isDrawing(player));
        } else if (this instanceof net.classicremastered.minecraft.player.Player player
                && m instanceof net.classicremastered.minecraft.model.HumanoidModel humanoid
                && player.inventory.getSelected() > 0) {
            humanoid.renderHoldingItem(
                    scale,
                    player.developerPowers()
                            .swordPose(player, player.inventory.getSelected(), partial));
        } else {
            m.render(anim, runAmt, ageInTicks, yaw, pitch, scale);
        }
    }

    public void heal(int amt) {
        if (this.health > 0) {
            this.health += amt;
            if (this.health > 20) this.health = 20;
            this.invulnerableTime = this.invulnerableDuration / 2;
        }
    }

    /** DAMAGE ALWAYS ALLOWED (Creative and Survival). */
    /** DAMAGE ALLOWED in both Survival and Creative. */
    @Override
    public void hurt(Entity src, int dmg) {
        if (dmg <= 0) return;
        Entity responsible =
                src instanceof net.classicremastered.minecraft.entity.Arrow arrow
                        ? arrow.getOwner()
                        : src;
        if (responsible instanceof Mob ally
                && (ally.corruptedToDefendPlayer || ally instanceof Defender)
                && (this instanceof net.classicremastered.minecraft.player.Player
                        || this instanceof CrossbowTurret
                        || this instanceof Defender
                        || corruptedToDefendPlayer
                        || (ally instanceof Defender
                                ? !Defender.isEnemy(this)
                                : !MobRegistry.isTurretHostile(this)))) return;
        if (this.health <= 0 || this.dead || this.removed) return;

        boolean freshHit =
                (src instanceof net.classicremastered.minecraft.entity.Arrow pellet
                                && pellet.getOwner() instanceof CrossbowTurret turret
                                && turret.variant.shotgun())
                        || this.invulnerableTime <= this.invulnerableDuration / 2f
                        || src instanceof net.classicremastered.minecraft.player.Player player
                                && player.developerPowers().isRapidPunch();
        int armorDamage = dmg;
        if (src != null || explosionDamage) {
            net.classicremastered.minecraft.Entity attacker =
                    src instanceof net.classicremastered.minecraft.entity.Arrow a
                            ? a.getOwner()
                            : src;
            if (attacker instanceof Mob
                    && !(attacker instanceof net.classicremastered.minecraft.player.Player)
                    && !(attacker instanceof Ironmock)) {
                if (this instanceof net.classicremastered.minecraft.player.Player
                        && net.classicremastered.minecraft.CombatRules.difficulty(level)
                                == net.classicremastered.minecraft.Difficulty.PEACEFUL) return;
                if (attacker instanceof Zombie
                        || attacker instanceof Skeleton
                        || attacker instanceof Spider)
                    dmg =
                            net.classicremastered.minecraft.CombatRules.undeadDamage(
                                    level,
                                    dmg,
                                    (Mob) attacker,
                                    src instanceof net.classicremastered.minecraft.entity.Arrow);
            }
            armorDamage = dmg;
            if (freshHit) {
                dmg = Equipment.reduce(this, dmg);
                if (dmg <= 0) {
                    Equipment.damageArmor(this, armorDamage);
                    return;
                }
            } else {
                dmg = dmg * (100 - 4 * Equipment.points(this)) / 100;
            }
        }
        if (ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI basic)
            basic.bind(level, this);
        if (this.ai != null) {
            this.ai.hurt(src, dmg);
        }

        if (!freshHit) {
            if (this.lastHealth - dmg >= this.health) return;
            this.health = this.lastHealth - dmg;
        } else {
            this.lastHealth = this.health;
            this.invulnerableTime = this.invulnerableDuration;
            this.health -= dmg;
            this.hurtTime = this.hurtDuration = 10;
        }

        Equipment.damageArmor(this, armorDamage);
        playHurtSound();

        this.hurtDir = 0.0F;
        if (src != null) {
            float dx = src.x - this.x;
            float dz = src.z - this.z;
            this.hurtDir =
                    (float) (Math.atan2((double) dz, (double) dx) * 180.0D / Math.PI) - this.yRot;
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
                        ((net.classicremastered.minecraft.mob.ai.BasicAI) this.ai).attackTarget =
                                attacker;
                    }
                }
            }
        }

        if (this.health <= 0) {
            Entity killer = src;
            if (killer == null
                    && "fall".equals(damageCause)
                    && forcedFallAttacker != null
                    && forcedFallAttacker.level == level) {
                killer = forcedFallAttacker;
            }
            forcedFallAttacker = null;
            this.die(killer);
        }
    }

    // --- AI movement state ---
    public boolean running = false; // used by AIs to indicate chase/flee mode

    protected int coinDrop = 0; // default: no coins

    private int killScore() {
        if (deathScore > 0) return deathScore;
        if (this instanceof Skeleton) return 120;
        if (this instanceof Zombie || MobRegistry.isTurretHostile(this)) return 80;
        return 0;
    }

    private int legacyCoinBonus() {
        if (deathScore <= 0) return 0;
        if (this instanceof Enderman) return 4;
        if (this instanceof Skeleton || this instanceof Spider) return 2;
        return this instanceof Zombie ? 1 : 0;
    }

    public void die(Entity killer) {
        if (dead) return;
        net.classicremastered.minecraft.level.itemstack.MobBattle.stop(this);
        if (level != null && !level.isNetworkMode() && level.doMobLoot) {
            for (int slot = 0; slot < equipment.length; slot++) {
                int raw = equipment[slot];
                if (raw > 0
                        && level.random.nextFloat()
                                < (this instanceof Zombie && slot < 4 ? .085f : .05f)
                        && !(this instanceof net.classicremastered.minecraft.player.Player))
                    level.addEntity(
                            new net.classicremastered.minecraft.entity.DroppedItem(
                                            level, x, y, z, raw - 256)
                                    .withArmorWear(Equipment.wear(this, slot)));
            }
            if (this instanceof Enderman
                    && level.random.nextBoolean()
                    && net.classicremastered.minecraft.level.itemstack.Item.ENDER_PEARL != null) {
                level.addEntity(
                        new net.classicremastered.minecraft.entity.DroppedItem(
                                level,
                                x,
                                y,
                                z,
                                net.classicremastered.minecraft.level.itemstack.Item.ENDER_PEARL
                                        .id));
            }
            if (this instanceof Pig || this instanceof Cow) {
                net.classicremastered.minecraft.level.itemstack.ItemRegistry.bootstrap();
                var meat =
                        this instanceof Pig
                                ? net.classicremastered.minecraft.level.itemstack.Item.RAW_PORKCHOP
                                : net.classicremastered.minecraft.level.itemstack.Item.RAW_BEEF;
                level.addEntity(
                        new net.classicremastered.minecraft.entity.DroppedItem(
                                level, x, y, z, meat.id, 1 + level.random.nextInt(3)));
            }
            if (this instanceof Pig || this instanceof Spider) {
                var item =
                        this instanceof Pig
                                ? net.classicremastered.minecraft.level.itemstack.Equipment.LEATHER
                                : net.classicremastered.minecraft.level.itemstack.Equipment.STRING;
                if (item != null)
                    level.addEntity(
                            new net.classicremastered.minecraft.entity.DroppedItem(
                                    level, x, y, z, item.id, 1 + level.random.nextInt(2)));
            }
        }
        // Keep damage attribution separate so turret victims retaliate against the turret.
        Entity credited = DeathMessages.resolveAttacker(killer);
        if (credited instanceof CrossbowTurret turret) credited = turret.creditedOwner();
        int points = killScore();
        if (credited != null && points > 0) credited.awardKillScore(this, points);
        if (credited instanceof net.classicremastered.minecraft.player.Player p) {
            p.coins += legacyCoinBonus();
            if (coinDrop > 0 && (level == null || level.doMobLoot)) {
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
        String deathSound = soundDeath;
        if (deathSound == null && !(this instanceof net.classicremastered.minecraft.player.Player))
            deathSound = "damage/hit";
        if (deathSound != null && this.level != null) {
            if (this.level.minecraft == null
                    || this.level.minecraft.settings == null
                    || this.level.minecraft.settings.allowMobSounds) {
                float pitch = randPitch(deathPitchMin, deathPitchMax);
                this.level.playSound(deathSound, this, 1.0F, pitch);
            }
        }
        this.dead = true;
        this.attackTime = 0;
        this.invulnerableTime = 0;
        this.xd *= 0.2F;
        if (this.yd > 0.0F) this.yd = 0.0F;
        this.yd -= 0.10F;
        this.zd *= 0.2F;
    }

    public void knockback(Entity src, int amt, float dx, float dz) {
        float mag = MathHelper.sqrt(dx * dx + dz * dz);
        float power = 0.4F;
        this.xd /= 2.0F;
        this.yd /= 2.0F;
        this.zd /= 2.0F;
        if (mag > 0.0F && Float.isFinite(mag)) {
            this.xd -= dx / mag * power;
            this.zd -= dz / mag * power;
        }
        this.yd += 0.4F;
        if (this.yd > 0.4F) this.yd = 0.4F;
    }

    public void animateHurt(float dx, float dz) {
        if (health <= 0 || removed) return;
        hurtTime = hurtDuration = 10;
        hurtDir = (float) Math.toDegrees(Math.atan2(dz, dx)) - yRot;
    }

    public void playHurtSound() {
        if (health <= 0 || dead || removed || level == null) return;
        if (level.minecraft != null
                && level.minecraft.settings != null
                && !level.minecraft.settings.allowMobSounds) return;

        String hurtSound = soundHurt;
        if (hurtSound == null && !(this instanceof net.classicremastered.minecraft.player.Player))
            hurtSound = "damage/hit";
        if (hurtSound != null) {
            level.playSound(hurtSound, this, 1.0F, randPitch(hurtPitchMin, hurtPitchMax));
        }
    }

    /** Fall damage applies in all modes for consistency. */
    @Override
    protected void causeFallDamage(float dist) {
        int dmg = (int) Math.ceil((double) (dist - 3.01F));
        if (dmg > 0) {
            this.hurt(null, dmg, "fall");
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
            if (a.walkSpeed > 0) walkSpeed = a.walkSpeed;
            if (a.airSpeed > 0) airSpeed = a.airSpeed;
            if (a.swimSpeed > 0) swimSpeed = a.swimSpeed;
            if (a.lavaSpeed > 0) lavaSpeed = a.lavaSpeed;
            if (a.runMultiplier > 0) runMul = a.runMultiplier;
            if (a.speedScale > 0) scale = a.speedScale;

            // apply run multiplier only when AI says it's chasing
            scale *= (a.running ? runMul : 1.0f);
        }

        if (this instanceof net.classicremastered.minecraft.player.Player player)
            scale *=
                    player.developerPowers().movementMultiplier(player)
                            * player.frostbiteMovementMultiplier();

        if (isInWater()) {
            y1 = y;
            moveRelative(yya, xxa, swimSpeed * scale);
            move(xd, yd, zd);
            xd *= 0.80f;
            yd *= 0.80f;
            zd *= 0.80f;
            yd -= 0.02f;
            if (horizontalCollision && isFree(xd, yd + 0.6f - y + y1, zd)) yd = 0.30f;

        } else if (isInLava()) {
            y1 = y;
            moveRelative(yya, xxa, lavaSpeed * scale);
            move(xd, yd, zd);
            xd *= 0.50f;
            yd *= 0.50f;
            zd *= 0.50f;
            yd -= 0.02f;
            if (horizontalCollision && isFree(xd, yd + 0.6f - y + y1, zd)) yd = 0.30f;

        } else {
            boolean climbing =
                    this instanceof net.classicremastered.minecraft.player.Player
                            && net.classicremastered.minecraft.level.tile.LadderBlock.isLadder(
                                    level.getTile(
                                            (int) Math.floor(x),
                                            (int) Math.floor(bb.y0 + .05f),
                                            (int) Math.floor(z)));
            if (climbing) {
                fallDistance = 0;
                var climber = (net.classicremastered.minecraft.player.Player) this;
                if (climber.isHoldingLadder()) yd = 0;
                else if (horizontalCollision || ai instanceof BasicAI input && input.jumping)
                    yd = .22f;
                else yd = Math.max(yd, -.12f);
            }
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
