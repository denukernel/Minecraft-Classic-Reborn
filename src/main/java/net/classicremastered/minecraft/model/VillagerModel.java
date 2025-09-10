package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public class VillagerModel extends Model {

    public ModelPart head, nose, body, robe, rightArm, leftArm, armsBar, rightLeg, leftLeg;

    public VillagerModel() { this(0.0F); }

    public VillagerModel(float inflate) {
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64; TexturedQuad.ATLAS_H = 64;
        try {
            head = new ModelPart(0, 0);
            head.setBounds(-4, -10, -4, 8, 10, 8, inflate);
            head.setPosition(0, 0, 0);

            nose = new ModelPart(24, 0);
            nose.setBounds(-1, -1, -6, 2, 4, 2, inflate);
            nose.setPosition(0, -2, 0);
            head.addChild(nose);

            body = new ModelPart(16, 20);
            body.setBounds(-4, 0, -3, 8, 12, 6, inflate);

            robe = new ModelPart(0, 38);
            robe.setBounds(-4, 0, -3, 8, 18, 6, inflate + 0.5F);
            body.addChild(robe);

            rightArm = new ModelPart(44, 22);
            rightArm.setBounds(-8, -2, -2, 4, 8, 4, inflate);
            rightArm.setPosition(0, 2, -1);

            leftArm = new ModelPart(44, 22);
            leftArm.mirror = true;
            leftArm.setBounds(4, -2, -2, 4, 8, 4, inflate);
            leftArm.setPosition(0, 2, -1);

            armsBar = new ModelPart(40, 38);
            armsBar.setBounds(-4, 2, -2, 8, 4, 4, inflate);
            armsBar.setPosition(0, 2, -1);

            rightArm.pitch = leftArm.pitch = armsBar.pitch = -0.75F;
            rightArm.yaw = leftArm.yaw = armsBar.yaw = 0.0F;
            rightArm.roll = leftArm.roll = armsBar.roll = 0.0F;

            body.setPosition(0, 0, 0);
            body.addChild(rightArm);
            body.addChild(leftArm);
            body.addChild(armsBar);

            rightLeg = new ModelPart(0, 22);
            rightLeg.setBounds(-2, 0, -2, 4, 12, 4, inflate);
            rightLeg.setPosition(-2, 12, 0);

            leftLeg = new ModelPart(0, 22);
            leftLeg.mirror = true;
            leftLeg.setBounds(-2, 0, -2, 4, 12, 4, inflate);
            leftLeg.setPosition(2, 12, 0);
        } finally {
            TexturedQuad.ATLAS_W = prevW; TexturedQuad.ATLAS_H = prevH;
        }
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                             float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        head.render(scale);   // nose is child
        body.render(scale);   // robe + arms as children
        rightLeg.render(scale);
        leftLeg.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        head.yaw   = netHeadYaw / 57.295776F;
        head.pitch = headPitch  / 57.295776F;

        // Arms stay crossed (already posed in ctor)
        rightArm.roll = 0.0F;
        leftArm.roll  = 0.0F;

        // Leg walk like humanoid
        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.pitch  = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F) * 1.4F * limbSwingAmount;
        rightLeg.yaw = leftLeg.yaw = 0.0F;
    }
}
