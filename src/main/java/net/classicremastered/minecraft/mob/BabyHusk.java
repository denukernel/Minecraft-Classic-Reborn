package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class BabyHusk extends Husk {
    public static final short REGISTRY_ID = 11;

    public BabyHusk(Level level, float x, float y, float z) {
        super(level, x, y, z);
        BabyZombie.configureBaby(this, x, y, z);
        modelName = "husk_baby";
        textureName = "/mob/husk_baby.png";
    }

    @Override
    public boolean isBaby() {
        return true;
    }
}
