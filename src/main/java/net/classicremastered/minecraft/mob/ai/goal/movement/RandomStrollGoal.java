package net.classicremastered.minecraft.mob.ai.goal.movement;

import net.classicremastered.minecraft.mob.ai.BasicAI;

public final class RandomStrollGoal extends MovementInputGoal {

    public RandomStrollGoal(BasicAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        boolean inWater = ai.mob.isInWater(), inLava = ai.mob.isInLava(); // ===== No target: wander =====

        // Occasionally pick a new wander vector
        if (ai.random.nextFloat() < 0.07F) {
            // Forward between [0..runSpeed], slight strafe
            ai.yya = ai.random.nextFloat() * ai.runSpeed;
            ai.xxa = (ai.random.nextFloat() - 0.5F) * ai.runSpeed * 0.5F;
        } else {
            // Gentle decay toward zero
            ai.yya *= 0.95F;
            ai.xxa *= 0.95F;
        }

        // Occasionally adjust heading a bit
        if (ai.random.nextFloat() < 0.04F) {
            // ±30° nudge
            ai.yRotA = (ai.random.nextFloat() - 0.5F) * 60.0F * 0.5F;
        } else {
            // decay heading change
            ai.yRotA *= 0.9F;
        }

        // Jump logic in wander:
        // - obstacle jump if pushing a wall
        // - medium-driven jump (water/lava) unless suppressed
        // - small random chance unless suppressed
        boolean obstacleJump = (ai.mob.onGround && ai.mob.horizontalCollision);
        boolean mediumJump = (!ai.suppressRandomJump && (inWater || inLava) && ai.random.nextFloat() < 0.8F);
        boolean randJump = (!ai.suppressRandomJump && ai.random.nextFloat() < 0.01F);

        ai.jumping = obstacleJump || mediumJump || randJump;
        finishMovement();
    }

}
