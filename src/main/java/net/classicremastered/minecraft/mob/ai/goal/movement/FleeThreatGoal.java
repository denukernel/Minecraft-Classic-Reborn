package net.classicremastered.minecraft.mob.ai.goal.movement;

import java.util.List;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;

public class FleeThreatGoal extends MobGoal<BasicAI> {

    private Entity nearestThreat;
    public FleeThreatGoal(BasicAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        if (!super.canStart() || !(ai.mob instanceof Villager villager) || villager.isHostile())
            return false;
        java.util.List near = ai.level.findEntities(ai.mob, ai.mob.bb.grow(8.0F, 4.0F, 8.0F));
        nearestThreat = null;
        float nearestDist2 = Float.MAX_VALUE;
        if (near != null) {
            for (Object o : near) {
                if (o instanceof Zombie || o instanceof Skeleton) {
                    Entity threat = (Entity) o;
                    if (!threat.removed) {
                        float dx = threat.x - ai.mob.x;
                        float dy = threat.y - ai.mob.y;
                        float dz = threat.z - ai.mob.z;
                        float dist2 = dx * dx + dy * dy + dz * dz;
                        if (dist2 < nearestDist2) {
                            nearestDist2 = dist2;
                            nearestThreat = threat;
                        }
                    }
                }
            }
        }

        return nearestThreat != null;
    }

    @Override
    public void tick() {
        float dx = ai.mob.x - nearestThreat.x;
        float dz = ai.mob.z - nearestThreat.z;
        float targetYaw = (float) (Math.atan2((double) dz, (double) dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = targetYaw - ai.mob.yRot;
        while (dyaw < -180.0F)
            dyaw += 360.0F;
        while (dyaw >= 180.0F)
            dyaw -= 360.0F;
        ai.yRotA = dyaw * 0.4F;

        ai.running = true;
        ai.yya = ai.runSpeed * 1.5F;

        boolean obstacleJump = (ai.mob.onGround && ai.mob.horizontalCollision);
        boolean mediumJump = (ai.mob.isInWater() || ai.mob.isInLava()) && ai.random.nextFloat() < 0.8F;
        ai.jumping = obstacleJump || mediumJump;

        ai.mob.yRot += ai.yRotA;
        ai.mob.xRot = (float) ai.defaultLookAngle;

        if (ai.yya > ai.runSpeed * 1.6F)
            ai.yya = ai.runSpeed * 1.6F;
        if (ai.yya < -ai.runSpeed * 1.6F)
            ai.yya = -ai.runSpeed * 1.6F;
        ai.xxa = 0.0F;
        ai.noActionTime = 0;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance > 0.001f)
            ai.navigateTo(ai.mob.x + dx / distance * 5f, ai.mob.bb.y0, ai.mob.z + dz / distance * 5f,
                    ai.runSpeed * 1.5f);
    }
}
