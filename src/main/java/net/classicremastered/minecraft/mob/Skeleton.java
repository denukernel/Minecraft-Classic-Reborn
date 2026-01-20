// File: src/com/mojang/minecraft/mob/Skeleton.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public class Skeleton extends Zombie {
    public static final long serialVersionUID = 0L;

    private boolean drawingBow = false; // <-- new flag

    public Skeleton(Level level, float x, float y, float z) {
        super(level, x, y, z);
        this.modelName   = "skeleton";
        this.textureName = "/mob/skeleton.png";
        this.ai          = new Skeleton$1(this); // use skeleton AI
        this.soundIdle  = "mob/skeleton/say";
        this.soundHurt  = "mob/skeleton/hurt";
        this.soundDeath = "mob/skeleton/death";
        this.coinDrop = 2;
    }

    // === Bow state for model animation ===
    public void setDrawingBow(boolean flag) {
        this.drawingBow = flag;
    }

    public boolean isDrawingBow() {
        return this.drawingBow;
    }

    @Override
    public void tick() {
        super.tick();
        // Clear drawing flag if attack cooldown expired
        if (this.attackTime <= 0) {
            this.drawingBow = false;
        }
    }

    // Called from AI when actually firing
    public void shootArrow(Level lvl) {
        // mark animation flag while shooting
        this.setDrawingBow(true);

        // Keep vanilla/classic arrow firing logic
        lvl.addEntity(new net.classicremastered.minecraft.entity.Arrow(
            lvl, this,
            this.x, this.y, this.z,
            this.yRot + 180.0F + (float)(Math.random() * 45.0D - 22.5D),
            this.xRot - (float)(Math.random() * 45.0D - 10.0D),
            1.0F
        ));
    }
}
