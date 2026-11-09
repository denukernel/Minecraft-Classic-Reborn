package net.classicremastered.minecraft.mob.ai.goal.tnt;

import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.tnt.ThrowTntAI;
import net.classicremastered.util.MathHelper;

public final class TntFrenzyGoal extends MobGoal<ThrowTntAI> {

    public TntFrenzyGoal(ThrowTntAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.combatActive && (ai.inFrenzy || ai.frenzyCooldown <= 0);
    }

    @Override
    public void tick() {
        if (!ai.inFrenzy) {
            ai.startFrenzy();
            return;
        }
        float runFrenzy = ai.walkSpeed * 1.4F * ai.frenzySprintMultiplier;
        ai.frenzyTicks++;

        // sprint toward player
        float dx = ai.playerTarget.x - ai.mob.x, dz = ai.playerTarget.z - ai.mob.z;
        float flatDist = MathHelper.sqrt(dx * dx + dz * dz);
        ai.accelerateTo(ai.playerTarget, runFrenzy);

        if (flatDist <= ai.meleeRange) {
            if (ai.frenzyInterval > 0)
                ai.frenzyInterval--;
            else if (ai.frenzyHitsLeft > 0) {
                ai.playerTarget.hurt(ai.mob, 1);
                ai.mob.attackTime = 5;
                ai.frenzyHitsLeft--;
                ai.frenzyInterval = ai.frenzyIntervalTicks;
            }
        }

        if (ai.frenzyHitsLeft <= 0 || ai.frenzyTicks >= ai.frenzySafetyMaxTicks) {
            ai.endFrenzy();
        }
        return;
    }

}
