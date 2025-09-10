package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.mob.Mob;

/**
 * Jumping/pouncing variant of BasicAttackAI.
 * Overrides the jump hook to add a gentle forward lunge.
 */
public class JumpAttackAI extends BasicAttackAI {
    public static final long serialVersionUID = 0L;

    public JumpAttackAI() {
        // Slightly slower base to compensate for bursty jumps (optional).
        this.runSpeed *= 0.90F;
    }


    protected boolean shouldJumpNow(Mob vehicle) {
        if (!vehicle.onGround) return false;
        if (this.attackTarget == null) return false;

        // Only pounce 1 in 6 ticks
        return this.random.nextInt(6) == 0;
    }

}
