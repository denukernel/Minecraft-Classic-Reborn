package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.BasicAI;

public class Rana extends Mob {

    public Rana(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);

        this.modelName   = "/test2.md3";
        this.textureName = "/mob/cube-nes.png";

        this.allowAlpha  = true;
        this.hasHair     = true;

        // MD3 grounding: keep the model on the floor
        this.heightOffset       = 0.0F;   // no humanoid eye lift
        this.modelGroundOffset  = 1.5F;  // tune 11.5–13.5 to taste

        this.ai         = new BasicAI();
        this.health     = 20;
        this.deathScore = 10;
    }

    public String getName() { return "Rana"; }
}