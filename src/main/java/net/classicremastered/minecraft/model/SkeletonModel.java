// File: src/com/mojang/minecraft/model/SkeletonModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

public class SkeletonModel extends ZombieModel {
    private static final int LEG_LENGTH = 12;
    private static final int LEG_HALF_DEPTH = 1;

    public SkeletonModel() {
        this.rightArm = new ModelPart(40, 16);
        this.rightArm.setBounds(-1.0F, -2.0F, -1.0F, 2, 12, 2, 0.0F);
        this.rightArm.setPosition(-5.0F, 2.0F, 0.0F);

        this.leftArm = new ModelPart(40, 16);
        this.leftArm.mirror = true;
        this.leftArm.setBounds(-1.0F, -2.0F, -1.0F, 2, 12, 2, 0.0F);
        this.leftArm.setPosition(5.0F, 2.0F, 0.0F);

        this.rightLeg = new ModelPart(0, 16);
        this.rightLeg.setBounds(
                -1.0F, 0.0F, -LEG_HALF_DEPTH, 2, LEG_LENGTH, LEG_HALF_DEPTH * 2, 0.0F);
        this.rightLeg.setPosition(-2.0F, 12.0F, 0.0F);

        this.leftLeg = new ModelPart(0, 16);
        this.leftLeg.mirror = true;
        this.leftLeg.setBounds(
                -1.0F, 0.0F, -LEG_HALF_DEPTH, 2, LEG_LENGTH, LEG_HALF_DEPTH * 2, 0.0F);
        this.leftLeg.setPosition(2.0F, 12.0F, 0.0F);
    }

    public float groundedStrideOffset(float limb, float amount, boolean baby) {
        float pitch = Math.abs(MathHelper.cos(limb * .6662f) * 1.4f * amount);
        // The lowest toe corner rises as the straight leg rotates around its hip.
        float reach = LEG_LENGTH * MathHelper.cos(pitch) + LEG_HALF_DEPTH * MathHelper.sin(pitch);
        return (LEG_LENGTH - reach) * (baby ? BabyZombieModel.CHILD_SCALE : 1f);
    }

    private Skeleton skeleton;
    private float partial;
    private net.classicremastered.minecraft.render.TextureManager renderTextures;

    public void setRenderTextures(net.classicremastered.minecraft.render.TextureManager textures) {
        renderTextures = textures;
    }

    public void setRenderPartial(float partial) {
        this.partial = partial;
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
        if (isRidingPose()) {
            rightArm.pitch -= .62831855f;
            leftArm.pitch -= .62831855f;
        }
        if (skeleton != null) {
            BowPose.apply(this, head.yaw, head.pitch, skeleton.isDrawingBow());
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
        head.render(scale);
        if (hasHeadOverlay) headwear.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        renderSeatedLegs(scale);
    }

    protected final void renderSeatedLegs(float scale) {
        renderSeatedLegs(rightLeg, leftLeg, scale);
    }

    public final void renderSeatedLegs(ModelPart right, ModelPart left, float scale) {
        float seatYaw = skeleton == null ? 0 : skeleton.seatedLegYaw(partial);
        if (seatYaw == 0) {
            right.render(scale);
            left.render(scale);
            return;
        }
        GL11.glPushMatrix();
        try {
            GL11.glRotatef(seatYaw, 0, 1, 0);
            right.render(scale);
            left.render(scale);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
