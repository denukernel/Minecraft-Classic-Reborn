// com/mojang/minecraft/mob/Spider.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public class Spider extends QuadrupedMob {

    public static final long serialVersionUID = 0L;

    private float stepDistance;

    public Spider(Level level, float x, float y, float z) {
        super(level, x, y, z);

        this.modelName = "spider";
        this.textureName = "/mob/spider.png";
        this.setSize(1.4F, 0.9F);

        this.heightOffset = 0.0F;
        this.renderOffset = 0.0F;
        this.coinDrop = 2;
        this.bobStrength = 0.0F;
        this.deathScore = 105;
        this.footSize = 0.45F;
        this.seatFactor = 0.60f;
        this.health = this.lastHealth = 16;
        this.soundIdle = "mob/spider/say";
        this.soundHurt = "mob/spider/say";
        this.soundDeath = "mob/spider/death";

        // Beta-style spider AI (neutral in light, hostile in dark/provoked).
        // Difficulty-based poison/paralysis is handled inside BetaSpiderAI.attack().
        this.ai = new net.classicremastered.minecraft.mob.ai.spider.BetaSpiderAI();

        // Ground speed tuning
        ((net.classicremastered.minecraft.mob.ai.BasicAI) this.ai).runSpeed = 1.458F;

        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        float previousX = x, previousZ = z;
        super.tick();

        // Hug slopes visually
        this.renderOffset = this.ySlideOffset;

        // Lightweight wall-climb feel
        if (health > 0 && this.horizontalCollision && !isInWater() && this.yd < 0.20F) {
            this.yd = 0.20F;
            this.fallDistance = 0;
        }
        if (health > 0 && onGround && level != null) {
            float dx = x - previousX, dz = z - previousZ;
            stepDistance += (float) Math.sqrt(dx * dx + dz * dz);
            if (stepDistance >= 1.2f) {
                stepDistance = 0;
                if (level.minecraft == null
                        || level.minecraft.settings == null
                        || level.minecraft.settings.allowMobSounds)
                    level.playSound("mob/spider/step", this, 0.15f, 1.0f);
            }
        }
    }
}
