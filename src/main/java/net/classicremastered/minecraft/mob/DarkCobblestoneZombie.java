package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class DarkCobblestoneZombie extends Zombie {
    private static final long serialVersionUID = 0L;
    public static final short REGISTRY_ID = 92;
    public static final int MAX_HEALTH = 26;

    public DarkCobblestoneZombie(Level level, float x, float y, float z) {
        super(level, x, y, z);
        textureName = "/mob/dark_cobblestone_zombie.png";
        health = lastHealth = MAX_HEALTH;
        setVoicePitch(.62f);
        helmet = armor = legacyArmor = hasHair = false;
        equipmentRolled = true;
        setPos(x, y, z);
    }

    @Override
    protected int healthLimit() {
        return MAX_HEALTH;
    }

    @Override
    public void heal(int amount) {
        if (health > 0) {
            health = Math.min(health + amount, MAX_HEALTH);
            invulnerableTime = invulnerableDuration / 2;
        }
    }
}
