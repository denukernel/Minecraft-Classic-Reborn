package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

/** Camera placement only. Player geometry belongs exclusively to the world entity pass. */
public record CameraView(float yaw, float pitch, float distance, Vec3D focus, Vec3D position) {
    public static CameraView resolve(Player player, int mode, float partial) {
        float yaw = player.yRotO + (player.yRot - player.yRotO) * partial;
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
        float distance = 4;
        if (player.level != null)
            for (int sample = 0; sample < 9; sample++) {
                float ox = sample == 8 ? 0 : ((sample & 1) == 0 ? -.12f : .12f);
                float oy = sample == 8 ? 0 : ((sample & 2) == 0 ? -.12f : .12f);
                float oz = sample == 8 ? 0 : ((sample & 4) == 0 ? -.12f : .12f);
                float sx = focus.x + ox, sy = focus.y + oy, sz = focus.z + oz;
                if (player.level.isSolidTile((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz))) {
                    distance = .15f;
                    continue;
                }
                // Level.clip mutates its input vector, so retain scalar coordinates for hit distance.
                MovingObjectPosition hit = player.level.clip(new Vec3D(sx, sy, sz),
                        new Vec3D(sx + dx * 4, sy + dy * 4, sz + dz * 4), true);
                if (hit != null) {
                    float travel = (hit.vec.x - sx) * dx + (hit.vec.y - sy) * dy + (hit.vec.z - sz) * dz;
                    distance = Math.min(distance, Math.max(.15f, travel - .15f));
                }
            }
        return new CameraView(yaw, pitch, distance, focus, focus.add(dx * distance, dy * distance, dz * distance));
    }

    public boolean outsidePlayer(Player player) {
        if (player.bb == null)
            return distance > .65f;
        return distance > .65f && !(position.x > player.bb.x0 - .1f && position.x < player.bb.x1 + .1f
                && position.y > player.bb.y0 - .1f && position.y < player.bb.y1 + .1f && position.z > player.bb.z0 - .1f
                && position.z < player.bb.z1 + .1f);
    }
}
