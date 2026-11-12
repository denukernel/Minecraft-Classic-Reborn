package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

/** Camera placement only. Player geometry belongs exclusively to the world entity pass. */
public record CameraView(float yaw, float pitch, float distance, Vec3D focus, Vec3D position) {
    private static final float THIRD_PERSON_DISTANCE = 4f;
    private static final float CAMERA_RADIUS = .15f;

    public static CameraView resolve(Player player, int mode, float partial) {
        float yaw = player.aimYaw(partial);
        float pitch = player.xRotO + (player.xRot - player.xRotO) * partial;
        Vec3D focus = new Vec3D(player.xo + (player.x - player.xo) * partial,
                player.yo + (player.y - player.yo) * partial, player.zo + (player.z - player.zo) * partial);
        if (mode == 0)
            return new CameraView(yaw, pitch, 0, focus, focus);
        if (mode == 2) {
            yaw += 180;
            pitch = -pitch;
        }
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        float dx = (float) (-Math.sin(y) * Math.cos(p)), dy = (float) Math.sin(p),
                dz = (float) (Math.cos(y) * Math.cos(p));
        float distance = clipDistance(player.level, focus, dx, dy, dz);
        return new CameraView(yaw, pitch, distance, focus,
                focus.add(dx * distance, dy * distance, dz * distance));
    }

    private static float clipDistance(Level level, Vec3D focus, float dx, float dy, float dz) {
        if (level == null)
            return THIRD_PERSON_DISTANCE;
        float x = dx * THIRD_PERSON_DISTANCE, y = dy * THIRD_PERSON_DISTANCE,
                z = dz * THIRD_PERSON_DISTANCE;
        Vec3D end = focus.add(x, y, z);
        AABB sweep = new AABB(focus.x, focus.y, focus.z, focus.x, focus.y, focus.z)
                .grow(CAMERA_RADIUS, CAMERA_RADIUS, CAMERA_RADIUS).expand(x, y, z);
        float distance = THIRD_PERSON_DISTANCE;
        // Sweep the camera volume against block shapes, including transparent leaves and glass.
        for (Object candidate : level.getCubes(sweep)) {
            AABB block = (AABB) candidate;
            AABB padded = block.grow(CAMERA_RADIUS, CAMERA_RADIUS, CAMERA_RADIUS);
            if (padded.contains(focus)) {
                if (staysOutside(focus.x, dx, block.x0, block.x1)
                        || staysOutside(focus.y, dy, block.y0, block.y1)
                        || staysOutside(focus.z, dz, block.z0, block.z1))
                    continue;
                return 0f;
            }
            var hit = padded.clip(focus, end);
            if (hit != null)
                distance = Math.min(distance, Math.max(0f, focus.distance(hit.vec) - .01f));
        }
        return distance;
    }

    private static boolean staysOutside(float start, float direction, float min, float max) {
        return start <= min && direction <= 0f || start >= max && direction >= 0f;
    }

    public boolean outsidePlayer(Player player) {
        if (player.bb == null)
            return distance > .65f;
        return distance > .65f && !(position.x > player.bb.x0 - .1f && position.x < player.bb.x1 + .1f
                && position.y > player.bb.y0 - .1f && position.y < player.bb.y1 + .1f && position.z > player.bb.z0 - .1f
                && position.z < player.bb.z1 + .1f);
    }

    CameraView withDistance(float newDistance) {
        if (newDistance >= distance)
            return this;
        float scale = newDistance / distance;
        return new CameraView(yaw, pitch, newDistance, focus, focus.add((position.x - focus.x) * scale,
                (position.y - focus.y) * scale, (position.z - focus.z) * scale));
    }
}
