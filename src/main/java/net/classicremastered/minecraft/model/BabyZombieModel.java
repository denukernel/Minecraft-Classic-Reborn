package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

public class BabyZombieModel extends ZombieModel {
    public static final float CHILD_SCALE = 0.65F;
    private static final float HEAD_SCALE = 1.5F;
    private static final float LEG_BOTTOM = 24.0F;

    public BabyZombieModel() {
        groundOffset = LEG_BOTTOM;
    }

    public static void applyChildTransform(float scale) {
        GL11.glTranslatef(0.0F, LEG_BOTTOM * scale * (1.0F - CHILD_SCALE), 0.0F);
        GL11.glScalef(CHILD_SCALE, CHILD_SCALE, CHILD_SCALE);
    }

    public static void renderHead(ModelPart part, float scale) {
        GL11.glPushMatrix();
        GL11.glTranslatef(part.x * scale, part.y * scale, part.z * scale);
        GL11.glScalef(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
        GL11.glTranslatef(-part.x * scale, -part.y * scale, -part.z * scale);
        part.render(scale);
        GL11.glPopMatrix();
    }

    @Override
    public final void render(float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        GL11.glPushMatrix();
        applyChildTransform(scale);
        renderHead(head, scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
        GL11.glPopMatrix();
    }
}
