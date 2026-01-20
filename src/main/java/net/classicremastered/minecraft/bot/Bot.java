package net.classicremastered.minecraft.bot;

import net.classicremastered.minecraft.bot.ai.BotInputFollow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.HumanoidMob;

public class Bot extends HumanoidMob {

    public Bot(Level level, float x, float y, float z) {
        super(level, x, y, z);
        this.setPos(x, y, z);

        this.modelName   = "humanoid";   // reuse player model
        this.textureName = "/char.png";  // reuse player skin (or custom skin path)
        this.allowAlpha  = true;

        // stats like player
        this.health = 20;
        this.lastHealth = this.health;

        // same sounds as player
        this.soundHurt  = "random/classic_hurt";
        this.soundDeath = "random/classic_hurt";

        // make sure bots NEVER render armor
        this.helmet = false; // override HumanoidMob random value
        this.armor  = false; // override HumanoidMob random value

        // AI
        this.ai = new BotInputFollow(this);
    }
}
