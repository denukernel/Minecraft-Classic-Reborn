package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.ai.goal.Goal;
import net.classicremastered.minecraft.mob.ai.goal.GoalSelector;
import net.classicremastered.minecraft.mob.ai.goal.movement.FleeThreatGoal;
import net.classicremastered.minecraft.mob.ai.goal.movement.MoveToTargetGoal;
import net.classicremastered.minecraft.mob.ai.goal.movement.PlannedStrollGoal;
import net.classicremastered.minecraft.mob.ai.goal.movement.RandomStrollGoal;
import net.classicremastered.minecraft.mob.ai.movement.CrowdMotion;
import net.classicremastered.minecraft.mob.ai.movement.GroundWander;
import net.classicremastered.minecraft.path.PathNavigator;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.util.CreativeModeHelper;

import java.util.List;
import java.util.Random;

public class BasicAI extends AI {

    public static final long serialVersionUID = 0L;
    public Random random = new Random();
    public float xxa;
    public float yya;
    public float yRotA;
    public Level level;
    public Mob mob;
    public boolean jumping = false;
    public int attackDelay = 0;
    public float runSpeed = 0.7F;
    public int noActionTime = 0;
    public Entity attackTarget = null;
    public boolean running = false;
    public boolean suppressRandomJump = false;
    public float walkSpeed = 0.10f; // ground
    public float airSpeed = 0.02f; // jumping/falling
    public float swimSpeed = 0.02f;
    public float lavaSpeed = 0.02f;
    public float runMultiplier = 1.40f; // applied when running == true
    public float speedScale = 1.00f; // global multiplier
    private transient GoalSelector selector;
    private transient GoalSelector movementGoals;
    private transient FleeThreatGoal fleeGoal;
    private transient java.util.List<GoalSelector> goalSets;
    private transient net.classicremastered.minecraft.path.PathNavigator navigator;
    private transient Level navigationLevel;
    private transient boolean steeredThisTick;
    private int wanderRouteTicks;
    private transient GroundWander wander;
    private transient GroundWander blockedPursuit;
    private transient Entity blockedPursuitTarget;
    private transient boolean detouring;
    private transient CrowdMotion crowdMotion;
    private transient boolean routeDriven;
    private transient float routeYaw;
    private transient boolean yieldingToCrowd;
    private transient int crowdWaitTicks;

    public final boolean isYieldingToCrowd() {
        return yieldingToCrowd;
    }

    public final boolean pursuingRoute() {
        return routeDriven && !detouring;
    }

    public final CrowdMotion crowdMotion() {
        if (crowdMotion == null) crowdMotion = new CrowdMotion();
        return crowdMotion;
    }

