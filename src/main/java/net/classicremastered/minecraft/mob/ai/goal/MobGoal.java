package net.classicremastered.minecraft.mob.ai.goal;

import net.classicremastered.minecraft.mob.ai.BasicAI;

public abstract class MobGoal<T extends BasicAI> extends Goal {

    protected final T ai;
    protected MobGoal(T ai) {
        this.ai = ai;
    }

    @Override
    public boolean canStart() {
        return ai.level != null && ai.mob != null && ai.mob.health > 0;
    }
}
