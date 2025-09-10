// com/mojang/minecraft/mob/ai/BossAttackAI.java  (optional helper)
package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.mob.BossZombieBase;
import net.classicremastered.minecraft.player.Player;

public final class BossAttackAI {
    private BossAttackAI() {}

    /** Acquire nearest player within R; null if none. */
    public static Player target(BossZombieBase b, float R) {
        if (b == null || b.level == null) return null;
        return b.level.getNearestPlayer(b.x, b.y, b.z, R);
    }

    /** Face (x,z) smoothly. */
    public static void face(BossZombieBase b, float tx, float tz, float rateDeg) {
        float yaw = (float)(Math.atan2(tz - b.z, tx - b.x) * 180.0/Math.PI) - 90.0F;
        float dyaw = yaw - b.yRot;
        while (dyaw < -180) dyaw += 360;
        while (dyaw >  180) dyaw -= 360;
        b.yRot += Math.max(-rateDeg, Math.min(rateDeg, dyaw));
    }
}