    @Override
    public void tick(Level level, Mob mob) {
        ladderStep = null;
        if (shorePath == null) shorePath = java.util.List.of();
        ++this.noActionTime;
        if (CreativeModeHelper.shouldIgnoreTarget(this.attackTarget)) {
            if (selector != null) stopAllGoals();
            this.attackTarget = null;
            return;
        }
        this.level = level;
        this.mob = mob;
        float tickYaw = mob.yRot;
        yieldingToCrowd = false;
        if (this.attackDelay > 0) {
            --this.attackDelay;
        }

        boolean shelteringInWater = false;
        if (mob.health <= 0) {
            if (selector != null) selector.stopAll();
            this.jumping = false;
            this.xxa = 0.0F;
            this.yya = 0.0F;
            this.yRotA = 0.0F;
        } else {
            steeredThisTick = false;
            routeDriven = false;
            detouring = false;
            if (blockedPursuitTarget != attackTarget) {
                blockedPursuit = null;
                blockedPursuitTarget = null;
            }
            float previousYaw = mob.yRot;
            boolean escapingFire = escapeFire();
            boolean seekingWater = !escapingFire && seekSunlightWater();
            shelteringInWater = seekingWater && mob.isWetForFire();
            if (!escapingFire && !seekingWater) {
                this.update();
                goalSelector().tick();
            } else if (selector != null) {
                selector.stopAll();
            }
            if (shoreAirborne()) steeredThisTick = true;
            if (!steeredThisTick && navigationAllowed() && !mob.isInWater() && !mob.isInLava()) {
                if (attackTarget != null && !attackTarget.removed) {
                    if (wander != null) wander = null;
                    mob.yRot = previousYaw;
                    navigateTo(
                            attackTarget.x,
                            attackTarget.bb.y0,
                            attackTarget.z,
                            runSpeed * (running ? Math.min(1.4f, runMultiplier) : 1f));
                } else if (usesPlannedWandering()) {
                    if (wander == null || wander.level != level)
                        wander = new GroundWander(level, random);
                    mob.yRot = previousYaw;
                    applyRoute(wander.steer(mob, random), runSpeed * 0.75f, 8f);
                } else if (usesWanderNavigation()
                        && (wanderRouteTicks > 0
                                || (mob.onGround && mob.horizontalCollision && yya > 0))) {
                    if (wanderRouteTicks-- <= 0) {
                        float angle =
                                (mob.yRot + 70f + random.nextFloat() * 220f)
                                        * (float) Math.PI
                                        / 180f;
                        getNavigator()
                                .setGoal(
                                        mob.x - (float) Math.sin(angle) * 5f,
                                        mob.bb.y0,
                                        mob.z + (float) Math.cos(angle) * 5f);
                        wanderRouteTicks = 60;
                    }
                    mob.yRot = previousYaw;
                    applyRoute(getNavigator().steer(mob), runSpeed);
                }
            }
        }

        if (pursuingRoute() && mob.onGround && yya > 0 && navigationAllowed()) {
            float factor = crowdMotion().forwardFactor(this, routeYaw);
            if (factor < 0.15f) {
                // A stationary leader can leave a whole queue suppressing its own jumps.
                if (crowdWaitTicks < 20) {
                    crowdWaitTicks++;
                    yieldingToCrowd = true;
                    jumping = false;
                    mob.xd *= 0.6f;
                    mob.zd *= 0.6f;
                    mob.yRot = tickYaw;
                } else {
                    factor = 0.35f;
                }
            } else crowdWaitTicks = 0;
            yya *= factor;
        } else if (!pursuingRoute() || attackTarget == null) {
            crowdWaitTicks = 0;
        }
        if (mob.health > 0
                && mob.isInWater()
                && !shelteringInWater
                && navigationAllowed()
                && usesDefaultSwimSteering()
                && Pathfinder.supportsSwimming(mob)) {
            // Pursuit rotates toward the target during update. Start from the last
            // swim heading so a shore detour can keep turning away from a wall.
            mob.yRot = tickYaw;
            if (!Pathfinder.canEnterWater(mob) || mob instanceof Zombie && !level.isDaytime()) {
                Entity shoreGoal =
                        attackTarget != null
                                        && !attackTarget.removed
                                        && !attackTarget.isWetForFire()
                                ? attackTarget
                                : null;
                if (!steerToShore(shoreGoal)) {
                    xxa = yya = yRotA = 0;
                    jumping = mob.yd < .12f;
                }
            } else if (attackTarget != null && !attackTarget.removed) steerInWater();
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
        if (ladderStep != null) {
            mob.fallDistance = 0;
            mob.xd = mob.yd = mob.zd = 0;
            mob.move(climbDelta(ladderStep.ladderX), climbDelta(ladderStep.ladderY),
                    climbDelta(ladderStep.ladderZ));
            mob.fallDistance = 0;
        } else {
            mob.travel(this.xxa, this.yya);
        }

        List list = level.findEntities(mob, mob.bb.grow(0.2F, 0.0F, 0.2F));
        if (list != null && list.size() > 0) {
            for (int i = 0; i < list.size(); ++i) {
                Entity e = (Entity) list.get(i);
                if (e.isPushable()
                        && (!(e instanceof Mob)
                                || e instanceof net.classicremastered.minecraft.player.Player
                                || mob instanceof net.classicremastered.minecraft.player.Player)) {
                    e.push(mob);
                }
            }
            if (!(mob instanceof net.classicremastered.minecraft.player.Player))
                crowdMotion().separate(mob, list);
        }
    }

    protected boolean usesDefaultSwimSteering() {
        return true;
    }

    private transient net.classicremastered.minecraft.path.PathNavigator.Result ladderStep;

    private int swimRepath;
    private transient java.util.List<net.classicremastered.minecraft.path.ShoreNavigator.Step>
            shorePath = java.util.List.of();
    private int shoreIndex, shoreRepath, shoreJumpTicks;
    private transient Entity shoreTarget;

    private boolean shoreAirborne() {
        if (shoreJumpTicks > 0) shoreJumpTicks--;
        if (shorePath == null
                || shorePath.isEmpty()
                || shoreTarget != null && (shoreTarget != attackTarget || shoreTarget.removed))
            return false;
        var exit = shorePath.get(shorePath.size() - 1);
        if (mob.onGround && mob.bb.y0 >= exit.feetY() - .1f) {
            shorePath = java.util.List.of();
            shoreRepath = 0;
            clearNavigation();
            return false;
        }
        if (shoreJumpTicks == 0 || mob.isInWater() || mob.onGround) return false;
        shoreHeading(exit);
        jumping = false;
        return true;
    }

    private void shoreHeading(net.classicremastered.minecraft.path.ShoreNavigator.Step step) {
        float yaw = (float) Math.toDegrees(Math.atan2(step.z() - mob.z, step.x() - mob.x)) - 90f,
                d = yaw - mob.yRot;
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        mob.yRot += Math.max(-20, Math.min(20, d));
        xxa = 0;
        yRotA = 0;
        yya = Math.min(runSpeed, 1.1f);
        routeDriven = false;
        yieldingToCrowd = false;
    }

    private boolean steerToShore(Entity target) {
        if (target != null && target.isInWater()) return false;
        if (shorePath == null
                || shoreTarget != target
                || shoreJumpTicks == 0 && --shoreRepath <= 0) {
            shorePath =
                    net.classicremastered.minecraft.path.ShoreNavigator.find(level, mob, target);
            shoreIndex = shorePath.size() > 1 ? 1 : 0;
            shoreTarget = target;
            shoreRepath = 20;
        }
        if (shorePath.isEmpty()) return false;
        while (shoreIndex < shorePath.size() - 1) {
            var step = shorePath.get(shoreIndex);
            float dx = step.x() - mob.x, dz = step.z() - mob.z;
            if (dx * dx + dz * dz > .25f) break;
            shoreIndex++;
        }
        var step = shorePath.get(shoreIndex);
        shoreHeading(step);
        jumping = mob.yd < .16f;
        var exit = shorePath.get(shorePath.size() - 1);
        float dx = exit.x() - mob.x, dz = exit.z() - mob.z;
        float rise = exit.feetY() - mob.bb.y0;
        if (shoreIndex == shorePath.size() - 1
                && dx * dx + dz * dz < 2.56f
                && rise < 1.6f
                && shoreJumpTicks == 0) {
            mob.yd = Math.max(mob.yd, rise > .9f ? .58f : .42f);
            shoreJumpTicks = 18;
            jumping = false;
        }
        return true;
    }

    private float swimX, swimZ;

    private boolean swimClear(int x, int y, int z) {
        if (y < 1 || y >= level.depth - 2) return false;
        if (level instanceof net.classicremastered.minecraft.level.LevelInfiniteTerrain t
                && !t.chunks().isChunkLoaded(x >> 4, z >> 4)) return false;
        if (level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat t
                && !t.chunks().isChunkLoaded(x >> 4, z >> 4)) return false;
        return !level.isSolidTile(x, y, z) && !level.isSolidTile(x, y + 1, z);
    }

    private void steerInWater() {
        if (steerToShore(attackTarget)) return;
        if (--swimRepath <= 0) {
            swimRepath = 10;
            swimX = attackTarget.x;
            swimZ = attackTarget.z;
            if (mob.horizontalCollision) {
                int fx = (int) Math.floor(mob.x),
                        fy = (int) Math.floor(mob.bb.y0),
                        fz = (int) Math.floor(mob.z);
                float best = Float.MAX_VALUE;
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int x = fx + dx, z = fz + dz;
                        if (!swimClear(x, fy, z)
                                && !(swimClear(x, fy + 1, z) && level.isSolidTile(x, fy, z)))
                            continue;
                        // Check the short approach corridor at the swimming height.
                        boolean clear = true;
                        for (int i = 1; i <= 3; i++) {
                            int cx = fx + Math.round(dx * i / 3f),
                                    cz = fz + Math.round(dz * i / 3f);
                            if (!swimClear(cx, fy, cz) && !swimClear(cx, fy + 1, cz)) {
                                clear = false;
                                break;
                            }
                        }
                        if (!clear) continue;
                        float tx = x + .5f - attackTarget.x, tz = z + .5f - attackTarget.z;
                        float score = tx * tx + tz * tz + (dx * dx + dz * dz) * .3f;
                        if (score < best) {
                            best = score;
                            swimX = x + .5f;
                            swimZ = z + .5f;
                        }
                    }
            }
        }
        float dx = swimX - mob.x, dz = swimZ - mob.z;
        if (dx * dx + dz * dz > .09f) {
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f, d = yaw - mob.yRot;
            while (d > 180) d -= 360;
            while (d < -180) d += 360;
            mob.yRot += Math.max(-20, Math.min(20, d));
            xxa = 0;
            yya = runSpeed;
        }
        if (mob instanceof net.classicremastered.minecraft.mob.Skeleton
                && dx * dx + dz * dz >= 36
                && dx * dx + dz * dz <= 144
                && level.clip(
                                new net.classicremastered.minecraft.model.Vec3D(
                                        mob.x, mob.bb.y0 + 1.5f, mob.z),
                                new net.classicremastered.minecraft.model.Vec3D(
                                        attackTarget.x, attackTarget.bb.y0 + 1f, attackTarget.z))
                        == null) yya = 0;
        jumping = !attackTarget.isInWater() || mob.bb.y0 < attackTarget.bb.y0 + .2f;
        routeDriven = false;
        yieldingToCrowd = false;
        // Buoyancy is independent of random-jump suppression and fades at the surface.
        if (mob.yd > .16f) mob.yd = .16f;
    }

