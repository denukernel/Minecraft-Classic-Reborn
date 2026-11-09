package net.classicremastered.minecraft.mob.ai.goal;

import java.util.EnumSet;
import net.classicremastered.minecraft.mob.Mob;

public abstract class Goal {

    public enum Flag {
        MOVE, LOOK, JUMP, TARGET
    }
    protected Mob mob;
    private EnumSet<Flag> flags = EnumSet.allOf(Flag.class);
    protected Goal() {
    }

    public Goal(Mob mob) {
        this.mob = mob;
    }

    public final void setFlags(EnumSet<Flag> flags) {
        this.flags = flags.clone();
    }

    public final EnumSet<Flag> getFlags() {
        return flags.clone();
    }

    public boolean isInterruptible() {
        return true;
    }

    public abstract boolean canStart();

    public boolean canContinue() {
        return false;
    }

    public void start() {
    }

    public void tick() {
    }

    public void stop() {
    }
}
