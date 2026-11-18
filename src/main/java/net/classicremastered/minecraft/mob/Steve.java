package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class Steve extends Mob {
    public static final short REGISTRY_ID = 91;
    public static final String CAVE_SOUND = "mob/steve/cave";

    public Steve(Level level, float x, float y, float z) {
        super(level);
        textureName = "/char.png";
        setSize(.6f, 1.8f);
        heightOffset = .9f;
        health = lastHealth = 20;
        equipmentRolled = true;
        ai = null;
        soundHurt = soundDeath = CAVE_SOUND;
        setPos(x, y, z);
    }

    @Override
    public void aiStep() {
        travel(0, 0);
    }
}
