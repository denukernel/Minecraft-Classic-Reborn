package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.player.Player;
import org.lwjgl.opengl.GL11;

public final class DeveloperPowerRenderer {
    private DeveloperPowerRenderer() {
    }

    public static void render(Minecraft minecraft, float partial) {
        Player player = minecraft.player;
        if (player == null || !player.developerPowers().active(player))
            return;
        if (minecraft.cameraMode > 0)
            label(minecraft, player, partial, "Game Developer", false);
        if (!player.developerPowers().showMobHealthBar())
            return;
        for (Object entity : minecraft.level.blockMap.all) {
            if (!(entity instanceof Mob mob) || mob instanceof Player || mob.removed || mob.health <= 0)
                continue;
            float dx = mob.x - player.x, dy = mob.y - player.y, dz = mob.z - player.z;
            if (dx * dx + dy * dy + dz * dz <= 32 * 32)
                label(minecraft, mob, partial, mob.health + " HP", true);
        }
    }

    private static int maximumHealth(Mob mob) {
        if (mob instanceof StickFigure)
            return StickFigure.MAX_HEALTH;
        if (mob instanceof FakeHerobrine)
            return 9999;
        if (mob instanceof PlayinusBoss)
            return 300;
        if (mob instanceof MinetrumBoss || mob instanceof IllusionumBoss)
            return 250;
        if (mob instanceof IronGolem)
            return 100;
        if (mob instanceof Enderman || mob instanceof Ironmock)
            return 40;
        if (mob instanceof HouseVandalizer)
            return 30;
        if (mob instanceof Chicken)
            return 4;
        if (mob instanceof Bee || mob instanceof BabySkeleton || mob instanceof SignMob)
            return 10;
        return 20;
    }

    private static void label(Minecraft minecraft, Mob mob, float partial, String text, boolean healthBar) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(mob.xo + (mob.x - mob.xo) * partial,
                    mob.yo + (mob.y - mob.yo) * partial - mob.heightOffset + mob.bbHeight + (healthBar ? .85f : .35f),
                    mob.zo + (mob.z - mob.zo) * partial);
            float yaw = minecraft.player.yRotO + (minecraft.player.yRot - minecraft.player.yRotO) * partial;
            float pitch = minecraft.player.xRotO + (minecraft.player.xRot - minecraft.player.xRotO) * partial;
            GL11.glRotatef(-yaw - (minecraft.cameraMode == 2 ? 180 : 0), 0, 1, 0);
            GL11.glRotatef(minecraft.cameraMode == 2 ? pitch : -pitch, 1, 0, 0);
            GL11.glScalef(.025f, -.025f, .025f);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (healthBar) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(.05f, .05f, .05f, .85f);
                quad(-22, 10, 22, 16);
                GL11.glColor4f(.2f, 1, .35f, 1);
                quad(-21, 11, -21 + 42 * Math.min(1f, (float) mob.health / maximumHealth(mob)), 15);
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            minecraft.fontRenderer.renderNoShadow(text, -minecraft.fontRenderer.getWidth(text) / 2, 0,
                    healthBar ? 0xffffff : 0xffd65c);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void quad(float left, float top, float right, float bottom) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(left, top, 0);
        GL11.glVertex3f(left, bottom, 0);
        GL11.glVertex3f(right, bottom, 0);
        GL11.glVertex3f(right, top, 0);
        GL11.glEnd();
    }
}
