package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

/**
 * Model for the Agent mob (Minecraft Education Edition / Bedrock geometry.agent).
 *
 * Geometry specifications (32x32 texture atlas):
 * - Head: 6x7x5 at neck pivot (0, 15, -0.8), with nose/visor child cube (2x3x1).
 * - Body: 8x4x4 chest at pivot (0, 15, 0), with lower waist child cube (6x2x4).
 * - Right Arm: 2x9x2 at shoulder pivot (-5, 15, 0).
 * - Left Arm: 2x9x2 at shoulder pivot (5, 15, 0).
 * - Right Leg: 2x3x2 at hip pivot (-1.9, 21, 0).
 * - Left Leg: 2x3x2 at hip pivot (1.9, 21, 0).
 * Total height: 16 pixels (1 block tall).
 */
public final class AgentModel extends Model {

    public ModelPart head;
    public ModelPart nose;
    public ModelPart body;
    public ModelPart waist;
    public ModelPart rightArm;
    public ModelPart leftArm;
    public ModelPart rightLeg;
    public ModelPart leftLeg;

    public AgentModel() {
        int prevW = TexturedQuad.ATLAS_W;
        int prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 32;
        TexturedQuad.ATLAS_H = 32;

        this.groundOffset = 24.0F;

        // Head (neck pivot at Y = 15, Z = -0.8)
        head = new ModelPart(0, 0);
        head.setBounds(-3.0F, -7.0F, -2.2F, 6, 7, 5, 0.0F);
        head.setPosition(0.0F, 15.0F, -0.8F);

        // Nose / Visor attachment (child of head)
        nose = new ModelPart(17, 1);
        nose.setBounds(-1.0F, -3.0F, -3.2F, 2, 3, 1, 0.0F);
        head.addChild(nose);

        // Body: Upper chest (pivot at Y = 15)
        body = new ModelPart(0, 12);
        body.setBounds(-4.0F, 0.0F, -2.0F, 8, 4, 4, 0.0F);
        body.setPosition(0.0F, 15.0F, 0.0F);

        // Lower waist (child of body, spans Y from 4 to 6 relative to body)
        waist = new ModelPart(0, 20);
        waist.setBounds(-3.0F, 4.0F, -2.0F, 6, 2, 4, 0.0F);
        body.addChild(waist);

        // Right Arm (shoulder pivot at -5, 15, 0)
        rightArm = new ModelPart(24, 0);
        rightArm.setBounds(-1.0F, 0.0F, -1.0F, 2, 9, 2, 0.0F);
        rightArm.setPosition(-5.0F, 15.0F, 0.0F);

        // Left Arm (shoulder pivot at 5, 15, 0)
        leftArm = new ModelPart(24, 11);
        leftArm.setBounds(-1.0F, 0.0F, -1.0F, 2, 9, 2, 0.0F);
        leftArm.setPosition(5.0F, 15.0F, 0.0F);

        // Right Leg (hip pivot at -1.9, 21, 0)
        rightLeg = new ModelPart(0, 26);
        rightLeg.setBounds(-1.1F, 0.0F, -1.0F, 2, 3, 2, 0.0F);
        rightLeg.setPosition(-1.9F, 21.0F, 0.0F);

        // Left Leg (hip pivot at 1.9, 21, 0)
        leftLeg = new ModelPart(8, 26);
        leftLeg.setBounds(-0.9F, 0.0F, -1.0F, 2, 3, 2, 0.0F);
        leftLeg.setPosition(1.9F, 21.0F, 0.0F);

        // Restore texture atlas dimensions
        TexturedQuad.ATLAS_W = prevW;
        TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        head.render(scale);
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

        rightArm.pitch = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F) * 1.4F * limbSwingAmount;
        leftArm.pitch  = MathHelper.cos(limbSwing * 0.6662F)               * 1.4F * limbSwingAmount;
        rightArm.roll  = 0.0F;
        leftArm.roll   = 0.0F;

        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F)               * 1.4F * limbSwingAmount;
        leftLeg.pitch  = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F) * 1.4F * limbSwingAmount;
        rightLeg.yaw   = 0.0F;
        leftLeg.yaw    = 0.0F;
    }
}
