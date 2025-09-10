// com/mojang/minecraft/model/BabyHumanoidArmorModel.java
package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

public class BabyHumanoidArmorModel extends HumanoidModel {
    private static final float SCALE = 0.65f;

    public BabyHumanoidArmorModel() {
        super(1.0F); // use inflated version
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(SCALE, SCALE, SCALE);
        GL11.glTranslatef(0.0f, (1.8f * (1.0f - SCALE)) * 0.95f, 0.0f); // lift so feet align
        super.render(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        GL11.glPopMatrix();
    }
}
