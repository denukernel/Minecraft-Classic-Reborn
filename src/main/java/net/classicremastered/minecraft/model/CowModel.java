package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.render.TextureManager;
import org.lwjgl.opengl.GL11;

public final class CowModel extends AnimalModel {
    private final ModelPart saddle;

    public CowModel() {
        super(12, 0);
        int previousWidth = TexturedQuad.ATLAS_W;
        int previousHeight = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = TexturedQuad.ATLAS_H = 64;
        try {
            head = part(0, 0, -4, -4, -6, 8, 8, 6, 0, 4, -8, false);
            ModelPart muzzle = part(1, 33, -3, 1, -7, 6, 3, 1, 0, 0, 0, false);
            ModelPart rightHorn = part(22, 0, -5, -5, -5, 1, 3, 1, 0, 0, 0, false);
            ModelPart leftHorn = part(22, 0, 4, -5, -5, 1, 3, 1, 0, 0, 0, false);
            body = part(18, 4, -6, -10, -7, 12, 18, 10, 0, 5, 2, false);
            ModelPart udder = part(52, 0, -2, 2, -8, 4, 6, 1, 0, 0, 0, false);
            leg1 = part(0, 16, -2, 0, -2, 4, 12, 4, -4, 12, 7, false);
            leg2 = part(0, 16, -2, 0, -2, 4, 12, 4, 4, 12, 7, true);
            leg3 = part(0, 16, -2, 0, -2, 4, 12, 4, -4, 12, -5, false);
            leg4 = part(0, 16, -2, 0, -2, 4, 12, 4, 4, 12, -5, true);
            head.addChild(muzzle);
            head.addChild(rightHorn);
            head.addChild(leftHorn);
            body.addChild(udder);
            saddle = new ModelPart(28, 8);
            saddle.setBounds(-5, -10, -7, 10, 16, 8, .4f);
        } finally {
            TexturedQuad.ATLAS_W = previousWidth;
            TexturedQuad.ATLAS_H = previousHeight;
        }
    }

    public void renderSaddle(TextureManager textures, float scale) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load("/mob/cow_saddle.png"));
            GL11.glTranslatef(body.x * scale, body.y * scale, body.z * scale);
            GL11.glRotatef((float) Math.toDegrees(body.pitch), 1f, 0f, 0f);
            GL11.glTranslatef(0f, 1.25f * scale, 1.75f * scale);
            GL11.glScalef(1.2f, 1.125f, 1.25f);
            saddle.render(scale);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static ModelPart part(
            int u,
            int v,
            int x,
            int y,
            int z,
            int width,
            int height,
            int depth,
            int pivotX,
            int pivotY,
            int pivotZ,
            boolean mirror) {
        ModelPart part = new ModelPart(u, v);
        part.mirror = mirror;
        part.setBounds(x, y, z, width, height, depth, 0);
        part.setPosition(pivotX, pivotY, pivotZ);
        return part;
    }
}