    public void addGoal(Goal g) {
        goalSelector().addGoal(g);
    }

    public void addGoal(int priority, Goal goal) {
        goalSelector().addGoal(priority, goal);
    }

    private boolean escapeFire() {
        if (!navigationAllowed()) return false;
        boolean igniter = mob instanceof net.classicremastered.minecraft.mob.ZombieIgniter;
        var fireBounds = igniter ? mob.bb.grow(.5f, 0, .5f) : mob.bb;
        if ((!igniter && mob.remainingFireTicks <= 0) || !mob.overlapsFire(fireBounds))
            return false;

        float bestDistance = Float.POSITIVE_INFINITY;
        float bestYaw = mob.yRot;
        for (int direction = 0; direction < 16; direction++) {
            float yaw = mob.yRot + direction * 22.5f;
            double angle = Math.toRadians(yaw);
            float dx = (float) -Math.sin(angle), dz = (float) Math.cos(angle);
            for (float distance = .5f; distance <= 5f && distance < bestDistance; distance += .5f) {
                var next = mob.bb.cloneMove(dx * distance, 0, dz * distance);
                if (!level.getCubes(next).isEmpty()
                        || level.getCubes(next.cloneMove(0, -.65f, 0)).isEmpty()) break;
                if (!mob.overlapsFire(fireBounds.cloneMove(dx * distance, 0, dz * distance))) {
                    bestDistance = distance;
                    bestYaw = yaw;
                    break;
                }
            }
        }
        if (!Float.isFinite(bestDistance)) return false;

        mob.yRot = bestYaw;
        xxa = yRotA = 0;
        yya = 1;
        running = true;
        jumping = false;
        steeredThisTick = true;
        return true;
    }

