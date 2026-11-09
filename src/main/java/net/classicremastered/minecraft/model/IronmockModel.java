package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.Ironmock;
import org.lwjgl.opengl.GL11;

/** Native 16-pixel skin geometry, uniformly enlarged to a 24-pixel/1.5-block mob. */
public final class IronmockModel extends Model {
    public final ModelPart head, nose, body, pelvis, rightArm, leftArm, rightLeg, leftLeg;
    private int attackTicks;
    public IronmockModel() {
        groundOffset = 24;
        int oldW = TexturedQuad.ATLAS_W, oldH = TexturedQuad.ATLAS_H;
        try {
            TexturedQuad.ATLAS_W = TexturedQuad.ATLAS_H = 32;
            head = part(0, 0, -3, -7, -2.5f, 6, 7, 5, 0, 7, 0);
            nose = part(17, 0, -1, 0, -1, 2, 4, 1, 0, -3, -2.5f);
            head.addChild(nose);
            body = part(0, 12, -4, 0, -2, 8, 4, 4, 0, 7, 0);
            pelvis = part(0, 20, -3, 0, -2, 6, 2, 4, 0, 11, 0);
            rightArm = part(24, 0, -1, 0, -1, 2, 9, 2, -5, 7, 0);
            leftArm = part(24, 11, -1, 0, -1, 2, 9, 2, 5, 7, 0);
            rightLeg = part(0, 26, -1, 0, -1, 2, 3, 2, -2, 13, 0);
            leftLeg = part(8, 26, -1, 0, -1, 2, 3, 2, 2, 13, 0);
        } finally {
            TexturedQuad.ATLAS_W = oldW;
            TexturedQuad.ATLAS_H = oldH;
        }
    }

    private static ModelPart part(int u, int v, float x, float y, float z, int w, int h, int d, float px, float py,
            float pz) {
        ModelPart part = new ModelPart(u, v);
        part.setBounds(x, y, z, w, h, d, 0);
        part.setPosition(px, py, pz);
        return part;
    }

    @Override
    public void preAnimate(Object entity) {
        attackTicks = entity instanceof Ironmock ? ((Ironmock) entity).attackTime : 0;
    }

    @Override
    public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale) {
        head.yaw = Math.max(-.65f, Math.min(.65f, yaw / 57.295776f));
        head.pitch = Math.max(-.35f, Math.min(.35f, pitch / 57.295776f));
        float stride = (float) Math.cos(swing * .6662f) * .65f * Math.min(1, amount);
        rightLeg.pitch = stride;
        leftLeg.pitch = -stride;
        rightArm.pitch = -stride * .5f;
        leftArm.pitch = stride * .5f;
        rightArm.roll = .025f + (float) Math.sin(age * .06f) * .015f;
        leftArm.roll = -rightArm.roll;
        if (attackTicks > 0) {
            float strike = (float) Math.sin(Math.min(1, attackTicks / 8f) * Math.PI);
            rightArm.pitch = leftArm.pitch = -1.2f - strike * .9f;
        }
    }

    @Override
    public void render(float swing, float amount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, amount, age, yaw, pitch, scale);
        GL11.glPushMatrix();
        GL11.glScalef(1.5f, 1.5f, 1.5f);
        head.render(scale);
        body.render(scale);
        pelvis.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
        GL11.glPopMatrix();
    }
}
