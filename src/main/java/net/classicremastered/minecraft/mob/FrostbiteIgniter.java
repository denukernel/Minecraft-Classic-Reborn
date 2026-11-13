package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class FrostbiteIgniter extends ZombieIgniter {
    private static final long serialVersionUID = 0L;
    public static final short REGISTRY_ID = 79;

    public FrostbiteIgniter(Level level, float x, float y, float z) {
        super(level, x, y, z);
        modelName = "frostbite";
        textureName = "/mob/frostbite.png";
        health = lastHealth = Frostbite.MAX_HEALTH;
    }

    @Override
    public void heal(int amount) {
        if (health > 0) {
            health = Math.min(Frostbite.MAX_HEALTH, health + amount);
            invulnerableTime = invulnerableDuration / 2;
        }
    }

    @Override
    public void tick() {
        if (!level.isNetworkMode() && health > 0 && level.isDaytime()) {
            remove();
            return;
        }
        super.tick();
    }
}
