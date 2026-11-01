package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.model.*;
import net.classicremastered.util.MathHelper;

public final class LongNeckManModel extends Model {

    public ModelPart head;
    public ModelPart body;
    public ModelPart rightArm;
    public ModelPart leftArm;
    public ModelPart rightLeg;
    public ModelPart leftLeg;
    public ModelPart neck;

    public LongNeckManModel() {
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64; TexturedQuad.ATLAS_H = 32;
        head = new ModelPart(0, 0);
        head.setBounds(-4, -8, -4, 8, 8, 8, 0);
        head.setPosition(-0.34675127F, -5.699999F, 0.1508505F);
        body = new ModelPart(16, 16);
        body.setBounds(-4, 0, -2, 8, 12, 4, 0);
        body.setPosition(0, 0, 0);
        
        rightArm = new ModelPart(40, 16);
        rightArm.setBounds(-3, -2, -2, 4, 12, 4, 0);
        rightArm.setPosition(-4.7096424F, 2, -0.2532395F);
        
        leftArm = new ModelPart(40, 16);
        leftArm.setBounds(-3, -2, -2, 4, 12, 4, 0);
        leftArm.setPosition(5.844747F, 2, -0.10895178F);
        
        neck = new ModelPart(40, 16);
        neck.mirror = true;
        neck.setBounds(-1, -2, -2, 4, 12, 4, 0);
        neck.setPosition(-1.6606224F, -8.800002F, 0.31606564F);
        
        rightLeg = new ModelPart(0, 16);
        rightLeg.setBounds(-2, 0, -2, 4, 12, 4, 0);
        rightLeg.setPosition(-3.6000004F, 12, 0);
        
        leftLeg = new ModelPart(0, 16);
        leftLeg.mirror = true;
        leftLeg.setBounds(-2, 0, -2, 4, 12, 4, 0);
        leftLeg.setPosition(1.864896F, 12, 0.335699F);
        
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
        this.neck.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        // Head and neck follow look direction
        head.yaw   = netHeadYaw / 57.295776F;
        head.pitch = headPitch  / 57.295776F;
        neck.yaw   = head.yaw;
        neck.pitch = head.pitch;

        // Arms: swing opposite each other, synced with legs
        rightArm.pitch = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.5F * limbSwingAmount;
        leftArm.pitch  = MathHelper.cos(limbSwing * 0.6662F) * 1.5F * limbSwingAmount;

        // Legs: walk opposite, like vanilla
        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.pitch  = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;

        // Idle arm sway (subtle breathing)
        rightArm.roll = MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm.roll  = -(MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F);
        rightArm.pitch += MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        leftArm.pitch  -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
    }
}
