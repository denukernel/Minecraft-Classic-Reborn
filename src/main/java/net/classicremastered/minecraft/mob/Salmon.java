package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class Salmon extends Fish {
    public static final short REGISTRY_ID = 88;

    public Salmon(Level level, float x, float y, float z) {
        super(level, x, y, z, "salmon", .7f, .4f);
    }
}
