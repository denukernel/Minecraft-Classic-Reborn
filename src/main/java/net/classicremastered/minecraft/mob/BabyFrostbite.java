package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class BabyFrostbite extends Frostbite {
    public static final short REGISTRY_ID = 10;

    public BabyFrostbite(Level level, float x, float y, float z) {
        super(level, x, y, z);
        BabyZombie.configureBaby(this, x, y, z);
        modelName = "frostbite_baby";
        textureName = "/mob/frostbite_baby.png";
    }

    @Override
    public boolean isBaby() {
        return true;
    }
}
