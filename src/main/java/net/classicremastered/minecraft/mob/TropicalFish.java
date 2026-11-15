package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class TropicalFish extends Fish {
    public static final short REGISTRY_ID = 89;

    public TropicalFish(Level level, float x, float y, float z) {
        super(level, x, y, z, "tropical_fish", .45f, .45f);
    }
}
