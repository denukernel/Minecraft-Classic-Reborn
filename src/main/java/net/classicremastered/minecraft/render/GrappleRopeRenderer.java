package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.itemstack.GrapplingHookItem;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

import org.lwjgl.opengl.GL11;

/** World-space rope with a hexagonal cross section and a small amount of sag. */
public final class GrappleRopeRenderer {
    private static final int SIDES = 6;
    private static final int SEGMENTS = 12;
    private static final float RADIUS = .026f;

    private GrappleRopeRenderer() {}

    public static void render(Player player, float partial) {
        Vec3D end = GrapplingHookItem.renderEndpoint(player, partial);
        if (end == null) return;
        Vec3D aim = player.aimDirection(partial);
        double yaw = Math.toRadians(player.aimYaw(partial));
        Vec3D start =
                new Vec3D(
                        player.xo
                                + (player.x - player.xo) * partial
                                + aim.x * .5f
                                + (float) Math.cos(yaw) * .26f,
                        player.yo + (player.y - player.yo) * partial + aim.y * .5f - .28f,
                        player.zo
                                + (player.z - player.zo) * partial
                                + aim.z * .5f
                                + (float) Math.sin(yaw) * .26f);
        float length = start.distance(end);
        if (length < .001f) return;
        float dx = (end.x - start.x) / length;
        float dy = (end.y - start.y) / length;
        float dz = (end.z - start.z) / length;
        float horizontal = (float) Math.hypot(dx, dz);
        float ux = horizontal < .001f ? 1 : -dz / horizontal;
        float uz = horizontal < .001f ? 0 : dx / horizontal;
        float vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
        float sag = Math.min(.18f, length * .035f) * horizontal;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            for (int side = 0; side < SIDES; side++) {
                double a = side * Math.PI * 2 / SIDES;
                double b = (side + 1) * Math.PI * 2 / SIDES;
                float shade = .83f + .17f * (float) Math.sin(a);
                GL11.glColor3f(.72f * shade, .49f * shade, .26f * shade);
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                for (int segment = 0; segment <= SEGMENTS; segment++) {
                    float t = segment / (float) SEGMENTS;
                    float x = start.x + (end.x - start.x) * t;
                    float y = start.y + (end.y - start.y) * t - 4 * t * (1 - t) * sag;
                    float z = start.z + (end.z - start.z) * t;
                    vertex(x, y, z, ux, uz, vx, vy, vz, a);
                    vertex(x, y, z, ux, uz, vx, vy, vz, b);
                }
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void vertex(
            float x,
            float y,
            float z,
            float ux,
            float uz,
            float vx,
            float vy,
            float vz,
            double angle) {
        float u = RADIUS * (float) Math.cos(angle);
        float v = RADIUS * (float) Math.sin(angle);
        GL11.glVertex3f(x + ux * u + vx * v, y + vy * v, z + uz * u + vz * v);
    }
}
