package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.path.Pathfinder;

public final class AvoidTntGoal extends MobGoal<BasicAttackAI> {
    private static final int[] ESCAPE_ANGLES = {0, -45, 45, -85, 85};

    public AvoidTntGoal(BasicAttackAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && ai.threats().primedTnt != null;
    }

    @Override
    public void tick() {
        ai.evading = true;
        ai.running = false;
        ai.jumping = false;
        ai.xxa = ai.yya = ai.yRotA = 0;
        var threat = ai.threats().primedTnt;
        float yaw = (float) Math.toDegrees(Math.atan2(threat.x - ai.mob.x, ai.mob.z - threat.z));
        for (int offset : ESCAPE_ANGLES) {
            float heading = yaw + offset;
            double angle = Math.toRadians(heading);
            float x = ai.mob.x - (float) Math.sin(angle) * .7f;
            float z = ai.mob.z + (float) Math.cos(angle) * .7f;
            if (Pathfinder.canWalkStraight(ai.level, ai.mob, x, ai.mob.bb.y0, z)) {
                ai.mob.yRot = heading;
                ai.yya = .65f;
                return;
            }
        }
        ai.mob.xd *= .5f;
        ai.mob.zd *= .5f;
    }
}
