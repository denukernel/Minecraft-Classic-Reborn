package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.opengl.GL11;

/** Modern oak hull and paddle geometry rendered through the classic model parts. */
public final class BoatModel {
    private static final float SCALE = 1f / 16;
    private final ModelPart[] hull;
    private final ModelPart[] paddles = new ModelPart[2];

    public BoatModel() {
        int width = TexturedQuad.ATLAS_W;
        int height = TexturedQuad.ATLAS_H;
        try {
            TexturedQuad.ATLAS_W = 128;
            TexturedQuad.ATLAS_H = 64;
            hull =
                    new ModelPart[] {
                        part(0, 0, -14, -9, -3, 28, 16, 3, 0, 3, 1, 90, 0),
                        part(0, 19, -13, -7, -1, 18, 6, 2, -15, 4, 4, 0, 270),
                        part(0, 27, -8, -7, -1, 16, 6, 2, 15, 4, 0, 0, 90),
                        part(0, 35, -14, -7, -1, 28, 6, 2, 0, 4, -9, 0, 180),
                        part(0, 43, -14, -7, -1, 28, 6, 2, 0, 4, 9, 0, 0)
                    };
            for (int i = 0; i < paddles.length; i++) {
                int v = i * 20;
                ModelPart handle = part(62, v, -1, 0, -5, 2, 2, 18, 3, -5, i == 0 ? 9 : -9, 0, 0);
                handle.roll = (float) Math.PI / 16;
                handle.addChild(
                        part(62, v, i == 0 ? -1.001f : .001f, -3, 8, 1, 6, 7, 0, 0, 0, 0, 0));
                paddles[i] = handle;
            }
        } finally {
            TexturedQuad.ATLAS_W = width;
            TexturedQuad.ATLAS_H = height;
        }
    }

    private static ModelPart part(
            int u,
            int v,
            float bx,
            float by,
            float bz,
            int width,
            int height,
            int depth,
            float x,
            float y,
            float z,
            float pitch,
            float yaw) {
        ModelPart part = new ModelPart(u, v);
        part.setBounds(bx, by, bz, width, height, depth, 0);
        part.setPosition(x, y, z);
        part.pitch = (float) Math.toRadians(pitch);
        part.yaw = (float) Math.toRadians(yaw);
        return part;
    }

    public void render(
            TextureManager textures,
            float x,
            float feetY,
            float z,
            float yaw,
            float wobble,
            float brightness,
            float left,
            float right) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, feetY + .375f, z);
            GL11.glRotatef(180 - yaw, 0, 1, 0);
            GL11.glRotatef(wobble, 1, 0, 0);
            GL11.glScalef(-1, -1, 1);
            GL11.glRotatef(90, 0, 1, 0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load("/mob/oak_boat.png"));
            GL11.glColor3f(brightness, brightness, brightness);
            for (ModelPart part : hull) part.render(SCALE);
            renderPaddle(0, left);
            renderPaddle(1, right);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void renderPaddle(int side, float time) {
        ModelPart paddle = paddles[side];
        paddle.pitch = (float) Math.toRadians(-37.5 + 22.5 * Math.sin(-time));
        paddle.yaw = (float) (Math.PI / 4 * Math.sin(1 - time));
        if (side == 1) paddle.yaw = (float) Math.PI - paddle.yaw;
        paddle.render(SCALE);
    }
}
