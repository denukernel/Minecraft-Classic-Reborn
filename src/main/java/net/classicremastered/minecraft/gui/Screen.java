package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

public class Screen {

    protected float imgZ = 0.0F;

    protected static void drawMinecoinIcon(TextureManager textures, int x, int y) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load("/gui/minecoin.png"));
            ShapeRenderer shape = ShapeRenderer.instance;
            shape.begin();
            shape.vertexUV(x, y + 10, 0, 0, 1);
            shape.vertexUV(x + 9, y + 10, 0, 1, 1);
            shape.vertexUV(x + 9, y, 0, 1, 0);
            shape.vertexUV(x, y, 0, 0, 0);
            shape.end();
        } finally {
            GL11.glPopAttrib();
        }
    }

    protected static void drawBox(int var0, int var1, int var2, int var3, int var4) {
        float var5 = (float) (var4 >>> 24) / 255.0F;
        float var6 = (float) (var4 >> 16 & 255) / 255.0F;
        float var7 = (float) (var4 >> 8 & 255) / 255.0F;
        float var9 = (float) (var4 & 255) / 255.0F;
        ShapeRenderer var8 = ShapeRenderer.instance;
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glColor4f(var6, var7, var9, var5);
        var8.begin();
        var8.vertex((float) var0, (float) var3, 0.0F);
        var8.vertex((float) var2, (float) var3, 0.0F);
        var8.vertex((float) var2, (float) var1, 0.0F);
        var8.vertex((float) var0, (float) var1, 0.0F);
        var8.end();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
    }

    protected static void drawFadingBox(int var0, int var1, int var2, int var3, int var4, int var5) {
        float var6 = (float) (var4 >>> 24) / 255.0F;
        float var7 = (float) (var4 >> 16 & 255) / 255.0F;
        float var8 = (float) (var4 >> 8 & 255) / 255.0F;
        float var12 = (float) (var4 & 255) / 255.0F;
        float var9 = (float) (var5 >>> 24) / 255.0F;
        float var10 = (float) (var5 >> 16 & 255) / 255.0F;
        float var11 = (float) (var5 >> 8 & 255) / 255.0F;
        float var13 = (float) (var5 & 255) / 255.0F;
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glBegin(7);
        GL11.glColor4f(var10, var11, var13, var9);
        GL11.glVertex2f((float) var0, (float) var3);
        GL11.glVertex2f((float) var2, (float) var3);
        GL11.glColor4f(var7, var8, var12, var6);
        GL11.glVertex2f((float) var2, (float) var1);
        GL11.glVertex2f((float) var0, (float) var1);
        GL11.glEnd();
        GL11.glDisable(3042);
        GL11.glEnable(3553);
    }

    public static void drawCenteredString(FontRenderer var0, String var1, int var2, int var3, int var4) {
        var0.render(var1, var2 - var0.getWidth(var1) / 2, var3, var4);
    }

    public static void drawString(FontRenderer var0, String var1, int var2, int var3, int var4) {
        var0.render(var1, var2, var3, var4);
    }

    public final void drawImage(int var1, int var2, int var3, int var4, int var5, int var6) {
        float var7 = 0.00390625F;
        float var8 = 0.00390625F;
        ShapeRenderer var9 = ShapeRenderer.instance;
        ShapeRenderer.instance.begin();
        var9.vertexUV((float) var1, (float) (var2 + var6), this.imgZ, (float) var3 * var7,
                (float) (var4 + var6) * var8);
        var9.vertexUV((float) (var1 + var5), (float) (var2 + var6), this.imgZ, (float) (var3 + var5) * var7,
                (float) (var4 + var6) * var8);
        var9.vertexUV((float) (var1 + var5), (float) var2, this.imgZ, (float) (var3 + var5) * var7,
                (float) var4 * var8);
        var9.vertexUV((float) var1, (float) var2, this.imgZ, (float) var3 * var7, (float) var4 * var8);
        var9.end();
    }
}
