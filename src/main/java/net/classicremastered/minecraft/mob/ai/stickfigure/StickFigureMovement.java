package net.classicremastered.minecraft.mob.ai.stickfigure;

import java.util.ArrayList;
import java.util.List;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.LiquidBlock;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.path.ShoreNavigator;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/**
 * Boss pursuit uses supported shortcuts, checked jump arcs, and controlled
 * buoyancy.
 */
public final class StickFigureMovement {

    private int leapTicks, leapAge, leapCooldown, scanCooldown, lastTick = -1, shoreRepath;
    private float leapX, leapZ;
    private List<Vec3D> leapPath = List.of();
    private List<ShoreNavigator.Step> shorePath = List.of();
    private Entity shoreTarget, chaseTarget;
    private boolean swimming, chasingSwimmer;
    private int noRouteTicks;
    private int shoreIndex;
    private ShoreNavigator.Step bankExit;
    private ShoreNavigator.Step blockedExit;
    private int bankExitTicks;

    public boolean isLeaping() {
        return leapTicks > 0 || bankExit != null;
    }

    public boolean isClimbingBank() {
        return bankExit != null;
    }

    public List<Vec3D> debugRoute() {
        if (bankExit != null) return List.of(debugShoreExit());
        if (leapTicks > 0)
            return List.copyOf(
                    leapPath.subList(Math.min(leapAge, leapPath.size()), leapPath.size()));
        return shorePath.subList(Math.min(shoreIndex, shorePath.size()), shorePath.size()).stream()
                .map(step -> new Vec3D(step.x(), step.feetY(), step.z()))
                .toList();
    }

    public Vec3D debugShoreExit() {
        var exit =
                bankExit != null
                        ? bankExit
                        : shorePath.isEmpty() ? null : shorePath.get(shorePath.size() - 1);
        return exit == null ? null : new Vec3D(exit.x(), exit.feetY(), exit.z());
    }

    public void tick(StickFigure boss) {
        if (lastTick >= 0 && boss.tickCount - lastTick > 1) cancel();
        lastTick = boss.tickCount;
        if (leapCooldown > 0) leapCooldown--;
        if (scanCooldown > 0) scanCooldown--;
    }

    public void cancel() {
        leapTicks = leapAge = 0;
        leapPath = List.of();
        shorePath = List.of();
        shoreTarget = null;
        shoreRepath = 0;
        bankExit = null;
        blockedExit = null;
    }

    public boolean chase(StickFigureAI ai, StickFigure boss, Entity target) {
        boolean inWater = boss.isInWater();
        if (target == null
                || target.removed
                || target.level != boss.level
                || CreativeModeHelper.shouldIgnoreTarget(target)) {
            target = null;
            if (!inWater && !isLeaping()) {
                cancel();
                return false;
            }
        }
        boolean targetInWater =
                target != null && (target.touchesWaterSurface() || target.isUnderWater());
        if (target != chaseTarget || inWater != swimming || targetInWater != chasingSwimmer) {
            ai.clearNavigation();
            if (!isLeaping()) cancel();
            chaseTarget = target;
            swimming = inWater;
            chasingSwimmer = targetInWater;
            noRouteTicks = 0;
        }
        if (bankExit != null) {
            if (climbBank(ai, boss)) return true;
            bankExit = null;
            shoreRepath = 0;
        }
        if (leapTicks > 0) {
            if ((leapAge > 1 && (boss.onGround || boss.horizontalCollision)) || --leapTicks <= 0) {
                boss.fallDistance = 0;
                cancel();
            } else {
                leapAge++;
                ai.xxa = ai.yya = ai.yRotA = 0;
                ai.jumping = false;
                if (leapAge < leapPath.size()) {
                    boss.xd = leapX;
                    boss.zd = leapZ;
                } else {
                    boss.xd = boss.zd = 0;
                }
                boss.fallDistance = 0;
                face(boss, leapX, leapZ, 24);
                return true;
            }
        }
        if (inWater) return swim(ai, boss, target, targetInWater);
        if (target == null) return false;
        if (targetInWater) {
            ai.navigateToWater(target.x, target.bb.y0, target.z, ai.runSpeed);
            return true;
        }
        float distance = (float) Math.hypot(target.x - boss.x, target.z - boss.z);
        float lead = Math.min(6, distance / .5f);
        float x = target.x + Math.max(-2, Math.min(2, target.xd * lead));
        float z = target.z + Math.max(-2, Math.min(2, target.zd * lead));
        boolean waterDetour =
                Pathfinder.waterBetween(boss.level, boss, target.x, target.bb.y0, target.z);
        if (!waterDetour
                && Math.abs(target.bb.y0 - boss.bb.y0) < 1.25f
                && Pathfinder.canWalkStraight(boss.level, boss, x, boss.bb.y0, z)) {
            ai.clearNavigation();
            noRouteTicks = 0;
            steer(ai, boss, x, z, distance < 2 ? .35f : 1f);
            return true;
        }
        ai.navigateTo(target.x, target.bb.y0, target.z, ai.runSpeed * .75f);
        if (ai.hasActiveRoute() || waterDetour) {
            noRouteTicks = 0;
            return false;
        }
        if (++noRouteTicks >= 12 && boss.onGround && leapCooldown == 0 && scanCooldown == 0) {
            scanCooldown = 8;
            if (findLeap(ai, boss, target.x, target.bb.y0, target.z)) return true;
        }
        return false;
    }

