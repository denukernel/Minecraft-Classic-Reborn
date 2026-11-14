package net.classicremastered.minecraft.mob;

import java.util.List;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;

record TurretShot(Mob target, Vec3D muzzle, float yaw, float pitch) {
    private static final int MAX_FLIGHT_TICKS = 44;
    private static final float MAX_TARGET_SPEED = .75f;
    static final float MAX_LEAD_DISTANCE = MAX_TARGET_SPEED * MAX_FLIGHT_TICKS;

    static TurretShot aim(CrossbowTurret turret, Mob target, Vec3D muzzle, Vec3D center, boolean lead,
            List<Entity> nearby) {
        float vx = lead ? limit(target.x - target.xo) : 0f;
        float vy = lead && !target.onGround ? limit(target.y - target.yo) : 0f;
        float vz = lead ? limit(target.z - target.zo) : 0f;
        Vec3D offset = center.subtract(muzzle);
        float speed = turret.projectileSpeed();
        float drag = 1f;
        float travel = 0f;
        float fallSpeed = 0f;
        float drop = 0f;
        if (muzzle.distanceSquared(center) < .000001f)
            return null;
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            drag *= Arrow.FLIGHT_DRAG;
            fallSpeed = fallSpeed * Arrow.FLIGHT_DRAG + Arrow.TURRET_DROP;
            // Targets move before projectiles. Their hitbox holds still for this entire step.
            float leadY = Math.max(-1.5f, Math.min(1.5f, vy * tick));
            Vec3D startOffset = offset.add(vx * tick, leadY + drop, vz * tick);
            travel += drag;
            drop += fallSpeed;
            Vec3D endOffset = startOffset.add(0f, fallSpeed, 0f);
            float startGap = length(startOffset) - speed * (travel - drag);
            float endGap = length(endOffset) - speed * travel;
            if (startGap < -Arrow.HALF_WIDTH || endGap > Arrow.HALF_WIDTH)
                continue;
            float fraction = Math.max(0f, Math.min(1f, startGap / (startGap - endGap)));
            float time = tick - 1f + fraction;
            Vec3D direction = startOffset.add(0f, fallSpeed * fraction, 0f).normalize();
            if (!clear(turret, muzzle, direction, speed, time, nearby))
                continue;
            float yaw = (float) Math.toDegrees(Math.atan2(direction.x, -direction.z));
            float pitch = (float) -Math
                    .toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
            return new TurretShot(target, muzzle, yaw, pitch);
        }
        return null;
    }

    private static float length(Vec3D value) {
        return (float) Math.sqrt(value.x * value.x + value.y * value.y + value.z * value.z);
    }

    private static float limit(float velocity) {
        return Math.max(-MAX_TARGET_SPEED, Math.min(MAX_TARGET_SPEED, velocity));
    }

    private static boolean clear(CrossbowTurret turret, Vec3D start, Vec3D direction, float speed,
            float time, List<Entity> nearby) {
        float dx = direction.x * speed;
        float dy = direction.y * speed;
        float dz = direction.z * speed;
        for (int tick = 0; tick < time; tick++) {
            dx *= Arrow.FLIGHT_DRAG;
            dy = dy * Arrow.FLIGHT_DRAG - Arrow.TURRET_DROP;
            dz *= Arrow.FLIGHT_DRAG;
            float step = Math.min(1f, time - tick);
            Vec3D end = start.add(dx * step, dy * step, dz * step);
            AABB sweep = new AABB(start.x, start.y, start.z, start.x, start.y, start.z)
                    .grow(Arrow.HALF_WIDTH, Arrow.HALF_HEIGHT, Arrow.HALF_WIDTH)
                    .expand(end.x - start.x, end.y - start.y, end.z - start.z);
            for (Object collision : turret.level.getCubes(sweep)) {
                if (intersects((AABB) collision, start, end))
                    return false;
            }
            for (Entity entity : nearby) {
                if (entity != turret.riding
                        && !entity.removed
                        && entity.isShootable()
                        && (!(entity instanceof Mob mob) || mob.health > 0)
                        && !turret.canTarget(entity)
                        && sweep.intersectsInner(entity.bb)
                        && intersects(entity.bb, start, end)) return false;
            }
            start = end;
        }
        return true;
    }

    private static boolean intersects(AABB box, Vec3D start, Vec3D end) {
        AABB expanded = box.grow(Arrow.HALF_WIDTH, Arrow.HALF_HEIGHT, Arrow.HALF_WIDTH);
        return expanded.contains(start) || expanded.clip(start, end) != null;
    }
}
