// com/mojang/minecraft/mob/Spider.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public class Spider extends QuadrupedMob {
    public static final long serialVersionUID = 0L;

    private int sunExposeTicks = 0; // 200 ticks ≈ 10s @20TPS

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

        // Beta-style spider AI (neutral in light, hostile in dark/provoked).
        // Difficulty-based poison/paralysis is handled inside BetaSpiderAI.attack().
        this.ai = new net.classicremastered.minecraft.mob.ai.BetaSpiderAI();

        // Ground speed tuning
        ((net.classicremastered.minecraft.mob.ai.BasicAI) this.ai).runSpeed = 1.458F;

        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();

        // Hug slopes visually
        this.renderOffset = this.ySlideOffset;

        // Lightweight wall-climb feel
        if (this.horizontalCollision && !this.onGround && this.yd < 0.10F) {
            this.yd = 0.10F;
        }

        // Sunlight burn/vanish
        if (this.level != null && this.level.shouldUndeadBurnAt(this)) {
            if (++sunExposeTicks >= 200) {
                this.remove();
                return;
            }
        } else {
            sunExposeTicks = 0;
        }
    }
}
