package net.classicremastered.minecraft.mob.ai.goal.enderman;

import java.util.EnumSet;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.mob.ai.goal.Goal;

public final class EndermanSunEscapeGoal extends Goal {

    private final EndermanAI ai;
    public EndermanSunEscapeGoal(EndermanAI ai) {
        this.ai = ai;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canContinue() {
        return canStart();
    }

    @Override
    public boolean canStart() {
        return ai.hasPlayer() && !ai.angry && ai.level.shouldUndeadBurnAt(ai.mob);
    }

    @Override
    public void tick() {
        Enderman enderman = (Enderman) ai.mob;
        if (ai.sunTeleportCooldown == 0) {
            boolean escaped = ai.teleportation().teleportToShade(enderman, 48, 64);
            if (!escaped)
                escaped = ai.teleportation().teleportRandom(enderman);
            if (escaped) {
                ai.teleportCooldown = Math.max(40, ai.teleportCooldown);
                ai.sunTeleportCooldown = 60;
            } else
                ai.sunTeleportCooldown = 20;
        }
        ai.clearTarget();
    }
}
