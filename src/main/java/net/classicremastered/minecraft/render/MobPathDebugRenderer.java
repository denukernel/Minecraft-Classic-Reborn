package net.classicremastered.minecraft.render;

import java.util.List;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import org.lwjgl.opengl.GL11;

public final class MobPathDebugRenderer {
    private MobPathDebugRenderer() {}

    public static void render(Level level, Vec3D camera, float partial) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glLineWidth(2);
            AABB area =
                    new AABB(
                            camera.x - 48,
                            camera.y - 48,
                            camera.z - 48,
                            camera.x + 48,
                            camera.y + 48,
                            camera.z + 48);
            for (Object entity : level.findEntities(null, area)) {
                if (entity instanceof Mob mob
                        && mob.health > 0
                        && !mob.removed
                        && mob.ai instanceof BasicAI ai) draw(mob, ai, partial);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void draw(Mob mob, BasicAI ai, float partial) {
        List<Vec3D> route = ai.debugRoute();
        Vec3D exit = null;
        if (ai instanceof StickFigureAI boss) {
            var special = boss.movement().debugRoute();
            if (!special.isEmpty()) route = special;
            exit = boss.movement().debugShoreExit();
        }
        Entity target = ai.attackTarget;
        if (route.isEmpty() && exit == null && (target == null || target.removed)) return;

        Vec3D origin =
                new Vec3D(
                        mob.xo + (mob.x - mob.xo) * partial,
                        mob.renderFeetY(partial),
                        mob.zo + (mob.z - mob.zo) * partial);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor3f(0, 1, 1);
        Vec3D previous = origin;
        for (Vec3D node : route) {
            vertex(previous);
            vertex(node);
            marker(node, .12f);
            previous = node;
        }
        if (!route.isEmpty()) {
            GL11.glColor3f(1, 1, 0);
            marker(route.get(0), .24f);
        }
        if (target != null && !target.removed) {
            GL11.glColor3f(1, .25f, .25f);
            marker(new Vec3D(target.x, target.bb.y0, target.z), .3f);
        }
        if (exit != null) {
            GL11.glColor3f(.25f, 1, .25f);
            marker(exit, .4f);
        }
        GL11.glEnd();
    }

    private static void vertex(Vec3D point) {
        GL11.glVertex3f(point.x, point.y + .12f, point.z);
    }

    private static void marker(Vec3D point, float radius) {
        GL11.glVertex3f(point.x - radius, point.y + .12f, point.z);
        GL11.glVertex3f(point.x + radius, point.y + .12f, point.z);
        GL11.glVertex3f(point.x, point.y + .12f, point.z - radius);
        GL11.glVertex3f(point.x, point.y + .12f, point.z + radius);
        vertex(point);
        GL11.glVertex3f(point.x, point.y + .12f + radius * 2, point.z);
    }
}
