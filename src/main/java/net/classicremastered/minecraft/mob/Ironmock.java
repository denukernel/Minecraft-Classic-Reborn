package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.ironmock.IronmockAI;

public final class Ironmock extends Mob {

    public static final short REGISTRY_ID = 62;
    public Ironmock(Level level, float x, float y, float z) {
        super(level);
        modelName = "ironmock";
        textureName = "/mob/ironmock.png";
        setSize(.8f, 1.5f);
        heightOffset = .75f;
        bobStrength = 0;
        health = lastHealth = 40;
        coinDrop = 2;
        soundIdle = "random/anvil_use";
        soundHurt = "random/anvil_land";
        soundDeath = "random/anvil_break";
        ai = new IronmockAI();
        setPos(x, y, z);
    }
}
