package net.classicremastered.minecraft.mob.ai.goal.enderman;

import net.classicremastered.minecraft.level.itemstack.MobBattle;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.mob.ai.goal.Goal;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

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
        Mob battleTarget = MobBattle.target(ai.mob);
        if (validTarget(battleTarget)) ai.becomeAngryAt(battleTarget);
        Mob target = ai.angerTarget();
        if (validTarget(target)) {
            if (HostileTargets.canLocate(ai.mob, target)) {
                ai.setPursuitTarget(target);
                return;
            }
            ai.setPursuitTarget(null);
            if (ai.hasPlayerSearch()) {
                ai.running = false;
                ((Enderman) ai.mob).isAttacking = false;
                ai.farFromTargetTicks = 0;
                ai.navigation().clear();
                return;
            }
        }
        if (ai.angry || ai.attackTarget != null) ai.clearTarget();
        if (ai.level.getPlayer() instanceof Player player
                && validTarget(player)
                && isPlayerLookingAtMe(player, (Enderman) ai.mob)) {
            ai.becomeAngryAt(player);
            ai.setPursuitTarget(player);
        }
    }

    private boolean validTarget(Mob target) {
        return target != null
                && target != ai.mob
                && target.level == ai.level
                && target.health > 0
                && !target.removed
                && !CreativeModeHelper.shouldIgnoreTarget(target)
                && ai.mob.distanceToSqr(target) <= 64 * 64;
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
