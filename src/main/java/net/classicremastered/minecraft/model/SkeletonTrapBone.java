package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.phys.AABB;

/** Shared scatter positions and picking bounds in model pixels. */
public enum SkeletonTrapBone {
    HEAD("head", 3, -3, -4, -8, -4, 8, 8, 8),
    BODY("body", -2, 0, -4, 0, -2, 8, 12, 4),
    RIGHT_ARM("right_arm", -6, -1, -6, 0, -1, 2, 12, 2),
    LEFT_ARM("left_arm", 6, 2, 4, 0, -1, 2, 12, 2),
    RIGHT_LEG("right_leg", -5, 3, -3, 12, -1, 2, 12, 2),
    LEFT_LEG("left_leg", 5, 4, 1, 12, -1, 2, 12, 2);

    public static final float SCALE = 1f / 16f;
    public static final float GROUND_OFFSET = 24f;
    public static final float FALL_OFFSET_Y = 20f;
    public static final float FALL_OFFSET_Z = -9f;
    public static final float FALL_DEPTH = .76f;

    public final String partName;
    public final float scatterX, scatterY;
    private final AABB bounds;

    SkeletonTrapBone(
            String partName,
            float scatterX,
            float scatterY,
            float x,
            float y,
            float z,
            float width,
            float height,
            float depth) {
        this.partName = partName;
        this.scatterX = scatterX;
        this.scatterY = scatterY;
        x += scatterX;
        y += scatterY;
        bounds = new AABB(x, y, z, x + width, y + height, z + depth).grow(.35f, .35f, .35f);
    }

    public Vec3D intersect(Vec3D start, Vec3D end) {
        var hit = bounds.clip(start, end);
        return hit == null ? null : hit.vec;
    }

    public int mask() {
        return 1 << ordinal();
    }
}
