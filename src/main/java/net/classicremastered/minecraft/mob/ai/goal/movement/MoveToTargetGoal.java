package net.classicremastered.minecraft.mob.ai.goal.movement;

import net.classicremastered.minecraft.mob.ai.BasicAI;

public final class MoveToTargetGoal extends MovementInputGoal {

    public MoveToTargetGoal(BasicAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.attackTarget != null && !ai.attackTarget.removed;
    }

    @Override
    public void tick() {
        boolean inWater = ai.mob.isInWater(), inLava = ai.mob.isInLava(); // Face target smoothly
        float dx = ai.attackTarget.x - ai.mob.x;
        float dz = ai.attackTarget.z - ai.mob.z;
        float targetYaw = (float) (Math.atan2((double) dz, (double) dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = targetYaw - ai.mob.yRot;
        while (dyaw < -180.0F)
            dyaw += 360.0F;
        while (dyaw >= 180.0F)
            dyaw -= 360.0F;
        ai.yRotA = dyaw * 0.4F; // steer proportionally toward target

        // Forward movement (runSpeed base; boost if running)
        float fwd = ai.runSpeed * (ai.running ? 1.4F : 1.0F);
        ai.yya = fwd;

        // Light, occasional strafing to avoid getting stuck
        if (ai.random.nextFloat() < 0.06F) {
            ai.xxa = (ai.random.nextFloat() - 0.5F) * ai.runSpeed * 0.5F;
        }

        // Jump logic:
        // - Never random-jump if suppressRandomJump is set (builder climb mode)
        // - Do jump if pushing a wall and on ground
        // - In water/lava, bias to jump for buoyancy unless suppressed
        boolean wantRandomJump = (!ai.suppressRandomJump && ai.random.nextFloat() < 0.04F);
        boolean obstacleJump = (ai.mob.onGround && ai.mob.horizontalCollision);
        boolean mediumJump = (!ai.suppressRandomJump && (inWater || inLava) && ai.random.nextFloat() < 0.8F);

        ai.jumping = obstacleJump || mediumJump || wantRandomJump;

        finishMovement();
    }

}
