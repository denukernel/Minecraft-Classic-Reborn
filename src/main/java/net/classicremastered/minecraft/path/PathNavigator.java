package net.classicremastered.minecraft.path;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;

import java.util.*;

/** Per-mob cached routes, with collision validation, bounded replans, and stuck recovery. */
public final class PathNavigator {
    private final Level level;
    private float gx, gy, gz;
    private boolean hasGoal;
    private boolean movingGoal;
    private List<Pathfinder.Node> path = Collections.emptyList();
    private int index, replanTicks, progressTicks;
    private float progressX, progressY, progressZ;
    private float plannedX, plannedY, plannedZ;
    private int failedPlans;
    private boolean knownPosition;
    private float lastX, lastY, lastZ;
    private boolean aligning, alignZAxis;
    private float alignX, alignY, alignZ;

    public PathNavigator(Level level) {
        this.level = level;
    }

    public PathNavigator setGoal(float x, float feetY, float z) {
        float movement = (x - gx) * (x - gx) + (z - gz) * (z - gz);
        movingGoal = hasGoal && (movement > 0.0001f || Math.abs(feetY - gy) > 0.01f);
        float plannedMovement = (x - plannedX) * (x - plannedX) + (z - plannedZ) * (z - plannedZ);
        if (!hasGoal || plannedMovement > 9 || Math.abs(feetY - plannedY) > 1) replanTicks = 0;
        else if (movingGoal && !path.isEmpty()) replanTicks = Math.min(replanTicks, 8);
        gx = x;
        gy = feetY;
        gz = z;
        hasGoal = true;
        return this;
    }

    public void clearGoal() {
        hasGoal = false;
        aligning = false;
        path = Collections.emptyList();
        index = 0;
        replanTicks = 0;
        failedPlans = 0;
        knownPosition = false;
        progressTicks = 0;
    }

    public boolean hasGoal() {
        return hasGoal;
    }

    public boolean isWaitingForRoute() {
        return failedPlans > 0 && index >= path.size();
    }

    public List<Pathfinder.Node> pathSnapshot() {
        return List.copyOf(path);
    }

    private void advance(Mob mob, GroundClearance ground) {
        float reach =
                Math.max(
                        0.035f,
                        Math.min(0.18f, ((float) Math.ceil(ground.width) - ground.width) * 0.35f));
        while (index < path.size()) {
            Pathfinder.Node next = path.get(index);
            float dx = next.x + ground.offset - mob.x, dz = next.z + ground.offset - mob.z;
            if (dx * dx + dz * dz > reach * reach || Math.abs(mob.bb.y0 - next.feetY) > 0.2f) break;
            index++;
        }
    }

