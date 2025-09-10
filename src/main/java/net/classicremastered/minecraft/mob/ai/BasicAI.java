package net.classicremastered.minecraft.mob.ai;

import java.util.List;
import java.util.Random;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ScaryMindZombie;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public class BasicAI extends AI {

    public static final long serialVersionUID = 0L;
    public Random random = new Random();
    public float xxa;
    public float yya;
    protected float yRotA;
    public Level level;
    public Mob mob;
    public boolean jumping = false;
    protected int attackDelay = 0;
    public float runSpeed = 0.7F;
    protected int noActionTime = 0;
    public Entity attackTarget = null;
    public boolean running = false;
    public boolean suppressRandomJump = false;
    public float walkSpeed    = 0.10f; // ground
    public float airSpeed     = 0.02f; // jumping/falling
    public float swimSpeed    = 0.02f;
    public float lavaSpeed    = 0.02f;
    public float runMultiplier= 1.40f; // applied when running == true
    public float speedScale   = 1.00f; // global multiplier

    @Override
    public void tick(Level level, Mob mob) {
        ++this.noActionTime;
        Entity player;
        if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && (player = level.getPlayer()) != null) {
            float dx = player.x - mob.x;
            float dy = player.y - mob.y;
            float dz = player.z - mob.z;
            if (dx * dx + dy * dy + dz * dz < 1024.0F) {
                this.noActionTime = 0;
            } else {
                mob.remove();
            }
        }
        if (CreativeModeHelper.shouldIgnoreTarget(this.attackTarget)) {
            this.attackTarget = null;
            return;
        }
        this.level = level;
        this.mob = mob;
        if (this.attackDelay > 0) {
            --this.attackDelay;
        }

        if (mob.health <= 0) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.yya = 0.0F;
            this.yRotA = 0.0F;
        } else {
            this.update();
        }

        boolean inWater = mob.isInWater();
        boolean inLava = mob.isInLava();
        if (this.jumping) {
            if (inWater) {
                mob.yd += 0.04F;
            } else if (inLava) {
                mob.yd += 0.04F;
            } else if (mob.onGround) {
                this.jumpFromGround();
            }
        }

        this.xxa *= 0.98F;
        this.yya *= 0.98F;
        this.yRotA *= 0.9F;
        mob.travel(this.xxa, this.yya);

        List list = level.findEntities(mob, mob.bb.grow(0.2F, 0.0F, 0.2F));
        if (list != null && list.size() > 0) {
            for (int i = 0; i < list.size(); ++i) {
                Entity e = (Entity) list.get(i);
                if (e.isPushable()) {
                    e.push(mob);
                }
            }
        }
    }

    protected void jumpFromGround() {
        this.mob.yd = 0.42F;
    }

    protected void update() {
        if (this.level == null || this.mob == null || this.mob.health <= 0) {
            // Hard stop if invalid
            this.jumping = false;
            this.xxa = 0.0F;
            this.yya = 0.0F;
            this.yRotA = 0.0F;
            return;
        }

        final boolean inWater = this.mob.isInWater();
        final boolean inLava  = this.mob.isInLava();

        // ===== If we have a target: chase it =====
        if (this.attackTarget != null && !this.attackTarget.removed) {
            // Face target smoothly
            float dx = this.attackTarget.x - this.mob.x;
            float dz = this.attackTarget.z - this.mob.z;
            float targetYaw = (float)(Math.atan2((double)dz, (double)dx) * 180.0D / Math.PI) - 90.0F;
            float dyaw = targetYaw - this.mob.yRot;
            while (dyaw < -180.0F) dyaw += 360.0F;
            while (dyaw >= 180.0F) dyaw -= 360.0F;
            this.yRotA = dyaw * 0.4F; // steer proportionally toward target

            // Forward movement (runSpeed base; boost if running)
            float fwd = this.runSpeed * (this.running ? 1.4F : 1.0F);
            this.yya = fwd;

            // Light, occasional strafing to avoid getting stuck
            if (this.random.nextFloat() < 0.06F) {
                this.xxa = (this.random.nextFloat() - 0.5F) * this.runSpeed * 0.5F;
            }

            // Jump logic:
            // - Never random-jump if suppressRandomJump is set (builder climb mode)
            // - Do jump if pushing a wall and on ground
            // - In water/lava, bias to jump for buoyancy unless suppressed
            boolean wantRandomJump = (!this.suppressRandomJump && this.random.nextFloat() < 0.04F);
            boolean obstacleJump   = (this.mob.onGround && this.mob.horizontalCollision);
            boolean mediumJump     = (!this.suppressRandomJump && (inWater || inLava) && this.random.nextFloat() < 0.8F);

            this.jumping = obstacleJump || mediumJump || wantRandomJump;

        } else {
            // ===== No target: wander =====

            // Occasionally pick a new wander vector
            if (this.random.nextFloat() < 0.07F) {
                // Forward between [0..runSpeed], slight strafe
                this.yya = this.random.nextFloat() * this.runSpeed;
                this.xxa = (this.random.nextFloat() - 0.5F) * this.runSpeed * 0.5F;
            } else {
                // Gentle decay toward zero
                this.yya *= 0.95F;
                this.xxa *= 0.95F;
            }

            // Occasionally adjust heading a bit
            if (this.random.nextFloat() < 0.04F) {
                // ±30° nudge
                this.yRotA = (this.random.nextFloat() - 0.5F) * 60.0F * 0.5F;
            } else {
                // decay heading change
                this.yRotA *= 0.9F;
            }

            // Jump logic in wander:
            // - obstacle jump if pushing a wall
            // - medium-driven jump (water/lava) unless suppressed
            // - small random chance unless suppressed
            boolean obstacleJump = (this.mob.onGround && this.mob.horizontalCollision);
            boolean mediumJump   = (!this.suppressRandomJump && (inWater || inLava) && this.random.nextFloat() < 0.8F);
            boolean randJump     = (!this.suppressRandomJump && this.random.nextFloat() < 0.01F);

            this.jumping = obstacleJump || mediumJump || randJump;
        }

        // Apply heading and look
        this.mob.yRot += this.yRotA;
        this.mob.xRot  = (float) this.defaultLookAngle;

        // If in fluid, bias toward upward motion when jumping (handled in tick())
        // (No direct vertical impulse here; BasicAI.tick() calls jumpFromGround()/buoyancy)

        // Clamp/soften inputs a bit
        if (this.yya >  this.runSpeed * 1.6F) this.yya =  this.runSpeed * 1.6F;
        if (this.yya < -this.runSpeed * 1.6F) this.yya = -this.runSpeed * 1.6F;
        if (this.xxa >  this.runSpeed)        this.xxa =  this.runSpeed;
        if (this.xxa < -this.runSpeed)        this.xxa = -this.runSpeed;

        // Keep noActionTime from drifting when actively moving
        if (Math.abs(this.yya) > 1e-3F || Math.abs(this.xxa) > 1e-3F || this.jumping) {
            this.noActionTime = 0;
        }
    }


    public void beforeRemove() {}

    @Override
    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        this.noActionTime = 0;
    }

    public void bind(net.classicremastered.minecraft.level.Level level, net.classicremastered.minecraft.mob.Mob mob) {
        this.level = level;
        this.mob   = mob;
    }
}
