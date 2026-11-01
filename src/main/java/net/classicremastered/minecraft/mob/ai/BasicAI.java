package net.classicremastered.minecraft.mob.ai;

import java.util.List;
import java.util.Random;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ScaryMindZombie;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.goal.goal.Goal;
import net.classicremastered.minecraft.mob.ai.goal.goal.GoalSelector;
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
    private final GoalSelector selector = new GoalSelector();
    private transient net.classicremastered.minecraft.path.PathNavigator navigator;
    private transient Level navigationLevel;
    private transient boolean steeredThisTick;
    private int wanderRouteTicks;
    private transient GroundWander wander;
    private transient CrowdMotion crowdMotion;
    private transient boolean routeDriven;
    private transient float routeYaw;
    private transient boolean yieldingToCrowd;
    public final boolean isYieldingToCrowd(){return yieldingToCrowd;}
    final boolean pursuingRoute(){return routeDriven;}
    final CrowdMotion crowdMotion() {
        if(crowdMotion==null)crowdMotion=new CrowdMotion();
        return crowdMotion;
    }
    
    @Override
    public void tick(Level level, Mob mob) {
        ++this.noActionTime;
        Entity player;
        selector.tick(); // runs active goal
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
        float tickYaw=mob.yRot;
        yieldingToCrowd=false;
        if (this.attackDelay > 0) {
            --this.attackDelay;
        }

        if (mob.health <= 0) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.yya = 0.0F;
            this.yRotA = 0.0F;
        } else {
            steeredThisTick = false;
            routeDriven = false;
            float previousYaw = mob.yRot;
            this.update();
            if (!steeredThisTick && navigationAllowed() && !mob.isInWater() && !mob.isInLava()) {
                if (attackTarget != null && !attackTarget.removed) {
                    if (wander != null) wander = null;
                    mob.yRot = previousYaw;
                    navigateTo(attackTarget.x, attackTarget.bb.y0, attackTarget.z,
                        runSpeed * (running ? Math.min(1.4f, runMultiplier) : 1f));
                } else if (usesPlannedWandering()) {
                    if (wander == null || wander.level != level) wander = new GroundWander(level, random);
                    mob.yRot = previousYaw;
                    applyRoute(wander.steer(mob, random), runSpeed * 0.75f, 8f);
                } else if (usesWanderNavigation() && (wanderRouteTicks > 0 || (mob.onGround && mob.horizontalCollision && yya > 0))) {
                    if (wanderRouteTicks-- <= 0) {
                        float angle = (mob.yRot + 70f + random.nextFloat() * 220f) * (float)Math.PI / 180f;
                        getNavigator().setGoal(mob.x-(float)Math.sin(angle)*5f,mob.bb.y0,mob.z+(float)Math.cos(angle)*5f);
                        wanderRouteTicks = 60;
                    }
                    mob.yRot = previousYaw;
                    applyRoute(getNavigator().steer(mob), runSpeed);
                }
            }
        }

        if(routeDriven && mob.onGround && yya>0 && navigationAllowed()) {
            float factor=crowdMotion().forwardFactor(this,routeYaw);
            yya*=factor;
            if(factor<0.15f) {
                yieldingToCrowd=true;jumping=false;mob.xd*=0.6f;mob.zd*=0.6f;mob.yRot=tickYaw;
            }
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
                if (e.isPushable() && (!(e instanceof Mob) || e instanceof net.classicremastered.minecraft.player.Player
                        || mob instanceof net.classicremastered.minecraft.player.Player)) {
                    e.push(mob);
                }
            }
            if(!(mob instanceof net.classicremastered.minecraft.player.Player))crowdMotion().separate(mob,list);
        }
    }
    public void addGoal(Goal g) {
        selector.addGoal(g);
    }

    protected boolean navigationAllowed() {
        return !(mob instanceof net.classicremastered.minecraft.player.Player);
    }
    protected boolean usesWanderNavigation() { return true; }
    protected boolean usesPlannedWandering() { return usesWanderNavigation(); }

    private net.classicremastered.minecraft.path.PathNavigator getNavigator() {
        if (navigator == null || navigationLevel != level) {
            navigationLevel = level;
            navigator = new net.classicremastered.minecraft.path.PathNavigator(level);
        }
        return navigator;
    }

    protected final boolean navigateTo(float x, float feetY, float z, float speed) {
        wander = null;
        steeredThisTick = true;
        if(attackTarget!=null && Math.abs(x-attackTarget.x)<0.01f && Math.abs(z-attackTarget.z)<0.01f) {
            float[] approach=crowdMotion().approach(this);
            if(approach!=null){x=approach[0];feetY=approach[1];z=approach[2];}
        }
        getNavigator().setGoal(x, feetY, z);
        return applyRoute(getNavigator().steer(mob), speed);
    }

    /** For specialized AI that applies acceleration after the normal travel step. */
    protected final void accelerateTo(Entity target, float acceleration) {
        getNavigator().setGoal(target.x,target.bb.y0,target.z);
        applyRoute(getNavigator().steer(mob),1f);
        mob.moveRelative(xxa,yya,acceleration);
        if(jumping && mob.onGround)jumpFromGround();
    }

    private boolean applyRoute(net.classicremastered.minecraft.path.PathNavigator.Result result, float speed) {
        return applyRoute(result, speed, 18f);
    }
    private boolean applyRoute(net.classicremastered.minecraft.path.PathNavigator.Result result, float speed, float maxTurn) {
        routeDriven=result.forward;
        routeYaw=result.targetYaw;
        xxa = yya = yRotA = 0;
        jumping = false;
        if (!result.forward) {
            mob.xd *= 0.5f; mob.zd *= 0.5f;
            return false;
        }
        float turn = result.targetYaw - mob.yRot;
        while (turn < -180f) turn += 360f;
        while (turn >= 180f) turn -= 360f;
        mob.yRot += Math.max(-maxTurn, Math.min(maxTurn, turn));
        if (Math.abs(turn) > 35f) {
            mob.xd *= 0.5f; mob.zd *= 0.5f;
            return false;
        }
        yya = Math.min(speed, 1.1f) * result.speedFactor;
        if (result.speedFactor < 0.5f) { mob.xd *= 0.7f; mob.zd *= 0.7f; }
        jumping = result.wantJump && mob.onGround;
        return true;
    }
    protected void jumpFromGround() {
        this.mob.yd = 0.42F;
    }

    protected boolean checkAndPerformFlee() {
        if (this.mob instanceof Villager && !((Villager) this.mob).isHostile()) {
            if (this.level == null) return false;
            java.util.List near = this.level.findEntities(this.mob, this.mob.bb.grow(8.0F, 4.0F, 8.0F));
            Entity nearestThreat = null;
            float nearestDist2 = Float.MAX_VALUE;
            if (near != null) {
                for (Object o : near) {
                    if (o instanceof Zombie || o instanceof Skeleton) {
                        Entity threat = (Entity) o;
                        if (!threat.removed) {
                            float dx = threat.x - this.mob.x;
                            float dy = threat.y - this.mob.y;
                            float dz = threat.z - this.mob.z;
                            float dist2 = dx * dx + dy * dy + dz * dz;
                            if (dist2 < nearestDist2) {
                                nearestDist2 = dist2;
                                nearestThreat = threat;
                            }
                        }
                    }
                }
            }

            if (nearestThreat != null) {
                float dx = this.mob.x - nearestThreat.x;
                float dz = this.mob.z - nearestThreat.z;
                float targetYaw = (float) (Math.atan2((double) dz, (double) dx) * 180.0D / Math.PI) - 90.0F;
                float dyaw = targetYaw - this.mob.yRot;
                while (dyaw < -180.0F) dyaw += 360.0F;
                while (dyaw >= 180.0F) dyaw -= 360.0F;
                this.yRotA = dyaw * 0.4F;

                this.running = true;
                this.yya = this.runSpeed * 1.5F;

                boolean obstacleJump = (this.mob.onGround && this.mob.horizontalCollision);
                boolean mediumJump = (this.mob.isInWater() || this.mob.isInLava()) && this.random.nextFloat() < 0.8F;
                this.jumping = obstacleJump || mediumJump;

                this.mob.yRot += this.yRotA;
                this.mob.xRot = (float) this.defaultLookAngle;

                if (this.yya > this.runSpeed * 1.6F) this.yya = this.runSpeed * 1.6F;
                if (this.yya < -this.runSpeed * 1.6F) this.yya = -this.runSpeed * 1.6F;
                this.xxa = 0.0F;
                this.noActionTime = 0;
                float distance=(float)Math.sqrt(dx*dx+dz*dz);
                if(distance>0.001f)navigateTo(mob.x+dx/distance*5f,mob.bb.y0,mob.z+dz/distance*5f,runSpeed*1.5f);
                return true;
            }
        }
        return false;
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

        if (checkAndPerformFlee()) {
            return;
        }

        final boolean inWater = this.mob.isInWater();
        final boolean inLava  = this.mob.isInLava();

        if (attackTarget == null && usesPlannedWandering() && navigationAllowed() && !inWater && !inLava) {
            // The persistent wandering controller owns the continuous walk.
            xxa = yya = yRotA = 0;
            jumping = running = false;
            return;
        }

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
