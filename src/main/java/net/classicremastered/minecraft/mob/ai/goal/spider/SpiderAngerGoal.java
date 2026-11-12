package net.classicremastered.minecraft.mob.ai.goal.spider;

import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.spider.BetaSpiderAI;

public final class SpiderAngerGoal extends MobGoal<BetaSpiderAI> {

    public SpiderAngerGoal(BetaSpiderAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return ai.level != null && ai.mob != null;
    }

    @Override
    public void tick() {
        if (net.classicremastered.minecraft.level.itemstack.MobBattle.target(ai.mob) != null) {
            ai.neutral = false;
            return;
        }
        if (ai.angerTicks > 0) ai.angerTicks--;
        boolean bright = isBright();
        if (bright && ai.angerTicks == 0 && ai.attackTarget != null && ai.random.nextInt(100) == 0)
            ai.attackTarget = null;
        ai.neutral =
                net.classicremastered.minecraft.CombatRules.difficulty(ai.level)
                                == net.classicremastered.minecraft.Difficulty.PEACEFUL
                        || (bright && ai.angerTicks == 0 && ai.attackTarget == null);
        if (ai.neutral) ai.attackTarget = null;
    }

    private boolean isBright() {
        return (ai.mob != null) && (ai.mob.getBrightness(0.0F) >= 0.50F);
    }
}
