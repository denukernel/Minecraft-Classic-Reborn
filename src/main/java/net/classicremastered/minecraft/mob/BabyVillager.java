package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

/** Smaller villager that uses BabyVillagerModel. */
public class BabyVillager extends Villager {

    private static final float BABY_SCALE = 0.70f; // model scale (see BabyVillagerModel)
    private static final float ADULT_H = 1.80f; // vanilla adult villager height
    private static final float BABY_H = ADULT_H * BABY_SCALE; // ~1.26
    private static final float BABY_W = 0.44f; // slimmer than adult width
    private static final float EYE_FACTOR = 0.9f; // eye at ~90% of body height

    public BabyVillager(Level level, float x, float y, float z) {
        super(level, x, y, z);

        this.modelName = "villager_baby";
        this.textureName = "/mob/villager.png";
        makeStepSound = false;
        coinDrop = -9;

        // Proper hitbox
        this.setSize(0.42F, 1.17F);
        this.heightOffset = 1.69F;
        this.setPos(x, y + 0.03F, z);

    }
}
