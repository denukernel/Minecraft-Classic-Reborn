package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.ImpactPrimedTnt;
import net.classicremastered.minecraft.entity.PrimedTnt;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.util.MathHelper;

public final class HostileThreats {

    int dodgeTicks;
    float dodgeStrafe;
    Entity primedTnt;

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

        primedTnt = null;
        if (!ai.isTntAvoidanceEnabled() || ai.level == null || ai.mob == null) return;
        float nearest = 36f;
        for (Entity candidate : ai.level.blockMap.getEntities(ai.mob, ai.mob.bb.grow(6, 6, 6))) {
            if (candidate.removed
                    || !(candidate instanceof PrimedTnt || candidate instanceof ImpactPrimedTnt))
                continue;
            float distance = ai.mob.distanceToSqr(candidate);
            if (distance >= nearest) continue;
            Vec3D start = new Vec3D(ai.mob.x, (ai.mob.bb.y0 + ai.mob.bb.y1) * .5f, ai.mob.z);
            if (ai.level.clip(start, new Vec3D(candidate.x, candidate.y, candidate.z), true)
                    == null) {
                nearest = distance;
                primedTnt = candidate;
            }
        }
    }

    private static float wrap(float a) {
        while (a <= -180f)
            a += 360f;
        while (a > 180f)
            a -= 360f;
        return a;
    }
}