    private boolean swim(StickFigureAI ai, StickFigure boss, Entity target, boolean targetInWater) {
        if (shoreRepath > 0) shoreRepath--;
        if (!targetInWater && (target != shoreTarget || shoreRepath == 0)) {
            shoreTarget = target;
            shoreRepath = 32;
            shorePath = ShoreNavigator.find(boss.level, boss, target, blockedExit);
            if (shorePath.isEmpty()) blockedExit = null;
            shoreIndex = 0;
        }
        float x = target == null ? boss.x : target.x, z = target == null ? boss.z : target.z;
        boolean nearBank = false;
        if (!shorePath.isEmpty()) {
            while (shoreIndex < shorePath.size() - 1) {
                ShoreNavigator.Step step = shorePath.get(shoreIndex);
                if (Math.hypot(step.x() - boss.x, step.z() - boss.z) > .6f) break;
                shoreIndex++;
                shoreRepath = 32;
            }
            ShoreNavigator.Step step = shorePath.get(shoreIndex);
            x = step.x();
            z = step.z();
            nearBank = shoreIndex == shorePath.size() - 1 && Math.hypot(x - boss.x, z - boss.z) < 2;
            if (nearBank && leapCooldown == 0) {
                bankExit = step;
                bankExitTicks = 30;
                if (climbBank(ai, boss)) return true;
                bankExit = null;
            }
        }
        boolean reachedTarget = shorePath.isEmpty() && Math.hypot(x - boss.x, z - boss.z) < 1.1f;
        steer(ai, boss, x, z, reachedTarget ? 0 : targetInWater ? 1f : .8f);
        ai.jumping = false;
        int surface = (int) Math.floor(boss.bb.y0 - .02f);
        int bx = (int) Math.floor(boss.x), bz = (int) Math.floor(boss.z);
        while (surface < boss.level.depth - 1 && boss.level.isWater(bx, surface + 1, bz)) surface++;
        float waterY =
                boss.level.isWater(bx, surface, bz)
                        ? LiquidBlock.waterSurfaceAt(
                                boss.level, bx, surface, bz, boss.x - bx, boss.z - bz)
                        : boss.bb.y0 + 1.25f;
        float feet = waterY - (nearBank || shorePath.isEmpty() || boss.onGround ? .3f : .85f);
        if (!targetInWater && !shorePath.isEmpty() && !nearBank) {
            feet = Math.max(feet, shorePath.get(shoreIndex).feetY());
        }
        if (targetInWater) feet = Math.min(waterY - .55f, target.bb.y0);
        boss.yd = Math.max(-.15f, Math.min(.19f, (feet - boss.bb.y0) * .18f));
        return true;
    }