    public Result steer(Mob mob) {
        if (!hasGoal || mob.bb == null) return Result.idle();
        float mx = mob.x - lastX, mz = mob.z - lastZ;
        if (knownPosition && (mx * mx + mz * mz > 9 || Math.abs(mob.bb.y0 - lastY) > 2)) {
            path = Collections.emptyList();
            index = 0;
            replanTicks = 0;
            progressTicks = 0;
            aligning = false;
        }
        lastX = mob.x;
        lastY = mob.bb.y0;
        lastZ = mob.z;
        knownPosition = true;
        GroundClearance ground = new GroundClearance(level, mob);
        float feet = mob.bb.y0;
        if (aligning) {
            float lane = alignZAxis ? Math.abs(mob.z - alignZ) : Math.abs(mob.x - alignX);
            if (lane < 0.07f
                    || Math.abs(feet - alignY) > 0.5f
                    || !ground.bodyLineClear(
                            mob.x, Math.max(feet, alignY), mob.z, alignX, alignZ)) {
                aligning = false;
                replanTicks = 0;
            } else return toward(mob, alignX, alignY, alignZ);
        }
        float dx = gx - mob.x, dz = gz - mob.z;
        if (dx * dx + dz * dz < 0.4f * 0.4f && Math.abs(feet - gy) < 0.5f) return Result.arrived();
        if (dx * dx + dz * dz < 2.25f && ground.walkableLine(mob.x, feet, mob.z, gx, gy, gz))
            return toward(mob, gx, gy, gz);
        replanTicks--;
        advance(mob, ground);
        if (index >= path.size() && !path.isEmpty()) replanTicks = 0;
        boolean invalid =
                index < path.size()
                        && !ground.standable(
                                path.get(index).x, path.get(index).feetY, path.get(index).z);
        if (invalid) {
            path = Collections.emptyList();
            index = 0;
            replanTicks = Math.min(replanTicks, 3);
        }
        if (++progressTicks >= 20) {
            float px = mob.x - progressX, py = feet - progressY, pz = mob.z - progressZ;
            if (px * px + py * py + pz * pz < 0.015f && index < path.size()) replanTicks = 0;
            progressX = mob.x;
            progressY = feet;
            progressZ = mob.z;
            progressTicks = 0;
        }
        if (replanTicks <= 0 && (mob.onGround || path.isEmpty())) {
            plannedX = gx;
            plannedY = gy;
            plannedZ = gz;
            Pathfinder.Node goal = ground.chaseGoal(gx, gy, gz);
            List<Pathfinder.Node> planned =
                    goal == null
                            ? null
                            : Pathfinder.findPath(ground, mob, goal.x, goal.feetY, goal.z, 1024);
            if (planned != null) {
                path = planned;
                index = 0;
                failedPlans = 0;
            } else if (index >= path.size()) {
                path = Collections.emptyList();
                index = 0;
                failedPlans = Math.min(4, failedPlans + 1);
            }
            int stagger = Math.floorMod(System.identityHashCode(mob), 5);
            replanTicks =
                    path.isEmpty()
                            ? 10 * Math.max(1, failedPlans) + stagger
                            : (movingGoal ? 8 : 20) + stagger;
            advance(mob, ground);
            if (index == 0 && path.size() > 1) {
                Pathfinder.Node successor = path.get(1);
                float nx = successor.x + ground.offset, nz = successor.z + ground.offset;
                float horizontal = (nx - mob.x) * (nx - mob.x) + (nz - mob.z) * (nz - mob.z);
                float rise = successor.feetY - feet;
                // A refreshed route must not send a descending or ascending mob
                // back to a cell center that it has already passed on the step.
                if (Math.abs(rise) > 0.2f
                        && Math.abs(rise) <= 1.01f
                        && horizontal <= 2.56f
                        && !ground.ladderNode(successor.x, successor.feetY, successor.z)
                        && ground.bodyLineClear(
                                mob.x, Math.max(feet, successor.feetY), mob.z, nx, nz)) index = 1;
            }
        }
        if (index >= path.size()) return Result.idle();
        Pathfinder.Node rung = path.get(index);
        Pathfinder.Node previous = rung.parent;
        if (previous != null && (previous.x == rung.x && previous.z == rung.z
                || ground.ladderNode(previous.x, previous.feetY, previous.z)
                || ground.ladderNode(rung.x, rung.feetY, rung.z))) {
            boolean vertical = previous.x == rung.x && previous.z == rung.z;
            boolean clear = vertical ? ground.climbEdge(rung.x, previous.feetY, rung.z, rung.feetY)
                    : ground.bodyLineClear(mob.x, Math.max(feet, rung.feetY), mob.z,
                            rung.x + ground.offset, rung.z + ground.offset);
            if (!clear) {
                path = Collections.emptyList();
                index = replanTicks = 0;
                return Result.idle();
            }
            return Result.ladder(rung.x + ground.offset - mob.x, rung.feetY - feet,
                    rung.z + ground.offset - mob.z);
        }
        if (index == 0 && path.size() > 1 && mob.onGround && crowded(mob)) {
            Pathfinder.Node start = path.get(0), next = path.get(1);
            float upper = Math.max(feet, next.feetY);
            if (Math.abs(start.feetY - feet) < 0.2f
                    && !ground.bodyLineClear(
                            mob.x, upper, mob.z, next.x + ground.offset, next.z + ground.offset)) {
                boolean xLeg = next.x != start.x;
                float x = xLeg ? mob.x : start.x + ground.offset,
                        z = xLeg ? start.z + ground.offset : mob.z;
                float lane = xLeg ? Math.abs(mob.z - z) : Math.abs(mob.x - x);
                if (lane > 0.1f && ground.walkableLine(mob.x, feet, mob.z, x, start.feetY, z)) {
                    // Finish the small lateral move into a narrow lane before
                    // reconsidering shortcuts. Crowd pressure must not toggle
                    // between a backward cell center and the forward doorway.
                    aligning = true;
                    alignZAxis = xLeg;
                    alignX = x;
                    alignY = start.feetY;
                    alignZ = z;
                    return toward(mob, alignX, alignY, alignZ);
                }
            }
        }
        // Skip redundant grid centers only across a fully supported, body-clear corridor.
        // This also prevents periodic replans from steering back to the start-cell center.
        for (int look = path.size() - 1;
                look > index;
                look = look > index + 6 ? index + 6 : look - 1) {
            Pathfinder.Node candidate = path.get(look);
            boolean ladderAhead = false;
            for (int step = index; step <= look; step++) {
                Pathfinder.Node node = path.get(step);
                if (ground.ladderNode(node.x, node.feetY, node.z)) {
                    ladderAhead = true;
                    break;
                }
            }
            if (ladderAhead)
                continue;
            if (ground.walkableLine(
                    mob.x,
                    feet,
                    mob.z,
                    candidate.x + ground.offset,
                    candidate.feetY,
                    candidate.z + ground.offset)) {
                index = look;
                break;
            }
        }
        if (index + 1 < path.size()) {
            Pathfinder.Node current = path.get(index), successor = path.get(index + 1);
            float nx = successor.x + ground.offset, nz = successor.z + ground.offset;
            float rise = successor.feetY - feet;
            boolean aligned =
                    current.z == successor.z
                                    && Math.abs(mob.z - (current.z + ground.offset)) < 0.25f
                            || current.x == successor.x
                                    && Math.abs(mob.x - (current.x + ground.offset)) < 0.25f;
            float horizontal = (nx - mob.x) * (nx - mob.x) + (nz - mob.z) * (nz - mob.z);
            if (aligned
                    && Math.abs(rise) > 0.2f
                    && Math.abs(rise) <= 1.01f
                    && horizontal <= 2.56f
                    && !ground.ladderNode(successor.x, successor.feetY, successor.z)
                    && ground.bodyLineClear(mob.x, Math.max(feet, successor.feetY), mob.z, nx, nz))
                index++;
        }
        Pathfinder.Node next = path.get(index);
        float x = next.x + ground.offset, z = next.z + ground.offset;
        // Do not feed movement into a newly blocked corridor while waiting for a replan.
        float upper = Math.max(feet, next.feetY);
        if (!ground.bodyLineClear(mob.x, upper, mob.z, x, z)) {
            replanTicks = Math.min(replanTicks, 3);
            return Result.idle();
        }
        return toward(mob, x, next.feetY, z);
    }

