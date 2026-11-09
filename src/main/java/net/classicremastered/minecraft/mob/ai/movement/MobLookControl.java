package net.classicremastered.minecraft.mob.ai.movement;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.model.Vec3D;

/**
 * Eye-level tracking independent of navigation, adapted from the modern look
 * controller.
 */
public final class MobLookControl {

    private float yaw, oldYaw, pitch, oldPitch;
    private boolean initialized;
    private Entity idleTarget;
    private int idleTicks;

    public static boolean supports(Mob mob) {
        return mob.ai instanceof BasicAttackAI
                || mob instanceof Enderman
                || mob instanceof net.classicremastered.minecraft.mob.Villager villager
                        && villager.isHostile();
    }

    public void tick(Mob mob) {
        if (!initialized) {
            yaw = oldYaw = mob.yBodyRot;
            pitch = oldPitch = mob.xRot;
            initialized = true;
        }
        oldYaw = yaw;
        oldPitch = pitch;
        if (mob.health <= 0 || mob.removed)
            return;
        Entity target = mob.ai instanceof BasicAI ai ? ai.attackTarget : null;
        if (!alive(target) || target.level != mob.level)
            target = null;
        if (target == null) {
            if (--idleTicks <= 0 || !visible(mob, idleTarget) || distance(mob, idleTarget) > 64)
                idleTarget = null;
            if (idleTarget == null && mob.level.random.nextFloat() < .02f) {
                Entity nearby = mob.level.getNearestPlayer(mob.x, mob.y, mob.z, 8);
                if (visible(mob, nearby)) {
                    idleTarget = nearby;
                    idleTicks = 40 + mob.level.random.nextInt(40);
                }
            }
            target = idleTarget;
        } else {
            idleTarget = null;
        }
        float wantedYaw = mob.yBodyRot, wantedPitch = 0;
        if (target != null) {
            float dx = target.x - mob.x, dz = target.z - mob.z;
            float dy = target.bb.y0 + target.bbHeight * .85f - (mob.bb.y0 + mob.bbHeight * .85f);
            if (dx * dx + dz * dz > .00001f)
                wantedYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            wantedPitch = Math.max(
                    -40,
                    Math.min(
                            40,
                            (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))));
        }
        yaw += Math.max(-30, Math.min(30, wrap(wantedYaw - yaw)));
        yaw = mob.yBodyRot + Math.max(-75, Math.min(75, wrap(yaw - mob.yBodyRot)));
        pitch += Math.max(-30, Math.min(30, wantedPitch - pitch));
        oldYaw = yaw - wrap(yaw - oldYaw);
    }

    public float yaw(Mob mob, float partial) {
        float body = mob.yBodyRotO + (mob.yBodyRot - mob.yBodyRotO) * partial;
        return initialized ? oldYaw + (yaw - oldYaw) * partial - body : 0;
    }

    public float pitch(float partial) {
        return oldPitch + (pitch - oldPitch) * partial;
    }

    private static boolean alive(Entity entity) {
        return entity != null
                && !entity.removed
                && (!(entity instanceof Mob mob) || mob.health > 0);
    }

    private static float distance(Mob mob, Entity target) {
        if (target == null)
            return Float.MAX_VALUE;
        float dx = mob.x - target.x, dy = mob.y - target.y, dz = mob.z - target.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean visible(Mob mob, Entity target) {
        return alive(target)
                && target.bb != null
                && mob.level.clip(
                        new Vec3D(mob.x, mob.bb.y0 + mob.bbHeight * .85f, mob.z),
                        new Vec3D(
                                target.x, target.bb.y0 + target.bbHeight * .85f, target.z),
                        true) == null;
    }

    private static float wrap(float angle) {
        return angle - (float) Math.floor((angle + 180) / 360) * 360;
    }
}
