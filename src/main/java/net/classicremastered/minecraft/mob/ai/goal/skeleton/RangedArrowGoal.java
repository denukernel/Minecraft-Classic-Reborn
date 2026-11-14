package net.classicremastered.minecraft.mob.ai.goal.skeleton;

import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.skeleton.SkeletonArrowAI;
import net.classicremastered.util.MathHelper;

public final class RangedArrowGoal extends MobGoal<SkeletonArrowAI> {

    public RangedArrowGoal(SkeletonArrowAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return ai.level != null && ai.mob != null;
    }

    @Override
    public void tick() {

        Mob battleTarget = net.classicremastered.minecraft.level.itemstack.MobBattle.target(ai.mob);

        Mob player =
                battleTarget != null
                        ? battleTarget
                        : net.classicremastered.minecraft.mob.ai.combat.HostileTargets
                                .nearestForPursuit(ai.mob, 20, ai.attackTarget);
        ai.setPursuitTarget(player);
        if (player == null || player.removed || player.health <= 0) {
            ai.windup = 0;
            return;
        }

        // Face the player
        faceSmooth(ai.mob, player.x - ai.mob.x, player.z - ai.mob.z);

        float dx = player.x - ai.mob.x;
        float dz = player.z - ai.mob.z;
        float dist = MathHelper.sqrt(dx * dx + dz * dz);

        if (dist < ai.preferredMin) {
            ai.mob.moveRelative(0.0F, -1.0F, ai.walkSpeed);
            ai.windup = 0;
            return;
        }
        if (dist > ai.preferredMax) {
            ai.mob.moveRelative(0.0F, 1.0F, ai.walkSpeed);
            ai.windup = 0;
            return;
        }

        if (ai.cooldown > 0) {
            ai.cooldown--;
            return;
        }

        if (ai.windup < Math.max(20, ai.windupTicks)) {
            ai.windup++;
            ai.mob.attackTime = Math.max(ai.mob.attackTime, Mob.ATTACK_DURATION);
            return;
        }

        shootArrow(ai.level, ai.mob, player);
        ai.mob.attackTime = Mob.ATTACK_DURATION;
        ai.windup = 0;
        ai.cooldown = ai.cooldownTicks;
    }

    private void shootArrow(Level level, Mob mob, Mob target) {
        float dx = target.x - mob.x;
        float dz = target.z - mob.z;
        float dy = (target.y + target.heightOffset) - (mob.y + mob.heightOffset);
        float horiz = MathHelper.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-Math.atan2(dy, horiz) * 180.0D / Math.PI);

        float yawRad = yaw * ((float) Math.PI / 180F);
        float pitchRad = pitch * ((float) Math.PI / 180F);

        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        float forward = 0.8F;
        float up = mob.heightOffset;

        float offX = -sinYaw * cosPitch * forward;
        float offY = -sinPitch * forward + up;
        float offZ = cosYaw * cosPitch * forward;

        level.addEntity(
                new Arrow(
                        level,
                        mob,
                        mob.x + offX,
                        mob.y + offY,
                        mob.z + offZ,
                        yaw + 180f,
                        pitch,
                        ai.arrowSpeed));
    }

    private void faceSmooth(Mob mob, float dx, float dz) {
        float desiredYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = desiredYaw - mob.yRot;
        while (dyaw < -180.0F) dyaw += 360.0F;
        while (dyaw >= 180.0F) dyaw -= 360.0F;
        mob.yRot += dyaw * 0.25F;
    }
}
