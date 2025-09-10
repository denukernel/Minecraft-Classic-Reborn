// file: src/com/mojang/minecraft/mob/TNTThrower.java
package net.classicremastered.minecraft.mob;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.ThrowTntAI;

public class TNTThrower extends HumanoidMob {

    public static final long serialVersionUID = 0L;
    private Random random;

    public TNTThrower(Level level, float x, float y, float z) {
        super(level, x, y, z);
        if (this.random == null) this.random = new Random();

        this.heightOffset = 1.62F;
        this.modelName    = "tntthrower";
        this.textureName  = "/mob/tntthrower.png";
        this.deathScore   = 250;
        this.setSize(0.6F, 1.8F);
        this.setPos(x, y, z);

        ThrowTntAI ai = new ThrowTntAI();
        ai.preferredMin   = 4.0F;
        ai.preferredMax   = 12.0F;
        ai.walkSpeed      = 0.02F;
        ai.windupTicks    = 6;
        ai.cooldownTicks  = 36;
        ai.throwPower     = 0.90F;
        ai.fuseTicks      = 36;
        this.ai = ai;
    }
}
