package net.classicremastered.minecraft.mob.ai.goal.tnt;

import net.classicremastered.minecraft.entity.ImpactPrimedTnt;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.tnt.ThrowTntAI;
import net.classicremastered.util.MathHelper;

public final class ThrowTntGoal extends MobGoal<ThrowTntAI> {

    public ThrowTntGoal(ThrowTntAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.combatActive;
    }

    @Override
    public void tick() {
        if (ai.frenzyCooldown > 0)
            ai.frenzyCooldown--;
        float walk = ai.walkSpeed * (ai.running ? 1.4F : 1.0F);
        float dx = ai.playerTarget.x - ai.mob.x;
        float dz = ai.playerTarget.z - ai.mob.z;
        float flatDist = MathHelper.sqrt(dx * dx + dz * dz);

        if (flatDist < ai.preferredMin) {
            ai.faceSmooth(ai.mob, dx, dz);
            ai.mob.moveRelative(0.0F, -1.0F, walk);
            ai.windup = 0;
            return;
        }
        if (flatDist > ai.preferredMax) {
            ai.accelerateTo(ai.playerTarget, walk);
            ai.windup = 0;
            return;
        }

        if (!ai.hasLineOfSight(ai.level, ai.mob, ai.playerTarget)) {
            ai.accelerateTo(ai.playerTarget, walk * 1.25f);
            ai.windup = 0;
            return;
        }

        ai.faceSmooth(ai.mob, dx, dz);
        if (ai.cooldown > 0) {
            ai.cooldown--;
            return;
        }

        if (ai.windup < ai.windupTicks) {
            ai.windup++;
            ai.mob.attackTime = Math.max(ai.mob.attackTime, Mob.ATTACK_DURATION);
            return;
        }

        // Throw the straight-shot TNT (explodes on ground/player)
        float dy = (ai.playerTarget.y + 1.55F) - (ai.mob.y + ai.mob.heightOffset);
        float pitchUp = ai.clamp(-35F, -5F - flatDist * 1.8F - dy * 1.2F, -5F);

        float yaw = ai.mob.yRot;
        float yawRad = yaw * ((float) Math.PI / 180F);
        float forward = 0.6F, up = 0.6F;

        float offX = -MathHelper.sin(yawRad) * forward;
        float offZ = MathHelper.cos(yawRad) * forward;

        ai.level.addEntity(new net.classicremastered.minecraft.entity.ImpactPrimedTnt(
                ai.level, ai.mob,
                ai.mob.x + offX,
                ai.mob.y + ai.mob.heightOffset + up,
                ai.mob.z + offZ,
                ai.playerTarget.x, ai.playerTarget.y + 1.55F, ai.playerTarget.z,
                ai.throwPower, ai.fuseTicks, ai.preferredMax).setExplodeOnGround(true)
                .setExplodeOnPlayer(true));

        ai.mob.attackTime = Mob.ATTACK_DURATION;
        ai.windup = 0;
        ai.cooldown = ai.cooldownTicks;
    }

}
