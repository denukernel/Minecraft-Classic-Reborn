// File: com/mojang/minecraft/model/HumanoidModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public class HumanoidModel extends Model {

    public ModelPart head, headwear, body, rightArm, leftArm, rightLeg, leftLeg;

    // detect tall skin
    private final boolean tallSkin;

    public HumanoidModel() { this(0.0F, 32); }
    public HumanoidModel(float inflate) { this(inflate, 32); }

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
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        head.render(scale);
        headwear.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        head.yaw   = netHeadYaw / 57.295776F;
        head.pitch = headPitch  / 57.295776F;

        rightArm.pitch = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 2.0F * limbSwingAmount;
        rightArm.roll  = (MathHelper.cos(limbSwing * 0.2312F) + 1.0F) * limbSwingAmount;

        leftArm.pitch  = MathHelper.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount;
        leftArm.roll   = (MathHelper.cos(limbSwing * 0.2812F) - 1.0F) * limbSwingAmount;

        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.pitch  = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;

        rightArm.roll += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm.roll  -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        rightArm.pitch+= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        leftArm.pitch -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
    }
}
