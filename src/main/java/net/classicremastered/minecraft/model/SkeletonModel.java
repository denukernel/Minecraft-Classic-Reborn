// File: src/com/mojang/minecraft/model/SkeletonModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.util.MathHelper;

public class SkeletonModel extends ZombieModel {

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

    private Skeleton skeleton;
    private net.classicremastered.minecraft.render.TextureManager renderTextures;

    public void setRenderTextures(net.classicremastered.minecraft.render.TextureManager textures) {
        renderTextures = textures;
    }

    @Override
    public void preAnimate(Object entity) {
        super.preAnimate(entity);
        skeleton = entity instanceof Skeleton s ? s : null;
    }

    @Override
    public void setRotationAngles(
            float limb, float amount, float age, float yaw, float pitch, float scale) {
        super.setRotationAngles(limb, amount, age, yaw, pitch, scale);
        rightArm.yaw = leftArm.yaw = 0;
        rightArm.roll = leftArm.roll = 0;
        rightArm.pitch = MathHelper.cos(limb * .6662f + (float) Math.PI) * 1.4f * amount;
        leftArm.pitch = MathHelper.cos(limb * .6662f) * 1.4f * amount;
        if (skeleton != null) {
            float aimYaw = (skeleton.getBowAimYaw() - skeleton.yBodyRot) / 57.295776f;
            float aimPitch = skeleton.getBowAimPitch() / 57.295776f;
            BowPose.apply(this, aimYaw, aimPitch, skeleton.isDrawingBow());
        }
    }

    @Override
    public void render(float limb, float amount, float age, float yaw, float pitch, float scale) {
        renderBody(limb, amount, age, yaw, pitch, scale);
        if (skeleton == null || renderTextures == null) return;
        net.classicremastered.minecraft.render.HandItemRenderer.renderBow(
                rightArm,
                renderTextures,
                skeleton.getBowTexture(),
                scale,
                skeleton.getRenderBrightness(1),
                1);
    }

    protected void renderBody(
            float limb, float amount, float age, float yaw, float pitch, float scale) {
        super.render(limb, amount, age, yaw, pitch, scale);
    }
}
