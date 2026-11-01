package net.classicremastered.minecraft;

import net.classicremastered.minecraft.model.ModelPart;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.HeldBlock;
import net.classicremastered.minecraft.render.Renderer;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

/** Draws the first-person arm and held block after the world passes. */
final class ClientFirstPersonRenderer {
    private ClientFirstPersonRenderer() {}

    static void render(Renderer renderer, float partial, int stereoEye) {
        if (renderer.minecraft.cameraMode != 0) return;
        Player camPlayer;
        GL11.glClear(256);
        GL11.glLoadIdentity();
        if (renderer.minecraft.settings.anaglyph) {
            GL11.glTranslatef((float) ((stereoEye << 1) - 1) * 0.1F, 0.0F, 0.0F);
        }

        renderer.hurtEffect(partial);
        if (renderer.minecraft.settings.viewBobbing) {
            renderer.applyBobbing(partial);
        }

        if (renderer.minecraft.cameraMode == 0) {
            renderer.setLighting(true);
            renderer.renderHeldItem(partial);
            renderer.setLighting(false);
        }
    }
}
