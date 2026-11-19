package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;

final class BuilderPlacement {
    private static final float REACH_SQUARED = 16;
    private static final float[][] FACE_SAMPLES = {{0, 0}, {0, .38f}, {0, -.38f}, {.32f, 0}, {-.32f, 0}};

    private BuilderPlacement() {
    }

    record ScaffoldColumn(int x, int baseY, int z, int goalY) {
        Vec3D standAt(int y) {
            return new Vec3D(x + .5f, y, z + .5f);
        }
    }

    static boolean onScaffold(Builder builder) {
        int x = (int) Math.floor(builder.x), y = (int) Math.floor(builder.bb.y0);
        int z = (int) Math.floor(builder.z);
        return scaffoldAt(builder.level, new Pos(x, y, z)) || scaffoldAt(builder.level, new Pos(x, y - 1, z));
    }

    static boolean scaffoldAt(Level level, Pos pos) {
        return Block.BUILDER_SCAFFOLD != null && level != null
                && level.getTile(pos.x(), pos.y(), pos.z()) == Block.BUILDER_SCAFFOLD.id;
    }

    static boolean canClimbTo(Builder builder, Vec3D stand) {
        int x = (int) Math.floor(stand.x), z = (int) Math.floor(stand.z);
        if ((int) Math.floor(builder.x) != x || (int) Math.floor(builder.z) != z)
            return false;
        int low = (int) Math.floor(Math.min(builder.bb.y0, stand.y));
        int high = (int) Math.ceil(Math.max(builder.bb.y0, stand.y));
        for (int y = low; y < high; y++) {
            if (!scaffoldAt(builder.level, new Pos(x, y, z)))
                return false;
        }
        return true;
    }

    static Vec3D center(Pos pos) {
        return new Vec3D(pos.x() + .5f, pos.y() + .5f, pos.z() + .5f);
    }

    static Vec3D face(Level level, Vec3D from, Pos target, boolean placingScaffold) {
        if (from.distanceSquared(center(target)) > 25)
            return null;
        for (int side = 0; side < 6; side++) {
            Pos support = target.step(side);
            if (!level.isSolidTile(support.x(), support.y(), support.z())
                    && !(placingScaffold && side == 0 && scaffoldAt(level, support)))
                continue;
            Vec3D inside = center(target).add((support.x() - target.x()) * .501f,
                    (support.y() - target.y()) * .501f, (support.z() - target.z()) * .501f);
            for (float[] sample : FACE_SAMPLES) {
                Vec3D point = switch (side) {
                    case 0, 1 -> inside.add(sample[0], 0, sample[1]);
                    case 2, 3 -> inside.add(sample[0], sample[1], 0);
                    default -> inside.add(0, sample[1], sample[0]);
                };
                var hit = level.clip(from.add(0, 0, 0), point, !placingScaffold);
                if (hit != null && hit.x == support.x() && hit.y == support.y() && hit.z == support.z()
                        && hit.face == (side ^ 1) && from.distanceSquared(hit.vec) <= REACH_SQUARED) {
                    return hit.vec;
                }
            }
        }
        return null;
    }

    static boolean withinBreakReach(Level level, Vec3D from, Pos pos) {
        Vec3D target = center(pos);
        return from.distanceSquared(target) <= REACH_SQUARED
                && level.clip(from.add(0, 0, 0), target, true) == null;
    }

    static boolean occupied(Level level, Pos pos) {
        AABB space = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1);
        for (Object entity : level.findEntities(null, space)) {
            if (entity instanceof Mob mob && !mob.removed && mob.health > 0)
                return true;
        }
        return false;
    }

    static boolean inWay(Builder builder, Vec3D feet, Pos target) {
        float half = builder.bbWidth * .5f;
        return feet.y < target.y() + 1 && feet.y + builder.bbHeight > target.y() && feet.x + half > target.x()
                && feet.x - half < target.x() + 1 && feet.z + half > target.z()
                && feet.z - half < target.z() + 1;
    }
}
