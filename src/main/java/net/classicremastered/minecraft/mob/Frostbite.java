package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public class Frostbite extends Zombie {
    public static final short REGISTRY_ID = 9;
    public static final int MAX_HEALTH = 23;

    public Frostbite(Level level, float x, float y, float z) {
        super(level, x, y, z);
        modelName = "frostbite";
        textureName = "/mob/frostbite.png";
        health = lastHealth = MAX_HEALTH;
    }

    @Override
    public void heal(int amount) {
        if (health > 0) {
            health = Math.min(MAX_HEALTH, health + amount);
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
