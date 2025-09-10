// File: src/net/classicremastered/minecraft/model/IronGolemModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.IronGolem;
import net.classicremastered.util.MathHelper;

public final class IronGolemModel extends Model {

    public ModelPart head;
    public ModelPart body;
    public ModelPart rightArm;
    public ModelPart leftArm;
    public ModelPart rightLeg;
    public ModelPart leftLeg;

    // flag set in preAnimate to block idle animation when attacking
    private boolean attacking = false;

    public IronGolemModel() {
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 128;
        TexturedQuad.ATLAS_H = 128;

        this.groundOffset = 25; // tall mob

        // Head
        head = new ModelPart(0, 0);
        head.setBounds(-4.0F, -12.0F, -5.5F, 8, 10, 8, 0.0F);
        head.setPosition(0.0F, -7.0F, -2.0F);
        // nose
        ModelPart nose = new ModelPart(24, 0);
        nose.setBounds(-1.0F, -5.0F, -7.5F, 2, 4, 2, 0.0F);
        head.addChild(nose);

        // Body
        body = new ModelPart(0, 40);
        body.setBounds(-9.0F, -2.0F, -6.0F, 18, 12, 11, 0.0F);
        ModelPart belly = new ModelPart(0, 70);
        belly.setBounds(-4.5F, 10.0F, -3.0F, 9, 5, 6, 0.5F);
        body.addChild(belly);
        body.setPosition(0.0F, -7.0F, 0.0F);

        // Arms
        rightArm = new ModelPart(60, 21);
        rightArm.setBounds(-13.0F, -2.5F, -3.0F, 4, 30, 6, 0.0F);
        rightArm.setPosition(0.0F, -7.0F, 0.0F);

        leftArm = new ModelPart(60, 58);
        leftArm.setBounds(9.0F, -2.5F, -3.0F, 4, 30, 6, 0.0F);
        leftArm.setPosition(0.0F, -7.0F, 0.0F);

        // Legs
        rightLeg = new ModelPart(37, 0);
        rightLeg.setBounds(-3.5F, -3.0F, -3.0F, 6, 16, 5, 0.0F);
        rightLeg.setPosition(-4.0F, 11.0F, 0.0F);

        leftLeg = new ModelPart(60, 0);
        leftLeg.setBounds(-3.5F, -3.0F, -3.0F, 6, 16, 5, 0.0F);
        leftLeg.setPosition(5.0F, 11.0F, 0.0F);

        TexturedQuad.ATLAS_W = prevW;
        TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void preAnimate(Object entity) {
        attacking = false; // reset each frame
        if (entity instanceof IronGolem) {
            IronGolem g = (IronGolem) entity;
            if (g.attackTime > 0) {
                float swing = MathHelper.sin(((float) g.attackTime / 5.0F) * (float) Math.PI);
                rightArm.pitch = -2.0F + 1.5F * swing;
                leftArm.pitch  = -2.0F + 1.5F * swing;
                attacking = true;
            }
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
        // Head look
        head.yaw   = netHeadYaw * (float)Math.PI / 180F;
        head.pitch = headPitch  * (float)Math.PI / 180F;

        // Legs walk
        rightLeg.pitch = -1.5F * MathHelper.cos(limbSwing * 0.6662F) * limbSwingAmount;
        leftLeg.pitch  =  1.5F * MathHelper.cos(limbSwing * 0.6662F) * limbSwingAmount;

        // Only apply idle arm swing if not attacking
        if (!attacking) {
            rightArm.pitch = (-0.2F + 1.5F * MathHelper.cos(limbSwing * 0.6662F)) * limbSwingAmount;
            leftArm.pitch  = (-0.2F - 1.5F * MathHelper.cos(limbSwing * 0.6662F)) * limbSwingAmount;
        }
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw,
                       float headPitch, float scale) {
        head.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
    }
}