    private transient PathNavigator waterRefuge;
    private transient Level waterRefugeLevel;
    private transient int waterSearchDelay;
    private transient Pathfinder.Node waterDestination;
    private transient boolean usingWaterShelter;

    private boolean seekSunlightWater() {
        if (!(mob instanceof Zombie zombie)
                || !navigationAllowed()
                || !Pathfinder.supportsSwimming(zombie)
                || !zombie.canBurnInSunlight()
                || net.classicremastered.minecraft.CombatRules.difficulty(level)
                        != net.classicremastered.minecraft.Difficulty.EXTREME
                || !level.isDaytime()
                || level.getDaylightBrightness() < .55f
                || !(level.shouldUndeadBurnAt(zombie)
                        || (usingWaterShelter || waterDestination != null)
                                && zombie.isInWaterOrOnWater())) {
            waterDestination = null;
            usingWaterShelter = false;
            waterSearchDelay = 0;
            if (waterRefuge != null) waterRefuge.clearGoal();
            return false;
        }
        if (zombie.isInWaterOrOnWater()) {
            usingWaterShelter = true;
            holdPosition(mob.yRot);
            mob.xd *= .5f;
            mob.zd *= .5f;
            return true;
        }
        if (waterRefuge == null || waterRefugeLevel != level) {
            waterRefuge = new PathNavigator(level);
            waterRefugeLevel = level;
            waterDestination = null;
            waterSearchDelay = 0;
        }
        if (waterDestination != null
                && !level.isWater(waterDestination.x, waterDestination.y - 1, waterDestination.z)) {
            waterDestination = null;
            waterRefuge.clearGoal();
        }
        if (waterDestination == null && --waterSearchDelay <= 0) {
            waterSearchDelay = 20;
            waterDestination = Pathfinder.findWaterRefuge(level, mob);
            if (waterDestination == null) waterRefuge.clearGoal();
        }
        if (waterDestination == null) return false;
        float offset = Pathfinder.offset(mob.bbWidth);
        float x = waterDestination.x + offset, z = waterDestination.z + offset;
        float dx = x - mob.x, dz = z - mob.z;
        if (dx * dx + dz * dz < .16f && Math.abs(mob.bb.y0 - waterDestination.feetY) < .5f) {
            holdPosition((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
            yya = .55f;
            return true;
        }
        var route = waterRefuge.setGoal(x, waterDestination.feetY, z).steerToWaterRefuge(mob);
        if (waterRefuge.isWaitingForRoute()) {
            waterDestination = null;
            waterRefuge.clearGoal();
            return false;
        }
        steeredThisTick = true;
        running = true;
        applyRoute(route, 1f);
        return true;
    }

    /** Goal-facing view of the controller navigation policy. */
    public final boolean canNavigate() {
        return navigationAllowed();
    }

    /** Goal-facing view of the controller wandering policy. */
    public final boolean hasPlannedWandering() {
        return usesPlannedWandering();
    }

    protected boolean navigationAllowed() {
        return !(mob instanceof net.classicremastered.minecraft.player.Player);
    }

    protected boolean usesWanderNavigation() {
        return true;
    }

    protected boolean usesPlannedWandering() {
        return usesWanderNavigation();
    }

    private net.classicremastered.minecraft.path.PathNavigator getNavigator() {
        if (navigator == null || navigationLevel != level) {
            navigationLevel = level;
            navigator = new net.classicremastered.minecraft.path.PathNavigator(level);
        }
        return navigator;
    }

    public final List<net.classicremastered.minecraft.model.Vec3D> debugRoute() {
        var route = waterDestination != null ? waterRefuge : navigator;
        if (mob == null) return List.of();
        if (!mob.isInWater() || waterDestination != null) {
            if (route == null) return List.of();
            float offset = Pathfinder.offset(mob.movementClearanceWidth());
            return route.remainingPath().stream()
                    .map(
                            n ->
                                    new net.classicremastered.minecraft.model.Vec3D(
                                            n.x + offset, n.feetY, n.z + offset))
                    .toList();
        }
        if (shorePath == null || shorePath.isEmpty()) return List.of();
        return shorePath.subList(Math.min(shoreIndex, shorePath.size()), shorePath.size()).stream()
                .map(s -> new net.classicremastered.minecraft.model.Vec3D(s.x(), s.feetY(), s.z()))
                .toList();
    }

    protected final void holdPosition(float yaw) {
        steeredThisTick = true;
        xxa = yya = yRotA = 0;
        jumping = false;
        mob.yRot = yaw;
    }

    public final boolean navigateTo(float x, float feetY, float z, float speed) {
        wander = null;
        steeredThisTick = true;
        boolean pursuingTarget =
                attackTarget != null
                        && Math.abs(x - attackTarget.x) < 0.01f
                        && Math.abs(z - attackTarget.z) < 0.01f;
        if (pursuingTarget) {
            float[] approach = crowdMotion().approach(this);
            if (approach != null) {
                x = approach[0];
                feetY = approach[1];
                z = approach[2];
            }
        }
        var pursuit = getNavigator().setGoal(x, feetY, z);
        var route = pursuit.steer(mob);
        if (pursuingTarget
                && pursuit.isWaitingForRoute()
                && !route.forward
                && !route.ladder
                && this
                        instanceof
                        net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI combat
                && !(combat.withinMeleeReach(attackTarget, 2f)
                        && combat.hasLineOfSight(mob, attackTarget))) {
            if (blockedPursuit == null
                    || blockedPursuit.level != level
                    || blockedPursuitTarget != attackTarget) {
                blockedPursuit = new GroundWander(level, random);
                blockedPursuitTarget = attackTarget;
            }
            detouring = true;
            return applyRoute(blockedPursuit.steer(mob, random), speed * .75f);
        }
        blockedPursuit = null;
        blockedPursuitTarget = null;
        return applyRoute(route, speed);
    }

    /** Follow a route that can enter water for this pursuit. */
    public final void navigateToWater(float x, float feetY, float z, float speed) {
        steeredThisTick = true;
        applyRoute(getNavigator().setGoal(x, feetY, z).steerToWaterRefuge(mob), speed);
    }

    /** For specialized AI that applies acceleration after the normal travel step. */
    public final void accelerateTo(Entity target, float acceleration) {
        getNavigator().setGoal(target.x, target.bb.y0, target.z);
        applyRoute(getNavigator().steer(mob), 1f);
        mob.moveRelative(xxa, yya, acceleration);
        if (jumping && mob.onGround) jumpFromGround();
    }

    private boolean applyRoute(
            net.classicremastered.minecraft.path.PathNavigator.Result result, float speed) {
        return applyRoute(result, speed, 18f);
    }

    private boolean applyRoute(
            net.classicremastered.minecraft.path.PathNavigator.Result result,
            float speed,
            float maxTurn) {
        routeDriven = result.forward;
        routeYaw = result.targetYaw;
        xxa = yya = yRotA = 0;
        jumping = false;
        if (result.ladder) {
            ladderStep = result;
            return true;
        }
        if (!result.forward) {
            if (attackTarget != null && !attackTarget.removed) {
                float face =
                        (float)
                                        Math.toDegrees(
                                                Math.atan2(
                                                        attackTarget.z - mob.z,
                                                        attackTarget.x - mob.x))
                                - 90f
                                - mob.yRot;
                while (face < -180) face += 360;
                while (face >= 180) face -= 360;
                mob.yRot += Math.max(-maxTurn, Math.min(maxTurn, face));
            }
            mob.xd *= 0.5f;
            mob.zd *= 0.5f;
            return false;
        }
        float turn = result.targetYaw - mob.yRot;
        while (turn < -180f) turn += 360f;
        while (turn >= 180f) turn -= 360f;
        mob.yRot += Math.max(-maxTurn, Math.min(maxTurn, turn));
        if (Math.abs(turn) > 35f) {
            mob.xd *= 0.5f;
            mob.zd *= 0.5f;
            return false;
        }
        yya = Math.min(speed, 1.1f) * result.speedFactor;
        if (result.speedFactor < 0.5f) {
            mob.xd *= 0.7f;
            mob.zd *= 0.7f;
        }
        jumping = result.wantJump && mob.onGround;
        return true;
    }

    public final boolean hasActiveRoute() {
        return routeDriven || ladderStep != null;
    }

    public final void clearNavigation() {
        getNavigator().clearGoal();
        ladderStep = null;
        routeDriven = false;
    }

    private static float climbDelta(float value) {
        return Math.max(-.18f, Math.min(.18f, value));
    }

    protected void jumpFromGround() {
        this.mob.yd = 0.42F;
    }

    protected boolean checkAndPerformFlee() {
        if (fleeGoal == null) fleeGoal = new FleeThreatGoal(this);
        if (!fleeGoal.canStart()) return false;
        fleeGoal.tick();
        return true;
    }

    protected void update() {
        defaultMovement();
    }

    protected boolean handlesTargetMovement() {
        return false;
    }

    public final void defaultMovement() {
        if (level == null || mob == null || mob.health <= 0) {
            jumping = false;
            xxa = yya = yRotA = 0;
            return;
        }
        if (attackTarget != null && handlesTargetMovement()) {
            xxa = yya = yRotA = 0f;
            jumping = false;
            return;
        }
        if (movementGoals == null) {
            if (fleeGoal == null) fleeGoal = new FleeThreatGoal(this);
            movementGoals =
                    createGoalSelector(
                            fleeGoal,
                            new PlannedStrollGoal(this),
                            new MoveToTargetGoal(this),
                            new RandomStrollGoal(this));
        }
        movementGoals.tick();
    }

    protected final GoalSelector createGoalSelector(Goal... goals) {
        GoalSelector created = new GoalSelector(goals);
        if (goalSets == null) goalSets = new java.util.ArrayList<>();
        goalSets.add(created);
        return created;
    }

    private void stopAllGoals() {
        if (selector != null) selector.stopAll();
        if (goalSets != null) for (GoalSelector goals : goalSets) goals.stopAll();
    }

    protected final GoalSelector goalSelector() {
        if (selector == null) {
            selector = new GoalSelector();
            registerGoals(selector);
        }
        return selector;
    }

    protected void registerGoals(GoalSelector selector) {}

    public void beforeRemove() {
        stopAllGoals();
    }

    @Override
    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        this.noActionTime = 0;
    }

    public void bind(
            net.classicremastered.minecraft.level.Level level,
            net.classicremastered.minecraft.mob.Mob mob) {
        this.level = level;
        this.mob = mob;
    }
}
