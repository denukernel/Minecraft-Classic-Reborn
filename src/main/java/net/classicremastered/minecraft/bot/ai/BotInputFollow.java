package net.classicremastered.minecraft.bot.ai;

import net.classicremastered.minecraft.bot.Bot;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.player.Player;

public class BotInputFollow extends BasicAI {
    public BotInputFollow(Bot bot) {
        this.mob = bot;
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
        float distanceSquared = dx * dx + dz * dz;
        float rise = player.bb.y0 - mob.bb.y0;
        if (distanceSquared <= 2.5f * 2.5f
                && Math.abs(rise) <= 1f
                && HostileTargets.hasLineOfSight(mob, player)) {
            clearNavigation();
            holdPosition(
                    distanceSquared > .001f
                            ? (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f
                            : mob.yRot);
            return;
        }

        running = distanceSquared > 6f * 6f;
        navigateTo(player.x, player.bb.y0, player.z, running ? 1f : .75f);
    }
}
