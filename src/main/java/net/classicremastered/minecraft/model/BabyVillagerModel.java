package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

/** Child-scaled version of your VillagerModel. */
public class BabyVillagerModel extends VillagerModel {
    public static final float CHILD_SCALE = 0.65f;
    private static final float ADULT_HEIGHT = 1.8f;
    private static final float LIFT_K = 0.95f;

    public BabyVillagerModel() { super(0.0f); }

    @Override
    public final void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                             float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        GL11.glPushMatrix();
        float lift = (ADULT_HEIGHT * (1.0f - CHILD_SCALE)) * LIFT_K;
        GL11.glTranslatef(0.0f, lift, 0.0f);
        GL11.glScalef(CHILD_SCALE, CHILD_SCALE, CHILD_SCALE);

        head.render(scale);
        body.render(scale);   // robe + arms as children
        rightLeg.render(scale);
        leftLeg.render(scale);

        GL11.glPopMatrix();
    }
}
