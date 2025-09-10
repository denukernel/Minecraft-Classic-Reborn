package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

/** Child-scaled version of your ZombieModel. */
public class BabyZombieModel extends ZombieModel {
    public static final float CHILD_SCALE = 0.65f;

    // Approx adult humanoid height in Classic models
    private static final float ADULT_HEIGHT = 1.8f;
    // Tweak factor: if feet still clip, raise toward 1.0–1.1; if they float, lower to 0.8–0.9
    private static final float LIFT_K = 1.00f;

    public BabyZombieModel() { super(); }

    @Override
    public final void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                             float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        GL11.glPushMatrix();
        // Raise the model so shrunken legs still rest on the ground plane
        float lift = (ADULT_HEIGHT * (1.0f - CHILD_SCALE)) * LIFT_K;
        GL11.glTranslatef(0.0f, lift, 0.0f);
        GL11.glScalef(CHILD_SCALE, CHILD_SCALE, CHILD_SCALE);

        head.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);

        GL11.glPopMatrix();
    }
}
