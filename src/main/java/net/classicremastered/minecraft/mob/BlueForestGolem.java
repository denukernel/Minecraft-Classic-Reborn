package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.golem.ForestGolemFollowAI;

/** A small passive forest companion, manually spawned by its registry name. */
public final class BlueForestGolem extends Mob {

    public static final short REGISTRY_ID = 61;
    public boolean persistent = true;

    public BlueForestGolem(Level level, float x, float y, float z) {
        super(level);
        modelName = "blue_forest_golem";
        textureName = "/mob/blue-forest-golem.png";
        setSize(0.85f, 1.5f);
        heightOffset = 0.75f;
        bobStrength = 0.25f;
        health = lastHealth = 20;
        ai = new ForestGolemFollowAI();
        setPos(x, y, z);
    }
}
