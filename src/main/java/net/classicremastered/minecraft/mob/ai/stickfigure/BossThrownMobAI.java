package net.classicremastered.minecraft.mob.ai.stickfigure;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.AI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Temporary ballistic motion for an actual passive mob, with its normal AI restored on landing. */
public final class BossThrownMobAI extends AI {
    private static final float GRAVITY = .045f;
    private static final float SPEED = 1.15f;
    private final AI original;
    private final Mob owner;
    private final Entity target;
    private float verticalSpeed;
    private int age;

    public BossThrownMobAI(Mob mob, Mob owner, Entity target) {
        original = mob.ai;
        this.owner = owner;
        this.target = target;
        float dx = target.x - mob.x;
        float dz = target.z - mob.z;
        float lead = Math.min(12f, (float) Math.hypot(dx, dz) / SPEED);
        dx += lead * Math.max(-.25f, Math.min(.25f, target.xd));
        dz += lead * Math.max(-.25f, Math.min(.25f, target.zd));
        int flightTicks = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / SPEED));
        float dy = centerY(target) - centerY(mob);
        mob.xd = dx / flightTicks;
        mob.zd = dz / flightTicks;
        mob.yd = verticalSpeed = dy / flightTicks + GRAVITY * (flightTicks - 1) * .5f;
        mob.fallDistance = 0;
        mob.markForcedFall(owner);
    }

    @Override
    public void tick(Level level, Mob mob) {
        if (mob.health <= 0 || owner.removed || owner.health <= 0 || owner.level != level) {
            mob.ai = original;
            return;
        }
        mob.yd = verticalSpeed;
        Vec3D start = new Vec3D(mob.x, centerY(mob), mob.z);
        Vec3D end = start.add(mob.xd, mob.yd, mob.zd);
        float worldDistance = Float.POSITIVE_INFINITY;
        for (Object cube : level.getCubes(mob.bb.expand(mob.xd, mob.yd, mob.zd))) {
            worldDistance = Math.min(worldDistance, hitDistance(mob, (AABB) cube, start, end));
        }
        float targetDistance =
                !target.removed
                                && target.level == level
                                && !CreativeModeHelper.shouldIgnoreTarget(target)
                        ? hitDistance(mob, target.bb, start, end)
                        : Float.POSITIVE_INFINITY;
        if (targetDistance < worldDistance) {
            float fraction = targetDistance == 0 ? 0 : targetDistance / start.distance(end);
            mob.move(mob.xd * fraction, mob.yd * fraction, mob.zd * fraction);
            int previousHealth = target instanceof Mob victim ? victim.health : 0;
            target.hurt(owner, 4);
            if (target instanceof Mob victim
                    && victim.health > 0
                    && victim.health < previousHealth) {
                victim.markForcedFall(owner);
            }
            mob.ai = original;
            mob.xd *= .2f;
            mob.zd *= .2f;
            return;
        }
        boolean blocked = worldDistance < Float.POSITIVE_INFINITY;
        float fraction = 1;
        if (blocked) {
            fraction =
                    worldDistance == 0
                            ? 0
                            : Math.max(0, worldDistance - .001f) / start.distance(end);
        }
        mob.move(mob.xd * fraction, mob.yd * fraction, mob.zd * fraction);
        mob.fallDistance = 0;
        verticalSpeed -= GRAVITY;
        mob.yd = verticalSpeed;
        if (++age > 80 || mob.collision || blocked) {
            if (mob.collision || blocked) mob.xd = mob.yd = mob.zd = 0;
            mob.ai = original;
        }
    }

    private static float centerY(Entity entity) {
        return (entity.bb.y0 + entity.bb.y1) * .5f;
    }

    private static float hitDistance(Mob mob, AABB box, Vec3D start, Vec3D end) {
        if (mob.bb.intersects(box)) return 0;
        // Leave a small gap so resting on a surface does not block upward launch.
        float halfWidth = mob.bbWidth * .5f - .0001f;
        float halfHeight = mob.bbHeight * .5f - .0001f;
        var hit = box.grow(halfWidth, halfHeight, halfWidth).clip(start, end);
        return hit == null ? Float.POSITIVE_INFINITY : start.distance(hit.vec);
    }
}
