package net.classicremastered.minecraft.mob.ai.goal.goal;

import net.classicremastered.minecraft.mob.Mob;

public abstract class Goal {
    protected Mob mob;

    public Goal(Mob mob) { this.mob = mob; }

    /** Whether this goal can start right now. */
    public abstract boolean canStart();

    /** Whether to continue after starting. */
    public boolean canContinue() { return false; }

    /** Called when goal begins. */
    public void start() {}

    /** Called each tick while active. */
    public void tick() {}

    /** Called when goal stops. */
    public void stop() {}
}
