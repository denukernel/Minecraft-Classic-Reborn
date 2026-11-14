package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class TeenSkeleton extends Skeleton {
    public static final short REGISTRY_ID = 82;

    public TeenSkeleton(Level level, float x, float y, float z) {
        super(level, x, y, z);
        TeenZombie.configureTeen(this, "skeleton_teen");
    }

    @Override
    protected float modelScale() {
        return TeenZombie.SCALE;
    }

    @Override
    protected double maxTargetLeadSpeed() {
        return .42;
    }
}
