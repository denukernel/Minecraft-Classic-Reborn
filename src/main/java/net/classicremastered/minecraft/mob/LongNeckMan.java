package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;

public final class LongNeckMan extends Mob {

    public static final long serialVersionUID = 0L;

    public LongNeckMan(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);
        this.modelName = "longneckman";
        this.textureName = "/mob/long_neck_man.png";
        this.heightOffset = 1.62F;
        this.ai = new BasicAttackAI(true, true);
        this.setSize(0.8F, 2.2F);
        this.health = 20;
        this.speed = 0.25F;
        this.coinDrop = 3;
        this.soundIdle = "mob/zombie/say";
        this.soundHurt = "mob/zombie/hurt";
        this.soundDeath = "mob/zombie/death";
    }
}
