package net.classicremastered.minecraft.model;

public final class NewSkeletonModel extends HumanoidModel {

    // authored base pose for arms (so we can restore each frame)
    private static final float BASE_R_ARM_PITCH = -1.570796F;   // -90°
    private static final float BASE_L_ARM_PITCH = -1.6580625F;  // ~-95°
    private static final float BASE_L_ARM_YAW   =  0.52359873F; // ~+30°

    public NewSkeletonModel() {
        // Temporarily set classic atlas to build UVs, then restore
        final int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64; TexturedQuad.ATLAS_H = 32;

        head = new ModelPart(0, 0);
        head.setBounds(-4F, -8F, -4F, 8, 8, 8, 0F);
        head.setPosition(0F, 0F, 0F);

        headwear = new ModelPart(32, 0);
        headwear.setBounds(-4F, -8F, -4F, 8, 8, 8, 0.5F);
        headwear.setPosition(0F, 0F, 0F);

        rightLeg = new ModelPart(0, 16);
        rightLeg.setBounds(-1F, 0F, -1F, 2, 12, 2, 0F);
        rightLeg.setPosition(-2F, 12F, 0F);

        leftLeg = new ModelPart(0, 16);
        leftLeg.mirror = true;
        leftLeg.setBounds(-1F, 0F, -1F, 2, 12, 2, 0F);
        leftLeg.setPosition(2F, 12F, 0F);

        rightArm = new ModelPart(40, 16);
        rightArm.setBounds(-1F, -2F, -1F, 2, 12, 2, 0F);
        rightArm.setPosition(-5F, 2F, 0F);

        leftArm = new ModelPart(40, 16);
        leftArm.mirror = true;
        leftArm.setBounds(-0.75F, -1F, -0.25F, 2, 12, 2, 0F);
        leftArm.setPosition(5F, 2F, 0F);

        body = new ModelPart(16, 16);
        body.setBounds(-4F, 0F, -2F, 8, 12, 4, 0F);
        body.setPosition(0F, 0F, 0F);

        // base authored pose
        rightArm.pitch = BASE_R_ARM_PITCH;
        leftArm.pitch  = BASE_L_ARM_PITCH;
        leftArm.yaw    = BASE_L_ARM_YAW;

        // restore atlas
        TexturedQuad.ATLAS_W = prevW; TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
        // Head: ModelPart angles are radians; caller gives degrees
        final float deg2rad = (float)Math.PI / 180F;
        head.yaw = netHeadYaw * deg2rad;
        head.pitch = headPitch * deg2rad;
        headwear.yaw = head.yaw;
        headwear.pitch = head.pitch;

        // Reset arms to base each frame
        rightArm.pitch = BASE_R_ARM_PITCH; rightArm.yaw = 0F; rightArm.roll = 0F;
        leftArm.pitch  = BASE_L_ARM_PITCH; leftArm.yaw  = BASE_L_ARM_YAW; leftArm.roll = 0F;

        // Walk cycle: legs out of phase; small arm counter-swing
        final float f = 0.6662F;
        final float legAmp = 1.4F * limbSwingAmount;
        final float armAmp = 0.6F * limbSwingAmount;

        rightLeg.pitch = (float)Math.cos(limbSwing * f) * legAmp;
        leftLeg .pitch = (float)Math.cos(limbSwing * f + Math.PI) * legAmp;

        rightArm.pitch += (float)Math.cos(limbSwing * f + Math.PI) * armAmp;
        leftArm .pitch += (float)Math.cos(limbSwing * f) * armAmp;
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        // ensure angles are set for this frame
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        // render order typical of Classic
        body.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        head.render(scale);
        headwear.render(scale);
    }
}
