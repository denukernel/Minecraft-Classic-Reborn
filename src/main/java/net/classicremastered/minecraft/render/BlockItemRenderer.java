package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.FlowerBlock;

import org.lwjgl.opengl.GL11;

/** Isolated preview state and per-face shading, shared by first- and third-person blocks. */
public final class BlockItemRenderer {
    public static void renderCube(
            Block block, TextureManager textures, float brightness, int size, float width) {
        GL11.glPushMatrix();
        try {
            float scale = width / size;
            GL11.glScalef(scale, scale, scale);
            GL11.glTranslatef(-size * .5f, -size * .5f, -size * .5f);
            for (int y = 0; y < size; y++)
                for (int z = 0; z < size; z++)
                    for (int x = 0; x < size; x++) {
                        GL11.glPushMatrix();
                        try {
                            GL11.glTranslatef(x, y, z);
                            render(block, textures, brightness);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void render(Block block, TextureManager textures, float brightness) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_POLYGON_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load(block.textureFile));
            ShapeRenderer shape = ShapeRenderer.instance;
            if (block instanceof net.classicremastered.minecraft.level.tile.CircuitBlock
                    || block instanceof net.classicremastered.minecraft.level.tile.PyramidBlock) {
                block.renderPreview(shape);
            } else if (block instanceof FlowerBlock) {
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glColor3f(brightness, brightness, brightness);
                block.renderPreview(shape);
            } else {
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glFrontFace(GL11.GL_CCW);
                float[] light = {.5f, 1, .8f, .8f, .6f, .6f};
                shape.begin();
                for (int face = 0; face < 6; face++) {
                    float shade = brightness * light[face];
                    shape.color(shade, shade, shade);
                    block.renderInside(shape, 0, 0, 0, face);
                }
                shape.end();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }
}
