package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public final class TNTThrowerModel extends HumanoidModel {

    // TNT the mob holds (child of rightArm)
    private final ModelPart tnt;

    // Static pose (radians)
    private static final float HEAD_PITCH   = -0.087266475F; // -5°
    private static final float RARM_PITCH   = -1.6532664F;   // about -95°
    private static final float RARM_YAW     = -0.34826645F;  // about -20°
    private static final float LARM_PITCH   = -1.6532664F;
    private static final float LARM_YAW     =  0.52226645F;  // about 30°

    public TNTThrowerModel() {
        super(0.0F); // no inflate

        // Force 64x64 atlas during construction, then restore
        final int pw = TexturedQuad.ATLAS_W, ph = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64; TexturedQuad.ATLAS_H = 64;

        // Head/Body
        head.setBounds(-4, -8, -4, 8, 8, 8, 0); head.setPosition(0, 0, 0);
        body.setBounds(-4,  0, -2, 8,12, 4, 0); body.setPosition(0, 0, 0);

        // Arms
        rightArm.setBounds(-3, -2, -2, 4,12,4, 0); rightArm.setPosition(-5, 2, 0);
        leftArm.mirror = true;
        leftArm.setBounds(-1, -2, -2, 4,12,4, 0); leftArm.setPosition( 5, 2, 0);

        // Legs
        rightLeg.setBounds(-2, 0, -2, 4,12,4, 0); rightLeg.setPosition(-2, 12, 0);
        leftLeg.mirror = true;
        leftLeg.setBounds(-2, 0, -2, 4,12,4, 0); leftLeg.setPosition( 2, 12, 0);

        // Hide hat layer if present (Classic sometimes has it)
        if (headwear != null) headwear.render = false;

        // TNT cube (UV start 32,40 on 64x64 sheet; adjust to your texture)
        tnt = new ModelPart(32, 40);
        tnt.setBounds(-3, 6, -8, 6, 6, 6, 0); // near wrist
        tnt.setPosition(0, 0, 0);
        rightArm.addChild(tnt);

        // restore atlas
        TexturedQuad.ATLAS_W = pw; TexturedQuad.ATLAS_H = ph;
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        // Head look
        head.yaw   = netHeadYaw / 57.295776F;
        head.pitch = headPitch  / 57.295776F + HEAD_PITCH;

        // Legs walk (vanilla pattern)
        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.pitch  = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F) * 1.4F * limbSwingAmount;

        // Arms default static “ready to throw”
        rightArm.roll = leftArm.roll = 0F;
        rightArm.pitch = RARM_PITCH; rightArm.yaw = RARM_YAW;
        leftArm.pitch  = LARM_PITCH; leftArm.yaw  = LARM_YAW;

        // Optional throw animation if your Mob sets attackOffset 0..1
        final float t = this.attackOffset; // present in Classic HumanoidModel
        if (t > 0F) {
            final float pi = 3.1415927F;
            if (t < 0.5F) {
                float k = t / 0.5F;                 // wind-up 0..1
                float e = MathHelper.sin(k * pi*0.5F);
                rightArm.pitch = lerp(RARM_PITCH, -2.10F, e);
                rightArm.yaw   = lerp(RARM_YAW,   RARM_YAW - 0.25F, e);
            } else {
                float k = (t - 0.5F) / 0.5F;        // release 0..1
                float e = MathHelper.sin(k * pi*0.9F);
                rightArm.pitch = lerp(-2.10F, -0.20F, e);
                rightArm.yaw   = lerp(RARM_YAW - 0.25F, 0.0F, e);
            }
        }
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        head.render(scale);
        body.render(scale);
        rightArm.render(scale); // renders TNT because it is a child
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