    private Result toward(Mob mob, float x, float feetY, float z) {
        float yaw = (float) Math.toDegrees(Math.atan2(z - mob.z, x - mob.x)) - 90f;
        float distance = (x - mob.x) * (x - mob.x) + (z - mob.z) * (z - mob.z);
        boolean jump =
                feetY > mob.bb.y0 + Math.max(0.25f, mob.footSize) + 0.01f && distance < 2.25f
                        && !(net.classicremastered.minecraft.level.tile.Block.blocks[level.getTile(
                                (int) Math.floor(x), (int) Math.floor(feetY - .01f), (int) Math.floor(
                                        z))] instanceof net.classicremastered.minecraft.level.tile.StairBlock);
        return new Result(
                yaw, true, jump, Math.max(0.15f, Math.min(1f, (float) Math.sqrt(distance) * 2f)));
    }

    private boolean crowded(Mob mob) {
        for (Object entity : level.findEntities(mob, mob.bb.grow(1.2f, 0.5f, 1.2f))) {
            if (entity instanceof Mob other
                    && !(other instanceof net.classicremastered.minecraft.player.Player)
                    && !other.removed
                    && other.health > 0
                    && !other.noPhysics) return true;
        }
        return false;
    }

    public static final class Result {
        public final float targetYaw;
        public final boolean forward, wantJump;
        public final float speedFactor;
        public final boolean ladder;
        public final float ladderX, ladderY, ladderZ;

        private Result(float yaw, boolean forward, boolean jump, float speed) {
            this(yaw, forward, jump, speed, false, 0f, 0f, 0f);
        }

        private Result(float yaw, boolean forward, boolean jump, float speed, boolean ladder,
                float ladderX, float ladderY, float ladderZ) {
            this.ladder = ladder;
            this.ladderX = ladderX;
            this.ladderY = ladderY;
            this.ladderZ = ladderZ;
            targetYaw = yaw;
            this.forward = forward;
            wantJump = jump;
            speedFactor = speed;
        }

        private static Result ladder(float dx, float dy, float dz) {
            return new Result(0f, false, false, 0f, true, dx, dy, dz);
        }

        public static Result idle() {
            return new Result(0, false, false, 0);
        }

        public static Result arrived() {
            return idle();
        }
    }
}
