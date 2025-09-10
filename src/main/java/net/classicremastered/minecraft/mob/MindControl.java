package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

/** Generic steering helper (kept for reuse; not required if Player handles all control). */
final class MindControl {
    private MindControl(){}

    static void steerPlayerToward(Player p, float tx, float ty, float tz, float nudge) {
        if (p == null) return;
        float dx = tx - p.x, dz = tz - p.z;
        float d  = MathHelper.sqrt(dx*dx + dz*dz);
        if (d < 0.001f) return;

        float targetYaw = (float)(Math.atan2(dz, dx) * 180.0/Math.PI) - 90.0F;
        float yawDelta  = targetYaw - p.yRot;
        while (yawDelta <= -180) yawDelta += 360;
        while (yawDelta >   180) yawDelta -= 360;
        p.yRot += Math.max(-6f, Math.min(6f, yawDelta * 0.25f));

        float nx = dx / d, nz = dz / d;
        p.xd += nx * nudge;
        p.zd += nz * nudge;

        if (ty > p.y + 0.1f && p.onGround) p.yd = 0.12f;
    }
}
