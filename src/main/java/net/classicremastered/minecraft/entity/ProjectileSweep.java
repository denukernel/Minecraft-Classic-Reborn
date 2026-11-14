package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;

/** Moves a centered projectile to its first contact along the current velocity. */
public final class ProjectileSweep {
    public record Impact(Mob mob, Vec3D blockContact) {}

    private ProjectileSweep() {}

    public static Impact move(Entity projectile, Entity owner) {
        return move(projectile, owner, false);
    }

    static Impact moveAgainstBlocks(Entity projectile) {
        return move(projectile, null, true);
    }

    private static Impact move(Entity projectile, Entity owner, boolean blocksOnly) {
        Vec3D start =
                new Vec3D(projectile.x, (projectile.bb.y0 + projectile.bb.y1) * .5f, projectile.z);
        Vec3D end = start.add(projectile.xd, projectile.yd, projectile.zd);
        AABB sweep = projectile.bb.expand(projectile.xd, projectile.yd, projectile.zd);
        float inset = blocksOnly ? 0 : .0001f;
        float halfWidth = projectile.bbWidth * .5f - inset;
        float halfHeight = projectile.bbHeight * .5f - inset;
        float nearest = Float.POSITIVE_INFINITY;
        Mob victim = null;
        AABB block = null;
        AABB blockQuery = blocksOnly ? sweep.grow(.001f, .001f, .001f) : sweep;
        for (Object candidate : projectile.level.getCubes(blockQuery)) {
            AABB bounds = (AABB) candidate;
            float distance = distanceSquared(bounds, start, end, halfWidth, halfHeight);
            if (distance < nearest) {
                nearest = distance;
                block = bounds;
            }
        }
        if (!blocksOnly) {
            for (Entity candidate : projectile.level.blockMap.getEntities(projectile, sweep)) {
                if (!(candidate instanceof Mob mob)
                        || mob == owner
                        || mob.removed
                        || mob.health <= 0) continue;
                float distance = distanceSquared(mob.bb, start, end, halfWidth, halfHeight);
                if (distance < nearest) {
                    nearest = distance;
                    victim = mob;
                }
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
        if (!hit) return null;
        Vec3D contact =
                blocksOnly && block != null
                        ? new Vec3D(
                                clamp(start.x + dx, block.x0, block.x1),
                                clamp(start.y + dy, block.y0, block.y1),
                                clamp(start.z + dz, block.z0, block.z1))
                        : null;
        return new Impact(victim, contact);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float distanceSquared(
            AABB target, Vec3D start, Vec3D end, float halfWidth, float halfHeight) {
        AABB expanded = target.grow(halfWidth, halfHeight, halfWidth);
        if (expanded.contains(start)) return 0;
        var hit = expanded.clip(start, end);
        return hit == null ? Float.POSITIVE_INFINITY : start.distanceSquared(hit.vec);
    }
}
