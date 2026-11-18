package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.BasicAI;

public class Rana extends Mob {

    public Rana(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);
        this.textureName = "/mob/cube-nes.png";

        this.allowAlpha  = true;
        this.hasHair     = true;

        this.heightOffset       = 0.0F;   // no humanoid eye lift

        this.ai         = new BasicAI();
        this.health     = 20;
        this.deathScore = 10;
    }

    public String getName() { return "Rana"; }
}