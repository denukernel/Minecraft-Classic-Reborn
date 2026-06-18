package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

public final class EndermanModel extends Model {

    public ModelPart head, headwear, body, rightArm, leftArm, rightLeg, leftLeg;
    public boolean isCarrying = false;
    public boolean isAttacking = false;

    public EndermanModel() {
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64;
        TexturedQuad.ATLAS_H = 32;
        this.groundOffset = 31;  // try 31–34 if your legs/boots differ
        // Head
        head = new ModelPart(0, 0);
        head.setBounds(-4, -8, -4, 8, 8, 8, 0.0F);
        head.setPosition(0, -13, 0);

        // Headwear
        headwear = new ModelPart(0, 16);
        headwear.setBounds(-4, -8, -4, 8, 8, 8, -0.5F);
        headwear.setPosition(0, 0, 0);
        head.addChild(headwear);

        // Body
        body = new ModelPart(32, 16);
        body.setBounds(-4, 0, -2, 8, 12, 4, 0.0F);
        body.setPosition(0, -14, 0);

        // Arms
        rightArm = new ModelPart(56, 0);
        rightArm.setBounds(-1, -2, -1, 2, 30, 2, 0.0F);
        rightArm.setPosition(-3, -12, 0);

        leftArm = new ModelPart(56, 0);
        leftArm.mirror = true;
        leftArm.setBounds(-1, -2, -1, 2, 30, 2, 0.0F);
        leftArm.setPosition(5, -12, 0);

        // Legs
        rightLeg = new ModelPart(56, 0);
        rightLeg.setBounds(-1, 0, -1, 2, 30, 2, 0.0F);
        rightLeg.setPosition(-2, -5, 0);

        leftLeg = new ModelPart(56, 0);
        leftLeg.mirror = true;
        leftLeg.setBounds(-1, 0, -1, 2, 30, 2, 0.0F);
        leftLeg.setPosition(2, -5, 0);

        TexturedQuad.ATLAS_W = prevW;
        TexturedQuad.ATLAS_H = prevH;
    }

 // EndermanModel.java — REPLACE this method
    @Override
    public void preAnimate(Object entity) {
        // sync transient pose flags from the mob each frame
        this.isAttacking = false;
        this.isCarrying  = false; // set true from your mob if/when you add carrying later

        if (entity instanceof net.classicremastered.minecraft.mob.Enderman) {
            net.classicremastered.minecraft.mob.Enderman e = (net.classicremastered.minecraft.mob.Enderman) entity;
            this.isAttacking = e.isAttacking;
        }
    }


    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        // Base pass (black body)
        head.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
    }

    public void renderEyes(TextureManager tm, float scale) {
        int eyesTex = tm.load("/mob/enderman_eyes.png");

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE); // additive blending
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, eyesTex);
        head.render(scale);

        GL11.glPopAttrib();
    }

 // EndermanModel.java — REPLACE this method
    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        // --- Head look ---
        head.yaw = netHeadYaw / 57.295776F;
        head.pitch = headPitch / 57.295776F;
        head.roll = 0.0F;

        // --- Base walk (halved stride for Enderman) ---
        rightLeg.pitch = (float) (MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount * 0.5D);
        leftLeg.pitch = (float) (MathHelper.cos(limbSwing * 0.6662F + 3.1415927F) * 1.4F * limbSwingAmount * 0.5D);

        rightArm.pitch = (float) (MathHelper.cos(limbSwing * 0.6662F + 3.1415927F) * 2.0F * limbSwingAmount * 0.5F * 0.5D);
        leftArm.pitch = (float) (MathHelper.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F * 0.5D);

        // Clamping of arms and legs rotations like in ModelEnderman.java
        float var8 = 0.4F;
        if (rightArm.pitch > var8) rightArm.pitch = var8;
        if (leftArm.pitch > var8) leftArm.pitch = var8;
        if (rightArm.pitch < -var8) rightArm.pitch = -var8;
        if (leftArm.pitch < -var8) leftArm.pitch = -var8;

        if (rightLeg.pitch > var8) rightLeg.pitch = var8;
        if (leftLeg.pitch > var8) leftLeg.pitch = var8;
        if (rightLeg.pitch < -var8) rightLeg.pitch = -var8;
        if (leftLeg.pitch < -var8) leftLeg.pitch = -var8;

        rightArm.yaw = leftArm.yaw = 0.0F;
        rightArm.roll = leftArm.roll = 0.0F;

        // --- Attack (two-hand punch, zombie-style), driven by Model.attackOffset (0..1) ---
        if (this.attackOffset > 0.001F) {
            float a  = MathHelper.sin(this.attackOffset * 3.1415927F);
            float a2 = MathHelper.sin((1.0F - (1.0F - this.attackOffset) * (1.0F - this.attackOffset)) * 3.1415927F);

            float basePunchPitch = -1.5707964F; // -90 deg base
            float punchPitchAdd  = (a * 1.3F - a2 * 0.4F);

            rightArm.yaw   = -(0.10F - a * 0.55F);
            leftArm .yaw   =  (0.10F - a * 0.55F);

            rightArm.pitch = basePunchPitch - punchPitchAdd;
            leftArm .pitch = basePunchPitch - punchPitchAdd;

            rightArm.roll += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.03F;
            leftArm .roll -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.03F;

            rightArm.pitch += MathHelper.sin(ageInTicks * 0.067F) * 0.03F;
            leftArm .pitch -= MathHelper.sin(ageInTicks * 0.067F) * 0.03F;
        }

        // --- Carry pose ---
        if (isCarrying) {
            rightArm.pitch = leftArm.pitch = -0.50F;
            rightArm.roll  =  0.05F;
            leftArm .roll  = -0.05F;
            rightArm.yaw   = 0.0F;
            leftArm .yaw   = 0.0F;
        }

        // Set rotation points (overrides values)
        rightArm.z = 0.0F;
        leftArm.z = 0.0F;
        rightLeg.z = 0.0F;
        leftLeg.z = 0.0F;
        rightLeg.y = -5.0F;
        leftLeg.y = -5.0F;
        head.z = 0.0F;
        head.y = -13.0F;

        headwear.pitch = 0.0F;
        headwear.yaw = 0.0F;
        headwear.roll = 0.0F;
        headwear.x = 0.0F;
        headwear.z = 0.0F;
        headwear.y = isAttacking ? 5.0F : 0.0F;

        // --- “Scream/attack” stance: head & torso attitude ---
        if (isAttacking) {
            head.y -= 5.0F;
            body.pitch = 0.08F;
        } else {
            body.pitch = 0.0F;
        }
    }
}
