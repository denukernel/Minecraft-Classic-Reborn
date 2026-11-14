package net.classicremastered.minecraft.bot.ai;

import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.player.Player;

public class BotPvPAI extends BasicAI {
    private final float reach;
    private final int attackInterval;
    private final boolean sprint;

    public BotPvPAI(Mob bot) {
        this(bot, 2.5f, 20, false);
    }

    protected BotPvPAI(Mob bot, float reach, int attackInterval, boolean sprint) {
        this.mob = bot;
        this.reach = reach;
        this.attackInterval = attackInterval;
        this.sprint = sprint;
    }

    @Override
    protected void update() {
        running = false;
        if (!(level.getPlayer() instanceof Player player)
                || player.removed
                || player.health <= 0
                || player.isSpectator()) {
            clearNavigation();
            holdPosition(mob.yRot);
            return;
        }

        float dx = player.x - mob.x;
        float dz = player.z - mob.z;
        float rise = player.bb.y0 - mob.bb.y0;
        float horizontalDistanceSquared = dx * dx + dz * dz;
        float yaw =
                horizontalDistanceSquared > .001f
                        ? (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f
                        : mob.yRot;
        if (Math.abs(rise) < 1.5f
                && horizontalDistanceSquared <= reach * reach
                && HostileTargets.hasLineOfSight(mob, player)) {
            clearNavigation();
            holdPosition(yaw);
            if (attackDelay == 0) {
                attackDelay = attackInterval;
                mob.attackTime = Mob.ATTACK_DURATION;
                player.hurt(mob, 2);
            }
            return;
        }

        running = sprint;
        navigateTo(player.x, player.bb.y0, player.z, sprint ? 1f : .85f);
        if (!hasActiveRoute() && tryBuild(rise, horizontalDistanceSquared)) {
            clearNavigation();
            holdPosition(yaw);
        }
    }

    protected boolean tryBuild(float rise, float horizontalDistanceSquared) {
        return false;
    }
}
