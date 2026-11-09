package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public class MeleeAttackGoal extends MobGoal<BasicAttackAI> {

    public MeleeAttackGoal(BasicAttackAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        if (ai.attackTarget == null)
            return;
        float dx = ai.attackTarget.x - ai.mob.x, dy = ai.attackTarget.y - ai.mob.y, dz = ai.attackTarget.z - ai.mob.z;
        ai.mob.xRot = Math.max(-60f,
                Math.min(60f, (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))));
        ai.running = true;
        ai.runMultiplier = 1.1f;
        if (ai.withinMeleeReach(ai.attackTarget, 2f) && ai.attackDelay == 0
                && ai.hasLineOfSight(ai.mob, ai.attackTarget))
            ai.attack(ai.attackTarget);
    }

    public boolean hit(Entity target) {
        if (ai.level == null || ai.mob == null)
            return false;
        if (net.classicremastered.minecraft.CombatRules
                .difficulty(ai.level) == net.classicremastered.minecraft.Difficulty.PEACEFUL)
            return false;
        if (net.classicremastered.minecraft.util.CreativeModeHelper.isCreativePlayer(target))
            return false;
        if (!ai.hasLineOfSight(ai.mob, target))
            return false;

        ai.mob.attackTime = 5;
        ai.attackDelay = ai.random.nextInt(20) + 10;

        int dmgRoll = (int) ((ai.random.nextFloat() + ai.random.nextFloat()) / 2.0F * (float) ai.damage + 1.0F);
        if (ai.mob.equipment[4] > 0)
            dmgRoll = Math.max(dmgRoll,
                    net.classicremastered.minecraft.level.itemstack.Equipment.damage(ai.mob.equipment[4]));
        target.hurt(ai.mob, dmgRoll);
        ai.noActionTime = 0;
        return true;
    }
}
