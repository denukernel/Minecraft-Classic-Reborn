package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

/** Faster, smaller Zombie that uses BabyZombieModel. */
public class BabyZombie extends Zombie {

    // Keep these in one place so model + hitbox stay in sync
    private static final float BABY_SCALE = 0.70f; // matches BabyZombieModel
    private static final float ADULT_H = 1.80f;
    private static final float BABY_H = ADULT_H * BABY_SCALE; // ≈ 1.26
    private static final float BABY_W = 0.44f; // a bit slimmer than adult
    private static final float EYE_FACTOR = 0.85f; // like adult (~0.9), a bit lower for kids

    @Override
    public boolean isBaby() {
        return true;
    }

    public BabyZombie(Level level, float x, float y, float z) {
        super(level, x, y, z);

        // Use baby model (see ModelManager)
        this.modelName = "zombie_baby";
        this.textureName = "/mob/zombie.png"; // reuse texture

        // Hitbox that matches the scaled render
        this.setSize(BABY_W, BABY_H);
        this.heightOffset = 1.69F;
        this.idlePitchMin = 1.5F; this.idlePitchMax = 1.5F;

        this.hurtPitchMin = 1.5F; this.hurtPitchMax = 1.5F;
        this.deathPitchMin= 1.5F; this.deathPitchMax= 1.5F;
        // Optional: tiny upward nudge on spawn to avoid uneven-ground clipping
        this.setPos(x, y + 0.03F, z);

        // Speed boost if AI is BasicAttackAI
        if (this.ai instanceof net.classicremastered.minecraft.mob.ai.BasicAttackAI) {
            net.classicremastered.minecraft.mob.ai.BasicAttackAI a = (net.classicremastered.minecraft.mob.ai.BasicAttackAI) this.ai;
            a.runSpeed *= 1.35F; // ~35% faster
        }

    }
}
