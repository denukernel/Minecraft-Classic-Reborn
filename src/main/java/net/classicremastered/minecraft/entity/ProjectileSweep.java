package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;

/** Moves a centered projectile to its first contact along the current velocity. */
public final class ProjectileSweep {
    public record Impact(Mob mob) {}

    private ProjectileSweep() {}

    public static Impact move(Entity projectile, Entity owner) {
        Vec3D start =
                new Vec3D(projectile.x, (projectile.bb.y0 + projectile.bb.y1) * .5f, projectile.z);
        Vec3D end = start.add(projectile.xd, projectile.yd, projectile.zd);
        AABB sweep = projectile.bb.expand(projectile.xd, projectile.yd, projectile.zd);
        float halfWidth = projectile.bbWidth * .5f - .0001f;
        float halfHeight = projectile.bbHeight * .5f - .0001f;
        float nearest = Float.POSITIVE_INFINITY;
        Mob victim = null;
        for (Object candidate : projectile.level.getCubes(sweep)) {
            nearest =
                    Math.min(
                            nearest,
                            distanceSquared((AABB) candidate, start, end, halfWidth, halfHeight));
        }
        for (Entity candidate : projectile.level.blockMap.getEntities(projectile, sweep)) {
            if (!(candidate instanceof Mob mob) || mob == owner || mob.removed || mob.health <= 0)
                continue;
            float distance = distanceSquared(mob.bb, start, end, halfWidth, halfHeight);
            if (distance < nearest) {
                nearest = distance;
                victim = mob;
            }
        }
        boolean hit = Float.isFinite(nearest);
        float fraction =
                !hit
                        ? 1f
                        : nearest == 0
                                ? 0f
                                : (float) Math.sqrt(nearest / start.distanceSquared(end));
        float dx = projectile.xd * fraction,
                dy = projectile.yd * fraction,
                dz = projectile.zd * fraction;
        projectile.bb.move(dx, dy, dz);
        projectile.x += dx;
        projectile.y += dy;
        projectile.z += dz;
        return hit ? new Impact(victim) : null;
    }

    private static float distanceSquared(
            AABB target, Vec3D start, Vec3D end, float halfWidth, float halfHeight) {
        AABB expanded = target.grow(halfWidth, halfHeight, halfWidth);
        if (expanded.contains(start)) return 0;
        var hit = expanded.clip(start, end);
        return hit == null ? Float.POSITIVE_INFINITY : start.distanceSquared(hit.vec);
    }
}
