package net.classicremastered.minecraft.mob.ai.goal.ironmock;

import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.ironmock.IronmockAI;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class IronmockAttackGoal extends MeleeAttackGoal {

    public IronmockAttackGoal(IronmockAI ai) {
        super(ai);
    }

    @Override
    public boolean hit(Entity target) {
        IronmockAI ai = (IronmockAI) this.ai;
        if (ai.mob == null || ai.level == null || target == null || target.removed || ai.mob.health <= 0
                || ai.attackDelay > 0 || ai.difficulty() == Difficulty.PEACEFUL
                || CreativeModeHelper.shouldIgnoreTarget(target)
                || target instanceof Mob && ((Mob) target).health <= 0)
            return false;
        float dx = target.x - ai.mob.x, dy = target.y - ai.mob.y, dz = target.z - ai.mob.z;
        if (dx * dx + dy * dy + dz * dz >= 4f || !ai.hasLineOfSight(ai.mob, target))
            return false;
        ai.attackDelay = 25;
        ai.mob.attackTime = 8;
        ai.noActionTime = 0;
        ai.damage = ai.difficulty() == Difficulty.EASY ? 4 : ai.difficulty() == Difficulty.HARD ? 9 : 6;
        target.hurt(ai.mob, ai.damage);
        return true;
    }
}
