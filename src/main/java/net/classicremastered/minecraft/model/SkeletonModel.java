// File: src/com/mojang/minecraft/model/SkeletonModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.util.MathHelper;

public final class SkeletonModel extends ZombieModel {

    public SkeletonModel() {
        this.rightArm = new ModelPart(40, 16);
        this.rightArm.setBounds(-1.0F, -2.0F, -1.0F, 2, 12, 2, 0.0F);
        this.rightArm.setPosition(-5.0F, 2.0F, 0.0F);

        this.leftArm = new ModelPart(40, 16);
        this.leftArm.mirror = true;
        this.leftArm.setBounds(-1.0F, -2.0F, -1.0F, 2, 12, 2, 0.0F);
        this.leftArm.setPosition(5.0F, 2.0F, 0.0F);

        this.rightLeg = new ModelPart(0, 16);
        this.rightLeg.setBounds(-1.0F, 0.0F, -1.0F, 2, 12, 2, 0.0F);
        this.rightLeg.setPosition(-2.0F, 12.0F, 0.0F);

        this.leftLeg = new ModelPart(0, 16);
        this.leftLeg.mirror = true;
        this.leftLeg.setBounds(-1.0F, 0.0F, -1.0F, 2, 12, 2, 0.0F);
        this.leftLeg.setPosition(2.0F, 12.0F, 0.0F);
    }

    @Override
    public void preAnimate(Object entity) {
        super.preAnimate(entity);
        if (entity instanceof Skeleton) {
            Skeleton sk = (Skeleton) entity;
            if (sk.isDrawingBow()) {
                // Animate bow-draw arms
                float f = (float) sk.attackTime / (float) Skeleton.ATTACK_DURATION; // 0..1
                float swing = MathHelper.sin(f * (float)Math.PI);

                // Right arm swings forward as if pulling string
                this.rightArm.pitch = -1.0F - swing * 0.8F;
                this.rightArm.yaw   = -0.2F + swing * 0.2F;

                // Left arm holds bow steady
                this.leftArm.pitch = -0.6F + swing * 0.3F;
                this.leftArm.yaw   =  0.2F - swing * 0.2F;
            }
        }
    }
}