    private boolean climbBank(StickFigureAI ai, StickFigure boss) {
        float dx = bankExit.x() - boss.x, dz = bankExit.z() - boss.z;
        float distance = (float) Math.hypot(dx, dz);
        float rise = bankExit.feetY() - boss.bb.y0;
        if (distance < .15f && boss.onGround && !boss.isInWater()) return false;
        if (--bankExitTicks <= 0
                || !landingClear(boss, bankExit.x(), bankExit.feetY(), bankExit.z())) {
            blockedExit = bankExit;
            leapCooldown = 8;
            return false;
        }
        ai.xxa = ai.yya = ai.yRotA = 0;
        ai.jumping = false;
        face(boss, dx, dz, 24);
        if (rise > -.06f && distance > .15f) {
            boss.xd = boss.zd = 0;
            boss.yd = Math.min(.42f, rise + .09f);
        } else if (distance > .15f) {
            float step = Math.min(.23f, distance) / distance;
            boss.xd = dx * step;
            boss.zd = dz * step;
            boss.yd = 0;
        } else {
            boss.xd = boss.zd = 0;
        }
        return true;
    }

    private static void steer(StickFigureAI ai, StickFigure boss, float x, float z, float speed) {
        float dx = x - boss.x, dz = z - boss.z;
        float turn = face(boss, dx, dz, 24);
        ai.xxa = ai.yRotA = 0;
        ai.yya = ai.runSpeed * speed * Math.max(.15f, 1 - Math.abs(turn) / 120f);
        ai.jumping = false;
        if (Math.abs(turn) > 60) {
            boss.xd *= .6f;
            boss.zd *= .6f;
        }
    }

    private static float face(StickFigure boss, float dx, float dz, float limit) {
        if (dx * dx + dz * dz < .0001f)
            return 0;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float turn = (float) Math.IEEEremainder(yaw - boss.yRot, 360);
        boss.yRot += Math.max(-limit, Math.min(limit, turn));
        return turn;
    }

    private boolean findLeap(StickFigureAI ai, StickFigure boss, float tx, float ty, float tz) {
        double heading = Math.atan2(tz - boss.z, tx - boss.x);
        float best = score(boss.x, boss.bb.y0, boss.z, tx, ty, tz) - 1;
        float[] destination = null;
        for (int angle : new int[]{0, -30, 30, -60, 60}) {
            double direction = heading + Math.toRadians(angle);
            for (int distance : new int[]{3, 5, 7}) {
                float x = (float) Math.floor(boss.x + Math.cos(direction) * distance) + .5f;
                float z = (float) Math.floor(boss.z + Math.sin(direction) * distance) + .5f;
                for (int rise = 4; rise >= -2; rise--) {
                    float feet = (float) Math.floor(boss.bb.y0) + rise;
                    float value = score(x, feet, z, tx, ty, tz) + distance * .15f;
                    if (value >= best || !landingClear(boss, x, feet, z))
                        continue;
                    if (plan(boss, x, feet, z) != null) {
                        best = value;
                        destination = new float[]{x, feet, z};
                    }
                }
            }
        }
        return destination != null
                && launch(ai, boss, destination[0], destination[1], destination[2]);
    }

    private static float score(float x, float y, float z, float tx, float ty, float tz) {
        return (float) Math.hypot(x - tx, z - tz) + Math.abs(y - ty) * 2;
    }

    private boolean launch(StickFigureAI ai, StickFigure boss, float x, float feet, float z) {
        if (!landingClear(boss, x, feet, z))
            return false;
        List<Vec3D> path = plan(boss, x, feet, z);
        if (path == null)
            return false;
        leapPath = path;
        int duration = path.size() - 1;
        leapX = (x - boss.x) / duration;
        leapZ = (z - boss.z) / duration;
        boss.yd = path.get(1).y - boss.bb.y0;
        boss.xd = leapX;
        boss.zd = leapZ;
        boss.fallDistance = 0;
        leapAge = 1;
        leapTicks = duration + 5;
        leapCooldown = 35;
        ai.xxa = ai.yya = ai.yRotA = 0;
        ai.jumping = false;
        face(boss, leapX, leapZ, 180);
        return true;
    }

    private static AABB body(StickFigure boss, float x, float feet, float z) {
        float half = boss.bbWidth * .5f - .002f;
        return new AABB(
                x - half, feet + .002f, z - half, x + half, feet + boss.bbHeight - .002f, z + half);
    }

