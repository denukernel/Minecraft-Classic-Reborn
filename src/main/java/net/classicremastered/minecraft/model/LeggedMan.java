package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public final class LeggedMan extends Model {

    public ModelPart head;
    public ModelPart body;
    public ModelPart rightArm;
    public ModelPart leftArm;
    public ModelPart rightLeg;
    public ModelPart leftLeg;
    public ModelPart thirdleg;
    public ModelPart fourthleg;
    public ModelPart sixthleg;

    public LeggedMan() {
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64; TexturedQuad.ATLAS_H = 32;
        head = new ModelPart(0, 0);
        head.setBounds(-4.0F, -8.0F, -4.0F, 8, 8, 8, 0.0F);
        head.setPosition(-0.20000015F, 0.0F, 0.59999985F);
        body = new ModelPart(16, 16);
        body.setBounds(-4.0F, 0.0F, -2.0F, 8, 12, 4, 0.0F);
        body.setPosition(0.0F, 0.0F, 0.0F);
        rightArm = new ModelPart(40, 16);
        rightArm.setBounds(-3.0F, -2.0F, -2.0F, 4, 12, 4, 0.0F);
        rightArm.setPosition(-5.0F, 2.0F, 0.0F);
        leftArm = new ModelPart(40, 16);
        leftArm.mirror = true;
        leftArm.setBounds(-1.0F, -2.0F, -2.0F, 4, 12, 4, 0.0F);
        leftArm.setPosition(5.0F, 2.0F, 0.0F);
        rightLeg = new ModelPart(0, 16);
        rightLeg.setBounds(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
        rightLeg.setPosition(-3.6000004F, 12.0F, 0.0F);
        leftLeg = new ModelPart(0, 16);
        leftLeg.mirror = true;
        leftLeg.setBounds(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
        leftLeg.setPosition(2.0F, 12.0F, 0.0F);
        thirdleg = new ModelPart(0, 16);
        thirdleg.setBounds(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
        thirdleg.setPosition(5.599999F, 12.0F, 0.0F);
        fourthleg = new ModelPart(0, 16);
        fourthleg.setBounds(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
        fourthleg.setPosition(-0.7999991F, 12.0F, 0.0F);
        sixthleg = new ModelPart(0, 16);
        sixthleg.setBounds(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
        sixthleg.setPosition(-6.7999973F, 12.0F, -1.4901161E-7F);
        TexturedQuad.ATLAS_W = prevW; TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void render(float a, float b, float c, float headYaw, float headPitch, float scale) {
        this.head.render(scale);
        this.body.render(scale);
        this.rightArm.render(scale);
        this.leftArm.render(scale);
        this.rightLeg.render(scale);
        this.leftLeg.render(scale);
        this.thirdleg.render(scale);
        this.fourthleg.render(scale);
        this.sixthleg.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        // Head follows look direction
        head.yaw   = netHeadYaw / 57.295776F;
        head.pitch = headPitch  / 57.295776F;

        // Arms: swing opposite each other, synced with legs
        rightArm.pitch = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.5F * limbSwingAmount;
        leftArm.pitch  = MathHelper.cos(limbSwing * 0.6662F) * 1.5F * limbSwingAmount;

        // Legs: walk opposite, alternating phases
        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.pitch  = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;
        thirdleg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        fourthleg.pitch = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;
        sixthleg.pitch = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;

        // Idle arm sway (subtle breathing)
        rightArm.roll = MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm.roll  = -(MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F);
        rightArm.pitch += MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        leftArm.pitch  -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
    }
}
