// File: src/com/mojang/minecraft/mob/Husk.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

/**
 * Desert variant of Zombie.
 * Same model, unique texture, immune to sunlight.
 */
public final class Husk extends Zombie {
    public static final long serialVersionUID = 0L;

    public Husk(Level level, float x, float y, float z) {
        super(level, x, y, z);
        this.modelName = "husk";          // reuse zombie model
        this.textureName = "/mob/husk.png"; // your husk texture
        // sound set defaults to zombie’s
        this.coinDrop = 2;
        // You can swap to husk-specific sounds if you add them later.
    }

    @Override
    protected boolean isSunImmune() {
        return true; // husks never burn in daylight
    }
}
