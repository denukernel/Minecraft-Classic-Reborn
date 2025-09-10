// File: src/net/classicremastered/minecraft/mob/Bee.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.BasicAI;

public final class Bee extends Mob {

    public Bee(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);
        this.modelName = "bee"; // point to BeeModel in ModelManager
        this.textureName = "/mob/bee.png";
        this.ai = new BasicAI();
        this.health = 10;
    }

    @Override
    public boolean isBaby() {
        return false;
    }
}
