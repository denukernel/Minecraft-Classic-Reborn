package net.classicremastered.minecraft.mob.ai.goal.combat;

import java.util.List;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.util.MathHelper;

public final class HostileThreats {

    int dodgeTicks, avoidTicks;
    float dodgeStrafe;
    public void scan(BasicAttackAI ai) {
        ai.evading = false;
        // Ground navigation validates support ahead without random yaw nudges.

        // Arrow dodge: if an arrow within 6 blocks approaches, strafe for a short burst
        if (ai.isArrowDodgingEnabled() && ai.level != null && ai.mob != null) {
            java.util.List list = ai.level.findEntities(ai.mob, ai.mob.bb.grow(6.0F, 3.0F, 6.0F));
            if (list != null) {
                for (Object o : list) {
                    if (!(o instanceof Arrow))
                        continue;
                    Arrow a = (Arrow) o;
                    if (a.removed || a.isEmbedded() || a.getOwner() == ai.mob)
                        continue;
                    if (ai.mob instanceof net.classicremastered.minecraft.mob.Skeleton
                            && a.getOwner() instanceof net.classicremastered.minecraft.mob.Skeleton)
                        continue;
                    float ax = a.x - ai.mob.x, az = a.z - ai.mob.z;
                    float velocitySquared = a.xd * a.xd + a.zd * a.zd;
                    float approach = ax * a.xd + az * a.zd;
                    if (velocitySquared < 0.0001f || approach >= 0)
                        continue;
                    float impactTicks = -approach / velocitySquared;
                    if (impactTicks > 12f)
                        continue;
                    float missX = ax + a.xd * impactTicks, missZ = az + a.zd * impactTicks;
                    if (missX * missX + missZ * missZ > 1.5f * 1.5f)
                        continue;
                    float v = MathHelper.sqrt(ax * ax + az * az);
                    if (v < 0.001f)
                        continue;
                    // approximate “coming at me” if arrow is within a forward cone
                    float yawToArrow = (float) (Math.atan2((double) az, (double) ax) * 180.0D / Math.PI) - 90.0F;
                    float dyaw = wrap(yawToArrow - ai.mob.yRot);
                    if (Math.abs(dyaw) < 50.0f) {
                        dodgeTicks = 10 + ai.random.nextInt(8);
                        // pick left/right opposite of dyaw sign
                        dodgeStrafe = (dyaw >= 0 ? -1.0f : 1.0f);
                    }
                }
            }
        }

        // TNT avoidance (static): if TNT block within 3 blocks, back away a bit
        if (ai.isTntAvoidanceEnabled() && ai.level != null && ai.mob != null) {
            int bx = fi(ai.mob.x), by = fi(ai.mob.y), bz = fi(ai.mob.z);
            final int R = 3;
            boolean nearTnt = false;
            for (int dy = -R; dy <= R && !nearTnt; dy++)
                for (int dz = -R; dz <= R && !nearTnt; dz++)
                    for (int dx = -R; dx <= R && !nearTnt; dx++) {
                        int id = ai.level.getTile(bx + dx, by + dy, bz + dz);
                        if (id == Block.TNT.id)
                            nearTnt = true;
                    }
            if (nearTnt) {
                avoidTicks = 12;
                // gentle backpedal
                ai.yya = -0.6f;
            }
        }

    }

    private static int fi(float f) {
        return (int) Math.floor(f);
    }

    private static float wrap(float a) {
        while (a <= -180f)
            a += 360f;
        while (a > 180f)
            a -= 360f;
        return a;
    }
}
