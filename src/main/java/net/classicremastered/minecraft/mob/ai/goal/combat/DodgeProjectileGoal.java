package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public final class DodgeProjectileGoal extends MobGoal<BasicAttackAI> {

    public DodgeProjectileGoal(BasicAttackAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.isArrowDodgingEnabled() && ai.threats().dodgeTicks > 0;
    }

    @Override
    public void tick() {
        ai.evading = true;
        ai.threats().dodgeTicks--;
        ai.running = true;
        ai.yya = 0.6f;
        ai.xxa = ai.threats().dodgeStrafe;
        // strafe set earlier; small random hop to help break line
        if (ai.mob.onGround && ai.random.nextFloat() < 0.1f)
            ai.jumping = true;
        return;
    }

}
