package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

/** Adult skeleton geometry rendered with the same child transforms as baby zombies. */
public final class BabySkeletonModel extends SkeletonModel {
    public BabySkeletonModel() {
        groundOffset = 24;
    }

    @Override
    protected void renderBody(
            float limb, float amount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limb, amount, age, yaw, pitch, scale);
        BabyZombieModel.renderHead(head, scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
    }

    @Override
    public void render(float limb, float amount, float age, float yaw, float pitch, float scale) {
        GL11.glPushMatrix();
        try {
            BabyZombieModel.applyChildTransform(scale);
            super.render(limb, amount, age, yaw, pitch, scale);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
