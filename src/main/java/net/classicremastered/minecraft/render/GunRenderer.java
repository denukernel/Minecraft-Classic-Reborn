package net.classicremastered.minecraft.render;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.model.GunModel;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

/** The model and beam share this pose, including first-person equip and recoil offsets. */
public final class GunRenderer {
    public record Pose(float x, float y, float z, float yaw, float pitch, float roll, float scale) {
        public void apply() {
            GL11.glTranslatef(x, y, z);
            GL11.glRotatef(yaw, 0, 1, 0);
            GL11.glRotatef(pitch, 1, 0, 0);
            GL11.glRotatef(roll, 0, 0, 1);
            GL11.glScalef(scale, scale, scale);
        }

        public Vec3D transform(Vec3D v) {
            double r = Math.toRadians(roll), p = Math.toRadians(pitch), a = Math.toRadians(yaw);
            double vx = v.x * scale, vy = v.y * scale, vz = v.z * scale;
            double rx = vx * Math.cos(r) - vy * Math.sin(r), ry = vx * Math.sin(r) + vy * Math.cos(r);
            double py = ry * Math.cos(p) - vz * Math.sin(p), pz = ry * Math.sin(p) + vz * Math.cos(p);
            return new Vec3D(x + (float) (rx * Math.cos(a) + pz * Math.sin(a)), y + (float) py,
                    z + (float) (-rx * Math.sin(a) + pz * Math.cos(a)));
        }
    }

    public static Pose firstPersonPose(Player p, float partial) {
        Minecraft mc = p.minecraft;
        HeldBlock held = mc != null && mc.renderer != null ? mc.renderer.heldBlock : null;
        float equip = held == null ? 1 : held.lastPos + (held.pos - held.lastPos) * partial;
        float recoil = 0;
        if (held != null && held.moving) {
            float t = Math.max(0, Math.min(1, (held.offset + partial) / 7f));
            recoil = (float) Math.sin(t * Math.PI);
        }
        return new Pose(.35f + .025f * recoil, -.24f - (1 - equip) * .9f - .025f * recoil, -.86f + .09f * recoil, 8,
                -3 - 5 * recoil, -5, .64f);
    }

    public static Pose thirdPersonPose() {
        return new Pose(.34f, -.38f, -.32f, 0, 0, 0, .68f);
    }

    private static boolean firstPerson(Player p) {
        return p.minecraft != null && p.minecraft.player == p && p.minecraft.cameraMode == 0;
    }

    public static Vec3D cameraToWorld(Player p, Vec3D point, float partial) {
        double yaw = Math.toRadians(p.aimYaw(partial));
        double pitch = Math.toRadians(p.xRotO + (p.xRot - p.xRotO) * partial);
        double y = point.y * Math.cos(pitch) + point.z * Math.sin(pitch);
        double z = -point.y * Math.sin(pitch) + point.z * Math.cos(pitch);
        return new Vec3D(p.xo + (p.x - p.xo) * partial + (float) (point.x * Math.cos(yaw) - z * Math.sin(yaw)),
                p.yo + (p.y - p.yo) * partial + (float) y,
                p.zo + (p.z - p.zo) * partial + (float) (point.x * Math.sin(yaw) + z * Math.cos(yaw)));
    }

    public static Vec3D muzzleWorld(Player p, float partial) {
        boolean first = firstPerson(p);
        Pose pose = first ? firstPersonPose(p, partial) : thirdPersonPose();
        Vec3D muzzle = pose.transform(GunModel.MUZZLE);
        // ClientFrameRenderer offsets the world view by -0.1 Z before setupCamera.
        // The held-item pass does not, so compensate only its world-space beam origin.
        if (first)
            muzzle = muzzle.add(0, 0, .1f);
        return cameraToWorld(p, muzzle, partial);
    }

    public static void renderFirstPerson(Player p, int id, float partial) {
        GL11.glPushMatrix();
        firstPersonPose(p, partial).apply();
        GunModel.render(id, p.getRenderBrightness(partial));
        GL11.glPopMatrix();
    }

    public static void renderWorld(Player p, float partial) {
        if (p.minecraft != null && p.minecraft.player == p && p.minecraft.renderer != null
                && !p.minecraft.renderer.shouldRenderLocalPlayer())
            return;
        int id = p.inventory.getSelected() - 256;
        if (!GunModel.isGun(id) || firstPerson(p))
            return;
        GL11.glPushMatrix();
        GL11.glTranslatef(p.xo + (p.x - p.xo) * partial, p.yo + (p.y - p.yo) * partial, p.zo + (p.z - p.zo) * partial);
        GL11.glRotatef(-p.aimYaw(partial), 0, 1, 0);
        GL11.glRotatef(-(p.xRotO + (p.xRot - p.xRotO) * partial), 1, 0, 0);
        thirdPersonPose().apply();
        GunModel.render(id, p.getRenderBrightness(partial));
        GL11.glPopMatrix();
    }
}
