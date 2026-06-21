package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.SmartHostileAI;

public final class LeggedMonster extends Mob {
    public static final long serialVersionUID = 0L;

    public LeggedMonster(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);
        this.modelName = "leggedmonster";
        this.textureName = "/mob/legged_monster.png";
        this.heightOffset = 1.62F;
        this.ai = new SmartHostileAI();
        this.setSize(0.8F, 1.8F);
        this.health = 20;
        this.speed = 0.25F;
        this.coinDrop = 3;
        this.soundIdle = "mob/zombie/say";
        this.soundHurt = "mob/zombie/hurt";
        this.soundDeath = "mob/zombie/death";
    }
}
