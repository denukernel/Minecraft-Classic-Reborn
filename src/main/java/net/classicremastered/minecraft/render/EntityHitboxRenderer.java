package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import org.lwjgl.opengl.GL11;

public final class EntityHitboxRenderer {
    private EntityHitboxRenderer() {}

    public static void render(Minecraft mc, Vec3D camera, Frustrum frustum, float partial) {
        var all = mc.level.blockMap.all;
        Entity[] entities;
        synchronized (all) {
            entities = all.toArray(new Entity[0]);
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glLineWidth(1.5f);
            GL11.glBegin(GL11.GL_LINES);
            for (Entity entity : entities) {
                if (entity.removed || entity.bb == null || !entity.shouldRender(camera)
                        || entity == mc.player && !mc.renderer.shouldRenderLocalPlayer()
                        || entity instanceof Player player && player.isSpectator())
                    continue;
                float remaining = 1f - partial;
                AABB box = entity.bb.cloneMove((entity.xo - entity.x) * remaining,
                        (entity.yo - entity.y) * remaining, (entity.zo - entity.z) * remaining);
                if (frustum.isBoxInFrustrum(box.x0, box.y0, box.z0, box.x1, box.y1, box.z1)) {
                    GL11.glColor3f(1f, 1f, 1f);
                    drawEdges(box);
                    if (entity instanceof Mob mob)
                        drawLook(mob, box, partial);
                }
            }
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawLook(Mob mob, AABB box, float partial) {
        float x = (box.x0 + box.x1) * .5f;
        float y = box.y0 + mob.getEyeHeight(partial);
        float z = (box.z0 + box.z1) * .5f;
        double yaw = Math.toRadians(mob instanceof Player player
                        ? player.aimYaw(partial) - 180f
                        : mob.renderBodyYaw(partial) + mob.renderHeadYaw(partial));
        double pitch = Math.toRadians(mob.renderHeadPitch(partial));
        float horizontal = 2f * (float) Math.cos(pitch);
        GL11.glColor3f(1f, 0f, 0f);
        drawFace(box, y);
        GL11.glColor3f(0f, 0f, 1f);
        line(x, y, z, x - horizontal * (float) Math.sin(yaw), y - 2f * (float) Math.sin(pitch),
                z + horizontal * (float) Math.cos(yaw));
    }

    private static void drawEdges(AABB box) {
        drawFace(box, box.y0);
        drawFace(box, box.y1);
        line(box.x0, box.y0, box.z0, box.x0, box.y1, box.z0);
        line(box.x1, box.y0, box.z0, box.x1, box.y1, box.z0);
        line(box.x1, box.y0, box.z1, box.x1, box.y1, box.z1);
        line(box.x0, box.y0, box.z1, box.x0, box.y1, box.z1);
    }

    private static void drawFace(AABB box, float y) {
        line(box.x0, y, box.z0, box.x1, y, box.z0);
        line(box.x1, y, box.z0, box.x1, y, box.z1);
        line(box.x1, y, box.z1, box.x0, y, box.z1);
        line(box.x0, y, box.z1, box.x0, y, box.z0);
    }

    private static void line(float x0, float y0, float z0, float x1, float y1, float z1) {
        GL11.glVertex3f(x0, y0, z0);
        GL11.glVertex3f(x1, y1, z1);
    }
}
