package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class Cod extends Fish {
    public static final short REGISTRY_ID = 87;

    public Cod(Level level, float x, float y, float z) {
        super(level, x, y, z, "cod", .5f, .3f);
    }
}
