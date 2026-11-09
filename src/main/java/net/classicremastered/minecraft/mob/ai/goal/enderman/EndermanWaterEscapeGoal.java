package net.classicremastered.minecraft.mob.ai.goal.enderman;

import java.util.EnumSet;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.mob.ai.goal.Goal;

public final class EndermanWaterEscapeGoal extends Goal {

    private final EndermanAI ai;
    public EndermanWaterEscapeGoal(EndermanAI ai) {
        this.ai = ai;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canContinue() {
        return canStart();
    }

    @Override
    public boolean canStart() {
        return ai.hasPlayer() && (ai.mob.isInWater()
                || (ai.mob.bb != null && ai.level.containsAnyLiquid(ai.mob.bb)));
    }

    @Override
    public void tick() {
        Enderman enderman = (Enderman) ai.mob;
        boolean escaped = ai.teleportation().teleportOutOfWater(enderman, 48, 64);
        if (escaped) {
            ai.teleportCooldown = 0;
            ai.waterDamageTimer = 0;
        } else if (--ai.waterDamageTimer <= 0) {
            ai.waterDamageTimer = 20;
            enderman.hurt(null, 2);
        }
        ai.clearTarget();
    }
}
