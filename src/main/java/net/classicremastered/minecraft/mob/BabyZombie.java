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

        configureBaby(this, x, y, z);
    }

    static void configureBaby(Zombie baby, float x, float y, float z) {
        // Hitbox that matches the scaled render
        baby.setSize(BABY_W, BABY_H);
        baby.heightOffset = 1.69F;
        baby.idlePitchMin = 1.5F;
        baby.idlePitchMax = 1.5F;

        baby.hurtPitchMin = 1.5F;
        baby.hurtPitchMax = 1.5F;
        baby.deathPitchMin = 1.5F;
        baby.deathPitchMax = 1.5F;
        // Optional: tiny upward nudge on spawn to avoid uneven-ground clipping
        baby.setPos(x, y + 0.03F, z);

        // Speed boost if AI is BasicAttackAI
        if (baby.ai instanceof net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI) {
            net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI a = (net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI) baby.ai;
            a.runSpeed *= 1.35F; // ~35% faster
        }

    }
}
