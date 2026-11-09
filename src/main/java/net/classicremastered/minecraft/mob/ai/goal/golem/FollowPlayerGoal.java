package net.classicremastered.minecraft.mob.ai.goal.golem;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.golem.ForestGolemFollowAI;
import net.classicremastered.minecraft.player.Player;

public final class FollowPlayerGoal extends MobGoal<ForestGolemFollowAI> {

    public FollowPlayerGoal(ForestGolemFollowAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        ai.xxa = ai.yya = ai.yRotA = 0;
        ai.jumping = ai.running = false;
        ai.attackTarget = null;
        Entity candidate = ai.level.getPlayer();
        if (!(candidate instanceof Player player) || player.removed || player.health <= 0) {
            ai.following = false;
            return;
        }
        float dx = player.x - ai.mob.x, dz = player.z - ai.mob.z;
        float dy = player.bb.y0 - ai.mob.bb.y0;
        float distanceSquared = dx * dx + dz * dz + dy * dy;
        if (distanceSquared > ai.FOLLOW_RANGE * ai.FOLLOW_RANGE) {
            ai.following = false;
            return;
        }
        if (distanceSquared > 2.8f * 2.8f || Math.abs(dy) > 0.2f)
            ai.following = true;
        else if (distanceSquared < 1.8f * 1.8f)
            ai.following = false;
        float horizontal = (float) Math.sqrt(Math.max(0.001f, dx * dx + dz * dz));
        ai.mob.xRot = Math.max(-25f, Math.min(25f,
                (float) -Math.toDegrees(Math.atan2(player.y - ai.mob.y, horizontal))));
        if (ai.following)
            ai.navigateTo(player.x, player.bb.y0, player.z, ai.runSpeed);
    }

}
