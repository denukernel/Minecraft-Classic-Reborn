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
        head.setPosition(0, -14, 0);

        // Headwear
        headwear = new ModelPart(32, 0);
        headwear.setBounds(-4, -8, -4, 8, 8, 8, 0.5F);
        headwear.setPosition(0, -14, 0);

        // Body
        body = new ModelPart(16, 16);
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
        rightLeg.setPosition(-2, 0, 0);

        leftLeg = new ModelPart(56, 0);
        leftLeg.mirror = true;
        leftLeg.setBounds(-1, 0, -1, 2, 30, 2, 0.0F);
        leftLeg.setPosition(2, 0, 0);

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
        headwear.render(scale);
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
        headwear.render(scale);

        GL11.glPopAttrib();
    }

 // EndermanModel.java — REPLACE this method
    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        // --- Head look (no vertical “y” offset to avoid jitter) ---
        head.y   = -14.0F;   headwear.y = -14.0F;
        head.yaw =  netHeadYaw / 57.295776F;
        head.pitch = headPitch / 57.295776F;

        // --- Base walk (long-limb stride) ---
        // keep legs as-is, but tone down arm walk since punch anim will drive arms
        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F)                 * 1.4F * limbSwingAmount;
        leftLeg .pitch = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F)    * 1.4F * limbSwingAmount;

        // start arms nearly relaxed (small walk sway)
        float armWalk = 0.15F; // small because long arms
        rightArm.pitch = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F) * armWalk * limbSwingAmount;
        leftArm .pitch = MathHelper.cos(limbSwing * 0.6662F)              * armWalk * limbSwingAmount;
        rightArm.yaw = leftArm.yaw = 0.0F;
        rightArm.roll = leftArm.roll = 0.0F;

        // --- Attack (two-hand punch, zombie-style), driven by Model.attackOffset (0..1) ---
        // Mob.render() is already setting attackOffset based on attackTime
        if (this.attackOffset > 0.001F) {
            float a  = MathHelper.sin(this.attackOffset * 3.1415927F);
            float a2 = MathHelper.sin((1.0F - (1.0F - this.attackOffset) * (1.0F - this.attackOffset)) * 3.1415927F);

            // bigger swing for long thin arms, but keep it elegant
            float basePunchPitch = -1.5707964F; // -90 deg base
            float punchPitchAdd  = (a * 1.3F - a2 * 0.4F);

            rightArm.yaw   = -(0.10F - a * 0.55F);
            leftArm .yaw   =  (0.10F - a * 0.55F);

            rightArm.pitch = basePunchPitch - punchPitchAdd;
            leftArm .pitch = basePunchPitch - punchPitchAdd;

            // subtle roll sway for life
            rightArm.roll += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.03F;
            leftArm .roll -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.03F;

            rightArm.pitch += MathHelper.sin(ageInTicks * 0.067F) * 0.03F;
            leftArm .pitch -= MathHelper.sin(ageInTicks * 0.067F) * 0.03F;
        }

        // --- Carry pose (if you wire it later) overrides arm attack/walk ---
        if (isCarrying) {
            rightArm.pitch = leftArm.pitch = -0.50F;
            rightArm.roll  =  0.05F;
            leftArm .roll  = -0.05F;
            rightArm.yaw   = 0.0F;
            leftArm .yaw   = 0.0F;
        }

        // --- “Scream/attack” stance: head & torso attitude (no head.y nudges) ---
        if (isAttacking) {
            // slight head tuck and torso lean to sell the lunge
            head.pitch     += 0.20F;
            headwear.pitch += 0.20F;
            body.pitch      = 0.08F;
        } else {
            body.pitch = 0.0F;
        }

        // (Endermen arms are very long; avoid harsh clamps except extreme values)
    
    }
}
