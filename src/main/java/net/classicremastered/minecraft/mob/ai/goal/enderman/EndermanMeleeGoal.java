package net.classicremastered.minecraft.mob.ai.goal.enderman;

import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.mob.ai.goal.Goal;
import net.classicremastered.minecraft.model.Vec3D;

import java.util.EnumSet;

public final class EndermanMeleeGoal extends Goal {

    private final EndermanAI ai;

    public EndermanMeleeGoal(EndermanAI ai) {
        this.ai = ai;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canContinue() {
        return canStart();
    }

    private static final float MELEE_REACH = (float) (Math.sqrt(2.04F) - 0.6F);
    private static final float PURSUIT_TELEPORT_RANGE = 16.0F;
    private static final int PURSUIT_TELEPORT_DELAY = 30;

    @Override
    public boolean canStart() {
        return ai.canTargetPlayer() && ai.angry;
    }

    @Override
    public void tick() {
        Enderman enderman = (Enderman) ai.mob;
        if (!(ai.attackTarget instanceof net.classicremastered.minecraft.mob.Mob player)
                || player.health <= 0
                || player.removed) return;
        float dx = player.x - enderman.x;
        float dy = player.bb.y0 - enderman.bb.y0;
        float dz = player.z - enderman.z;
        float dist2 = dx * dx + dy * dy + dz * dz;

        // Face player smoothly
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = targetYaw - enderman.yRot;
        while (dyaw < -180.0F) dyaw += 360.0F;
        while (dyaw >= 180.0F) dyaw -= 360.0F;
        enderman.yRot += dyaw * 0.3F;

        // --- Melee movement ---
        ai.running = true;
        ai.yya = ai.runSpeed * 1.3F;
        enderman.isAttacking = true;

        if (enderman.attackTime <= 0
                && enderman.bb.grow(MELEE_REACH, 0.0F, MELEE_REACH).intersects(player.bb)
                && hasLineOfSight(enderman, player)) {
            player.hurt(enderman, 2);
            enderman.attackTime = Enderman.ATTACK_DURATION;
        }

        if (dist2 > PURSUIT_TELEPORT_RANGE * PURSUIT_TELEPORT_RANGE) {
            if (++ai.farFromTargetTicks >= PURSUIT_TELEPORT_DELAY
                    && ai.teleportCooldown == 0
                    && ai.teleportation().teleportNearPlayer(enderman, player)) {
                ai.navigation().clear();
                ai.farFromTargetTicks = 0;
                return;
            }
        } else {
            ai.farFromTargetTicks = 0;
        }

        ai.navigation().steer(ai, enderman, player, dx, dz);
    }

    @Override
    public void stop() {
        ai.running = false;
        if (ai.mob instanceof Enderman) ((Enderman) ai.mob).isAttacking = false;
        ai.navigation().clear();
    }

    private boolean hasLineOfSight(Enderman from, net.classicremastered.minecraft.mob.Mob to) {
        Vec3D torso = new Vec3D(from.x, from.bb.y0 + 1.5F, from.z);
        Vec3D eyes = new Vec3D(to.x, to.y, to.z);
        return ai.level.clip(torso, eyes, true) == null;
    }
}
