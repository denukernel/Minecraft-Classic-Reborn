package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.gui.FontRenderer;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

import org.lwjgl.opengl.GL11;

public final class MobNameRenderer {
    private static final float MAX_DISTANCE = 64f;
    private static final float TEXT_SCALE = .025f;

    private MobNameRenderer() {
    }

    public static void render(Minecraft game, CameraView camera, Frustrum frustum, float partial) {
        Vec3D eye = camera.position();
        for (Entity entity : game.level.blockMap.all) {
            if (!(entity instanceof Mob mob) || mob instanceof Player || mob.removed
                    || mob.health <= 0 || mob.bb == null || mob.customName == null
                    || mob.customName.isBlank())
                continue;

            float x = mob.xo + (mob.x - mob.xo) * partial;
            float y = mob.bb.y1 + (mob.yo - mob.y) * (1 - partial) + .25f;
            float z = mob.zo + (mob.z - mob.zo) * partial;
            float dx = x - eye.x;
            float dy = y - eye.y;
            float dz = z - eye.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE)
                continue;

            int width = game.fontRenderer.getLiteralWidth(mob.customName);
            float extent = (width * .5f + 12) * TEXT_SCALE;
            if (frustum.isBoxInFrustrum(x - extent, y - extent, z - extent, x + extent, y + extent,
                    z + extent)) {
                draw(game.fontRenderer, camera, mob.customName, width, x, y, z);
            }
        }
    }

    private static void draw(FontRenderer font, CameraView camera, String name, int width, float x,
            float y, float z) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, z);
            GL11.glRotatef(-camera.yaw(), 0, 1, 0);
            GL11.glRotatef(-camera.pitch(), 1, 0, 0);
            GL11.glScalef(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);

            int left = -width / 2;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(0, 0, 0, .6f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(left - 2, -11);
            GL11.glVertex2f(left - 2, 0);
            GL11.glVertex2f(left + width + 2, 0);
            GL11.glVertex2f(left + width + 2, -11);
            GL11.glEnd();

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            font.renderLiteral(name, left, -10, 0xffffff);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
