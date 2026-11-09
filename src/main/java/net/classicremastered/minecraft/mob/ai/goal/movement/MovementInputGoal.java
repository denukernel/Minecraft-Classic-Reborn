package net.classicremastered.minecraft.mob.ai.goal.movement;

import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public abstract class MovementInputGoal extends MobGoal<BasicAI> {

    MovementInputGoal(BasicAI ai) {
        super(ai);
    }

    final void finishMovement() { // Apply heading and look
        ai.mob.yRot += ai.yRotA;
        ai.mob.xRot = (float) ai.defaultLookAngle;

        // If in fluid, bias toward upward motion when jumping (handled in tick())
        // (No direct vertical impulse here; BasicAI.tick() calls
        // jumpFromGround()/buoyancy)

        // Clamp/soften inputs a bit
        if (ai.yya > ai.runSpeed * 1.6F)
            ai.yya = ai.runSpeed * 1.6F;
        if (ai.yya < -ai.runSpeed * 1.6F)
            ai.yya = -ai.runSpeed * 1.6F;
        if (ai.xxa > ai.runSpeed)
            ai.xxa = ai.runSpeed;
        if (ai.xxa < -ai.runSpeed)
            ai.xxa = -ai.runSpeed;

        // Keep noActionTime from drifting when actively moving
        if (Math.abs(ai.yya) > 1e-3F || Math.abs(ai.xxa) > 1e-3F || ai.jumping) {
            ai.noActionTime = 0;
        }
    }
}
