package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

/** Smaller villager that uses BabyVillagerModel. */
public class BabyVillager extends Villager {

    private static final float BABY_SCALE  = 0.70f;     // model scale (see BabyVillagerModel)
    private static final float ADULT_H     = 1.80f;     // vanilla adult villager height
    private static final float BABY_H      = ADULT_H * BABY_SCALE; // ~1.26
    private static final float BABY_W      = 0.44f;     // slimmer than adult width
    private static final float EYE_FACTOR  = 0.9f;      // eye at ~90% of body height

    public BabyVillager(Level level, float x, float y, float z) {
        super(level, x, y, z);

        this.modelName   = "villager_baby";
        this.textureName = "/mob/villager.png";
        // === Alpha-like sound palette ===
        // Idle = rare groans, using same sound pool as hurt but pitched low
        this.soundIdle  = "random/classic_hurt";
        this.idlePitchMin = 1.45F; this.idlePitchMax = 1.65F;
        this.coinDrop = -9;

        // Hurt = low pitched "ugh" (steve grunt reused)
        this.soundHurt  = "random/classic_hurt";
        this.hurtPitchMin = 1.4F; this.hurtPitchMax = 1.5F;

        // Death = slightly louder, deeper grunt
        this.soundDeath = "random/classic_hurt";
        this.deathPitchMin = 1.35F; this.deathPitchMax = 1.45F;

        // Proper hitbox
        this.setSize(0.42F, 1.17F);
        this.heightOffset = 1.69F;
        this.setPos(x, y + 0.03F, z);

    }
}
