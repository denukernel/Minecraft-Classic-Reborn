// file: src/com/mojang/minecraft/mob/ai/PathChaseAI.java
package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.path.PathNavigator;
import net.classicremastered.minecraft.player.Player;

public class PathChaseAI extends BasicAI {

    public static final long serialVersionUID = 0L;

    /** Acquire within this radius. */
    public float chaseRange = 32.0F;

    /** Reused per-level. */
    private transient PathNavigator nav;

    @Override
    public void tick(Level level, Mob mob) {
        // keep references for update()
        this.level = level;
        this.mob   = mob;

        // ensure navigator exists
        if (nav == null) nav = new PathNavigator(level);
        else if (level != this.level) nav = new PathNavigator(level); // safety if worlds switch

        // then let BasicAI handle the frame (calls our update())
        super.tick(level, mob);
    }

    @Override
    protected void update() {
        // --- navigation happens here so BasicAI.tick uses our xxa/yya/jumping ---
        // target: nearest player
        Player target = level.getNearestPlayer(mob.x, mob.y, mob.z, chaseRange);

        if (target == null || target.removed) {
            // idle wander: very light yaw, no forward push
            if (random.nextFloat() < 0.03F)
                this.yRotA = (random.nextFloat() - 0.5F) * 40F;
            mob.yRot += this.yRotA;
            this.xxa = 0.0F;
            this.yya = 0.0F;
            this.jumping = false;
            this.mob.xRot = (float)this.defaultLookAngle;
            return;
        }

        // plan & steer
        nav.setGoal(target.x, target.y, target.z);
        PathNavigator.Result r = nav.steer(mob);

        // face toward the steering yaw (smooth)
        float dyaw = r.targetYaw - mob.yRot;
        while (dyaw < -180F) dyaw += 360F;
        while (dyaw >= 180F) dyaw -= 360F;
        mob.yRot += dyaw * 0.35F;

        // feed Classic-style controls:
        // forward = runSpeed (≈0.7), BasicAI/Mob.travel multiplies by 0.1 on ground
        this.xxa = 0.0F;
        this.yya = r.forward ? (this.runSpeed * (running ? 1.4F : 1.0F)) : 0.0F;

        // jump when a 1-block step is ahead, or when physics says we're kissing a wall
        this.jumping = r.wantJump || (mob.onGround && mob.horizontalCollision);

        // keep head pitch neutral
        this.mob.xRot = (float)this.defaultLookAngle;
    }
}
