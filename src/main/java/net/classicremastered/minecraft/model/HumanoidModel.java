// File: com/mojang/minecraft/model/HumanoidModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public class HumanoidModel extends Model {

    public ModelPart head, headwear, body, rightArm, leftArm, rightLeg, leftLeg;

    // detect tall skin
    private final boolean tallSkin;
    private float sneakProgress;
    private net.classicremastered.minecraft.player.Player developerPlayer;
    private float renderPartial;

    @Override
    public void preAnimate(Object entity) {
        developerPlayer =
                entity instanceof net.classicremastered.minecraft.player.Player player
                        ? player
                        : null;
    }

    public HumanoidModel() {
        this(0.0F, 32);
    }

    public HumanoidModel(float inflate) {
        this(inflate, 32);
    }

    public HumanoidModel(float inflate, int skinHeight) {
        this.tallSkin = (skinHeight == 64);

        int prevW = TexturedQuad.ATLAS_W;
        int prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64;
        TexturedQuad.ATLAS_H = tallSkin ? 64 : 32;

        if (tallSkin) {
            // --- 64x64 layout ---
            head = new ModelPart(0, 0);
            head.setBounds(-4, -8, -4, 8, 8, 8, inflate);

            headwear = new ModelPart(32, 0);
            headwear.setBounds(-4, -8, -4, 8, 8, 8, inflate + 0.5F);

            body = new ModelPart(16, 16);
            body.setBounds(-4, 0, -2, 8, 12, 4, inflate);

            rightArm = new ModelPart(40, 16);
            rightArm.setBounds(-3, -2, -2, 4, 12, 4, inflate);
            rightArm.setPosition(-5, 2, 0);

            leftArm = new ModelPart(32, 48); // unique slot in 64x64
            leftArm.mirror = true;
            leftArm.setBounds(-1, -2, -2, 4, 12, 4, inflate);
            leftArm.setPosition(5, 2, 0);

            rightLeg = new ModelPart(16, 48); // unique slot in 64x64
            rightLeg.setBounds(-2, 0, -2, 4, 12, 4, inflate);
            rightLeg.setPosition(-2, 12, 0);

            leftLeg = new ModelPart(0, 48); // unique slot in 64x64
            leftLeg.mirror = true;
            leftLeg.setBounds(-2, 0, -2, 4, 12, 4, inflate);
            leftLeg.setPosition(2, 12, 0);

        } else {
            // --- Classic 64x32 layout ---
            head = new ModelPart(0, 0);
            head.setBounds(-4, -8, -4, 8, 8, 8, inflate);

            headwear = new ModelPart(32, 0);
            headwear.setBounds(-4, -8, -4, 8, 8, 8, inflate + 0.5F);

            body = new ModelPart(16, 16);
            body.setBounds(-4, 0, -2, 8, 12, 4, inflate);

            rightArm = new ModelPart(40, 16);
            rightArm.setBounds(-3, -2, -2, 4, 12, 4, inflate);
            rightArm.setPosition(-5, 2, 0);

            leftArm = new ModelPart(40, 16);
            leftArm.mirror = true;
            leftArm.setBounds(-1, -2, -2, 4, 12, 4, inflate);
            leftArm.setPosition(5, 2, 0);

            rightLeg = new ModelPart(0, 16);
            rightLeg.setBounds(-2, 0, -2, 4, 12, 4, inflate);
            rightLeg.setPosition(-2, 12, 0);

            leftLeg = new ModelPart(0, 16);
            leftLeg.mirror = true;
            leftLeg.setBounds(-2, 0, -2, 4, 12, 4, inflate);
            leftLeg.setPosition(2, 12, 0);
        }

        // restore atlas
        TexturedQuad.ATLAS_W = prevW;
        TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void render(
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scale) {
        this.setRotationAngles(
                limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        renderParts(scale);
    }

    private void renderParts(float scale) {
        rightArm.pitch += .4f * sneakProgress;
        leftArm.pitch += .4f * sneakProgress;
        if (developerPlayer != null && developerPlayer.developerPowers().active(developerPlayer)) {
            renderDeveloperParts(scale);
            return;
        }
        drawParts(scale);
    }

    private void drawParts(float scale) {
        head.render(scale);
        headwear.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
    }

    private void renderDeveloperParts(float scale) {
        var powers = developerPlayer.developerPowers();
        float punch = powers.punchPose(developerPlayer, renderPartial);
        float kick = powers.kickPose(developerPlayer, renderPartial);
        float dodge = powers.dodgePose(developerPlayer, renderPartial);
        ModelPart arm = powers.leftPunch() ? leftArm : rightArm;
        float oldPitch = arm.pitch, oldYaw = arm.yaw, oldBodyYaw = body.yaw;
        float oldRightLeg = rightLeg.pitch, oldLeftLeg = leftLeg.pitch;
        float oldRightArm = rightArm.pitch, oldLeftArm = leftArm.pitch;
        float oldBodyPitch = body.pitch;
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glRotatef(28 * dodge * powers.dodgeSide(), 0, 0, 1);
            arm.pitch = oldPitch - 1.9f * punch;
            arm.yaw += (powers.leftPunch() ? -.5f : .5f) * punch;
            body.yaw += (powers.leftPunch() ? -.35f : .35f) * punch;
            rightLeg.pitch -= 1.7f * kick;
            leftLeg.pitch += .35f * kick;
            body.pitch -= .25f * kick;
            rightArm.pitch -= .8f * dodge;
            leftArm.pitch -= .8f * dodge;
            if (powers.aimingBlock(developerPlayer)) {
                rightArm.pitch = -2.5f;
                leftArm.pitch = -1.2f;
            }
            drawParts(scale);
        } finally {
            arm.pitch = oldPitch;
            arm.yaw = oldYaw;
            body.yaw = oldBodyYaw;
            rightLeg.pitch = oldRightLeg;
            leftLeg.pitch = oldLeftLeg;
            rightArm.pitch = oldRightArm;
            leftArm.pitch = oldLeftArm;
            body.pitch = oldBodyPitch;
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    public void applySwordPose(float pose) {
        rightArm.pitch -= Math.abs(pose) * 1.4f;
        rightArm.yaw -= Math.max(0, pose) * 1.25f;
        rightArm.roll += Math.max(0, pose) * .5f;
    }

    public void renderHoldingItem(float scale, float pose) {
        rightArm.pitch = rightArm.pitch * .5f - (float) Math.PI / 10;
        applySwordPose(pose);
        renderParts(scale);
    }

    public void renderHoldingGun(
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        float rp = rightArm.pitch, lp = leftArm.pitch, ry = rightArm.yaw, ly = leftArm.yaw;
        try {
            rightArm.pitch = -1.3f + headPitch / 57.295776f;
            leftArm.pitch = -1.15f + headPitch / 57.295776f;
            rightArm.yaw = -.12f;
            leftArm.yaw = .30f;
            renderParts(scale);
        } finally {
            rightArm.pitch = rp;
            leftArm.pitch = lp;
            rightArm.yaw = ry;
            leftArm.yaw = ly;
        }
    }

    public void renderHoldingBow(float yaw, float pitch, float scale, boolean aiming) {
        BowPose.apply(this, yaw / 57.295776f, pitch / 57.295776f, aiming);
        renderParts(scale);
    }

    public void setSneakProgress(float progress) {
        if (sneakProgress == progress) return;
        float delta = progress - sneakProgress;
        sneakProgress = progress;
        body.pitch = .5f * progress;
        head.y += 4.2f * delta;
        headwear.y = head.y;
        body.y += 3.2f * delta;
        rightArm.y += 3.2f * delta;
        leftArm.y += 3.2f * delta;
        rightLeg.z += 4 * delta;
        leftLeg.z += 4 * delta;
    }

    @Override
    public void setRotationAngles(
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scale) {
        renderPartial = ageInTicks - (float) Math.floor(ageInTicks);
        // Head follows look direction
        head.yaw = netHeadYaw / 57.295776F;
        head.pitch = headPitch / 57.295776F;
        headwear.copyFrom(head);
        rightArm.yaw = leftArm.yaw = 0;

        // Arms: swing opposite each other, synced with legs
        rightArm.pitch =
                MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.5F * limbSwingAmount;
        leftArm.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.5F * limbSwingAmount;

        // Legs: walk opposite, like vanilla
        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.pitch =
                MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;

        // Idle arm sway (subtle breathing)
        rightArm.roll = MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm.roll = -(MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F);
        rightArm.pitch += MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        leftArm.pitch -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
    }
}
