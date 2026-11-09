package net.classicremastered.minecraft.mob.ai.goal.creeper;

import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.mob.ai.creeper.CreeperAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public final class CreeperSwellGoal extends MobGoal<CreeperAI> {

    public CreeperSwellGoal(CreeperAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        if (ai.mob instanceof Creeper c && c.ignited) {
            ai.xxa = ai.yya = ai.yRotA = 0;
            ai.jumping = ai.running = false;
        }
    }

}
