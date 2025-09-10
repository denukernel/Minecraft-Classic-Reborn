// com/mojang/minecraft/mob/QuadrupedMob.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public abstract class QuadrupedMob extends Mob {

    public static final long serialVersionUID = 0L;

    public QuadrupedMob(Level level, float x, float y, float z) {
        super(level);

        // Generic quadruped body — wide but low
        this.setSize(1.4F, 1.2F);
        this.heightOffset = 0.90F;     // eye/center closer to back height
        this.footSize     = 0.50F;     // decent step-up without hovering

        this.setPos(x, y, z);
    }
}
