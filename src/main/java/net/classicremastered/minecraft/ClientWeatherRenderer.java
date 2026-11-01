package net.classicremastered.minecraft;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.Renderer;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

/** Draws rain curtains around the current player. */
final class ClientWeatherRenderer {
    private ClientWeatherRenderer() {}

    static void render(Renderer renderer, float partial) {
        if (renderer.minecraft.raining) {
            float rainDelta = partial;
            Renderer rr = renderer;
            Player player = renderer.minecraft.player;
            Level lvl = renderer.minecraft.level;
            int px = (int) player.x;
            int py = (int) player.y;
            int pz = (int) player.z;
            ShapeRenderer sr1 = ShapeRenderer.instance;
            GL11.glDisable(2884);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glBindTexture(3553, renderer.minecraft.textureManager.load("/rain.png"));

            for (int rx = px - 5; rx <= px + 5; ++rx) {
                for (int rz = pz - 5; rz <= pz + 5; ++rz) {
                    int highestY = lvl.getHighestTile(rx, rz);
                    int yMin = py - 5;
                    int yMax = py + 5;
                    if (yMin < highestY)
                        yMin = highestY;
                    if (yMax < highestY)
                        yMax = highestY;
                    if (yMin != yMax) {
                        float anim = ((float) ((rr.levelTicks + rx * 3121
                                + rz * 418711) % 32) + rainDelta) / 32.0F;
                        float dx = (float) rx + 0.5F - player.x;
                        float dz = (float) rz + 0.5F - player.z;
                        float fade = MathHelper.sqrt(dx * dx + dz * dz) / 5.0F;
                        GL11.glColor4f(1.0F, 1.0F, 1.0F,
                                (1.0F - fade * fade) * 0.7F);
                        sr1.begin();
                        sr1.vertexUV((float) rx, (float) yMin, (float) rz, 0.0F,
                                (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                        sr1.vertexUV((float) (rx + 1), (float) yMin,
                                (float) (rz + 1), 2.0F,
                                (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                        sr1.vertexUV((float) (rx + 1), (float) yMax,
                                (float) (rz + 1), 2.0F,
                                (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                        sr1.vertexUV((float) rx, (float) yMax, (float) rz, 0.0F,
                                (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                        sr1.vertexUV((float) rx, (float) yMin, (float) (rz + 1),
                                0.0F, (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                        sr1.vertexUV((float) (rx + 1), (float) yMin, (float) rz,
                                2.0F, (float) yMin * 2.0F / 8.0F + anim * 2.0F);
                        sr1.vertexUV((float) (rx + 1), (float) yMax, (float) rz,
                                2.0F, (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                        sr1.vertexUV((float) rx, (float) yMax, (float) (rz + 1),
                                0.0F, (float) yMax * 2.0F / 8.0F + anim * 2.0F);
                        sr1.end();
                    }
                }
            }

            GL11.glEnable(2884);
            GL11.glDisable(3042);
        }
    }
}
