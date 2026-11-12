package net.classicremastered.minecraft;

import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.render.Renderer;

import org.lwjgl.opengl.GL11;

/** Draws the first-person arm and held block after the world passes. */
final class ClientFirstPersonRenderer {
    private ClientFirstPersonRenderer() {}

    static void render(Renderer renderer, float partial, int stereoEye) {
        if (renderer.minecraft.cameraMode != 0 || renderer.minecraft.gamemode.isSpectator())
            return;
        GL11.glClear(256);
        GL11.glLoadIdentity();
        if (renderer.minecraft.settings.anaglyph) {
            GL11.glTranslatef((float) ((stereoEye << 1) - 1) * 0.1F, 0.0F, 0.0F);
        }

        renderer.hurtEffect(partial);
        if (renderer.minecraft.settings.viewBobbing) {
            renderer.applyBobbing(partial);
        }

        if (!renderer.minecraft.hudHidden) {
            renderer.setLighting(true);
            renderer.renderHeldItem(partial);
            renderer.setLighting(false);
        }
        if (renderer.minecraft.player.remainingFireTicks > 0) {
            renderFireOverlay(renderer);
        }
    }

    static void renderFireOverlay(Renderer renderer) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_TRANSFORM_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(
                    GL11.GL_TEXTURE_2D,
                    renderer.minecraft.textureManager.load(
                            net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE));
            GL11.glColor4f(1f, 1f, 1f, .88f);

            int tile = Block.FIRE.textureId;
            float u0 = (tile % 16 + .01f) / 16f, u1 = (tile % 16 + .99f) / 16f;
            float v0 = (tile / 16 + .01f) / 16f, v1 = (tile / 16 + .99f) / 16f;
            GL11.glBegin(GL11.GL_QUADS);
            for (int side = -1; side <= 1; side += 2) {
                GL11.glTexCoord2f(u0, v1);
                GL11.glVertex2f(side * 1.20f, -1.10f);
                GL11.glTexCoord2f(u1, v1);
                GL11.glVertex2f(side * -.11f, -1.10f);
                GL11.glTexCoord2f(u1, v0);
                GL11.glVertex2f(side * .40f, -.22f);
                GL11.glTexCoord2f(u0, v0);
                GL11.glVertex2f(side * 1.25f, -.01f);
            }
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