    private static boolean clear(Level level, AABB box) {
        if (!level.isInBounds(
                (int) Math.floor(box.x0),
                (int) Math.floor(box.y0),
                (int) Math.floor(box.z0))
                || !level.isInBounds(
                        (int) Math.floor(box.x1),
                        (int) Math.floor(box.y1),
                        (int) Math.floor(box.z1))
                || !level.getCubes(box).isEmpty()
                || level.containsLiquid(box, LiquidType.LAVA))
            return false;
        for (int x = (int) Math.floor(box.x0); x <= (int) Math.floor(box.x1); x++)
            for (int y = (int) Math.floor(box.y0); y <= (int) Math.floor(box.y1); y++)
                for (int z = (int) Math.floor(box.z0); z <= (int) Math.floor(box.z1); z++) {
                    int tile = level.getTile(x, y, z);
                    if (tile == Block.FIRE.id || tile == Block.CACTUS.id)
                        return false;
                }
        return true;
    }

    private static boolean landingClear(StickFigure boss, float x, float feet, float z) {
        AABB box = body(boss, x, feet, z);
        box.y1 = feet + StickFigure.STANDING_HEIGHT - .002f;
        int support = boss.level.getTile(
                (int) Math.floor(x), (int) Math.floor(feet - .01f), (int) Math.floor(z));
        return clear(boss.level, box)
                && support != Block.CACTUS.id
                && support != Block.FIRE.id
                && !boss.level.isWater((int) Math.floor(x), (int) Math.floor(feet - .01f), (int) Math.floor(z))
                && !boss.level
                        .getCubes(
                                new AABB(x - .15f, feet - .08f, z - .15f, x + .15f, feet, z + .15f))
                        .isEmpty();
    }

    private static List<Vec3D> plan(StickFigure boss, float x, float feet, float z) {
        float distance = (float) Math.hypot(x - boss.x, z - boss.z);
        if (distance < .5f || distance > 8 || feet - boss.bb.y0 > 4.1f) return null;
        boolean leavingWater = boss.isInWater();
        for (int duration : new int[] {16, 20, 24}) {
            if (distance / duration > .5f) continue;
            float low = .15f, high = 1.15f;
            for (int i = 0; i < 16; i++) {
                float speed = (low + high) * .5f;
                List<Vec3D> arc = arc(boss, x, z, duration, speed);
                if (arc.get(duration).y < feet + .02f) low = speed;
                else high = speed;
            }
            List<Vec3D> arc = arc(boss, x, z, duration, (low + high) * .5f);
            if (Math.abs(arc.get(duration).y - feet) > .06f
                    || arc.get(duration - 1).y <= arc.get(duration).y) continue;
            boolean valid = true;
            for (int i = 1; i < arc.size() && valid; i++) {
                Vec3D from = arc.get(i - 1), to = arc.get(i);
                for (int sample = 1; sample <= 4; sample++) {
                    float t = sample / 4f;
                    AABB sampleBody =
                            body(
                                    boss,
                                    from.x + (to.x - from.x) * t,
                                    from.y + (to.y - from.y) * t,
                                    from.z + (to.z - from.z) * t);
                    if (!clear(boss.level, sampleBody)
                            || !leavingWater
                                    && boss.level.containsLiquid(sampleBody, LiquidType.WATER)) {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid) return arc;
        }
        return null;
    }

    private static List<Vec3D> arc(
            StickFigure boss, float x, float z, int duration, float velocity) {
        List<Vec3D> path = new ArrayList<>();
        float feet = boss.bb.y0;
        path.add(new Vec3D(boss.x, feet, boss.z));
        for (int i = 1; i <= duration; i++) {
            Vec3D previous = path.get(i - 1);
            boolean water = boss.level.containsLiquid(
                    body(boss, previous.x, feet, previous.z).grow(0, -.4f, 0),
                    LiquidType.WATER);
            feet += velocity;
            velocity = water ? velocity * .8f - .02f : velocity * .98f - .08f;
            path.add(
                    new Vec3D(
                            boss.x + (x - boss.x) * i / duration,
                            feet,
                            boss.z + (z - boss.z) * i / duration));
        }
        return path;
    }
}
