// file: src/com/mojang/minecraft/mob/ai/SkeletonArrowAI.java
package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

public class SkeletonArrowAI extends BasicAI {
    public float preferredMin  = 4.0F;
    public float preferredMax  = 16.0F;
    public float walkSpeed     = 0.025F;

    public int windupTicks     = 8;
    public int cooldownTicks   = 40;
    public float arrowSpeed    = 1.2F;

    private int windup   = 0;
    private int cooldown = 0;

    public void tick(Level level, Mob mob) {
        super.tick(level, mob);
        if (level.creativeMode) return;

        Player player = level.getNearestPlayer(mob.x, mob.y, mob.z, 32.0F);
        if (player == null || player.removed || player.health <= 0) return;

        // Face the player
        faceSmooth(mob, player.x - mob.x, player.z - mob.z);

        float dx = player.x - mob.x;
        float dz = player.z - mob.z;
        float dist = MathHelper.sqrt(dx * dx + dz * dz);

        if (dist < preferredMin) {
            mob.moveRelative(0.0F, -1.0F, walkSpeed);
            windup = 0;
            return;
        }
        if (dist > preferredMax) {
            mob.moveRelative(0.0F, 1.0F, walkSpeed);
            windup = 0;
            return;
        }

        if (cooldown > 0) { cooldown--; return; }

        if (windup < windupTicks) {
            windup++;
            mob.attackTime = Math.max(mob.attackTime, Mob.ATTACK_DURATION);
            return;
        }

        shootArrow(level, mob, player);
        mob.attackTime = Mob.ATTACK_DURATION;
        windup = 0;
        cooldown = cooldownTicks;
    }

    private void shootArrow(Level level, Mob mob, Player target) {
        float dx = target.x - mob.x;
        float dz = target.z - mob.z;
        float dy = (target.y + target.heightOffset) - (mob.y + mob.heightOffset);
        float horiz = MathHelper.sqrt(dx * dx + dz * dz);

        float yaw   = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float)(-Math.atan2(dy, horiz) * 180.0D / Math.PI);

        float yawRad   = yaw   * ((float)Math.PI / 180F);
        float pitchRad = pitch * ((float)Math.PI / 180F);

        float cosYaw   = MathHelper.cos(yawRad);
        float sinYaw   = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        float forward = 0.8F;
        float up      = mob.heightOffset;

        float offX = -sinYaw * cosPitch * forward;
        float offY = -sinPitch * forward + up;
        float offZ = cosYaw * cosPitch * forward;

        level.addEntity(new Arrow(level, mob,
            mob.x + offX, mob.y + offY, mob.z + offZ,
            yaw, pitch, arrowSpeed
        ));
    }

    private void faceSmooth(Mob mob, float dx, float dz) {
        float desiredYaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = desiredYaw - mob.yRot;
        while (dyaw < -180.0F) dyaw += 360.0F;
        while (dyaw >= 180.0F) dyaw -= 360.0F;
        mob.yRot += dyaw * 0.25F;
    }
}
