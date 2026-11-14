package net.classicremastered.minecraft.mob.ai.goal.enderman;

import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.mob.ai.goal.Goal;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

import java.util.EnumSet;

public final class EndermanTargetGoal extends Goal {

    private final EndermanAI ai;

    public EndermanTargetGoal(EndermanAI ai) {
        this.ai = ai;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canContinue() {
        return canStart();
    }

    @Override
    public boolean canStart() {
        return ai.canTargetPlayer();
    }

    @Override
    public void tick() {
        net.classicremastered.minecraft.mob.Mob player =
                net.classicremastered.minecraft.mob.ai.combat.HostileTargets.nearestForPursuit(
                        ai.mob, 64, ai.attackTarget);
        if (player == null) {
            ai.setPursuitTarget(null);
            ai.running = false;
            ((Enderman) ai.mob).isAttacking = false;
            ai.farFromTargetTicks = 0;
            ai.navigation().clear();
            if (!ai.hasPlayerSearch()) ai.clearTarget();
            return;
        }
        if (!ai.angry
                && (player instanceof net.classicremastered.minecraft.mob.Defender
                        || player instanceof Player p
                                && isPlayerLookingAtMe(p, (Enderman) ai.mob))) {
            ai.angry = true;
            ai.navigation().clear();
        }
        if (ai.angry) ai.setPursuitTarget(player);
        else ai.clearTarget();
    }

    @Override
    public void stop() {
        ai.clearTarget();
    }

    private boolean isPlayerLookingAtMe(Player player, Enderman mob) {
        Vec3D eyes = new Vec3D(mob.x, mob.bb.y1 - 0.35F, mob.z);
        Vec3D camera = new Vec3D(player.x, player.y, player.z);
        float distance = camera.distance(eyes);
        if (distance < 0.0001F || distance > 64.0F) {
            return false;
        }

        Vec3D aim = player.aimDirection(1);
        double dot =
                ((eyes.x - camera.x) * aim.x
                                + (eyes.y - camera.y) * aim.y
                                + (eyes.z - camera.z) * aim.z)
                        / distance;
        if (dot <= 1.0 - 0.025 / distance) {
            return false;
        }

        // Level.clip limits the number of cells per trace.
        int segments = (int) Math.ceil(distance / 8.0F);
        Vec3D start = camera;
        for (int i = 1; i <= segments; i++) {
            float fraction = (float) i / segments;
            Vec3D end =
                    new Vec3D(
                            camera.x + (eyes.x - camera.x) * fraction,
                            camera.y + (eyes.y - camera.y) * fraction,
                            camera.z + (eyes.z - camera.z) * fraction);
            if (ai.level.clip(new Vec3D(start.x, start.y, start.z), end, true) != null) {
                return false;
            }
            start = end;
        }
        return true;
    }
}
