package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;

/** Gentle following with a stopping band and normal collision/jump physics. */
public final class ForestGolemFollowAI extends BasicAI {
    public static final float FOLLOW_RANGE = 32f;
    private boolean following;

    public ForestGolemFollowAI() {
        runSpeed = 0.7f;
        walkSpeed = 0.08f;
        suppressRandomJump = true;
    }

    @Override public void tick(Level level, Mob mob) {
        // A companion should not use BasicAI random distance despawning.
        noActionTime = 0;
        super.tick(level, mob);
    }

    @Override protected void update() {
        xxa = yya = yRotA = 0;
        jumping = running = false;
        attackTarget = null;
        Entity candidate = level.getPlayer();
        if (!(candidate instanceof Player player) || player.removed || player.health <= 0) {
            following = false;
            return;
        }
        float dx = player.x - mob.x, dz = player.z - mob.z;
        float dy=player.bb.y0-mob.bb.y0;
        float distanceSquared = dx * dx + dz * dz + dy * dy;
        if (distanceSquared > FOLLOW_RANGE * FOLLOW_RANGE) {
            following = false;
            return;
        }
        if (distanceSquared > 2.8f * 2.8f || Math.abs(dy)>0.2f) following = true;
        else if (distanceSquared < 1.8f * 1.8f) following = false;
        float horizontal = (float)Math.sqrt(Math.max(0.001f, dx*dx+dz*dz));
        mob.xRot = Math.max(-25f, Math.min(25f,
            (float)-Math.toDegrees(Math.atan2(player.y - mob.y, horizontal))));
        if (following) navigateTo(player.x,player.bb.y0,player.z,runSpeed);
    }
    @Override protected boolean usesWanderNavigation() { return false; }
}
