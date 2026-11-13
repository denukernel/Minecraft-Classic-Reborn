package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class HuskIgniter extends ZombieIgniter {
    private static final long serialVersionUID = 0L;
    public static final short REGISTRY_ID = 80;

    public HuskIgniter(Level level, float x, float y, float z) {
        super(level, x, y, z);
        modelName = "husk";
        textureName = "/mob/husk.png";
        coinDrop = 2;
    }

    @Override
    protected boolean isSunImmune() {
        return true;
    }
}
