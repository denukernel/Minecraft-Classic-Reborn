// File: src/com/mojang/minecraft/mob/ai/BetaSpiderAI.java
package net.classicremastered.minecraft.mob.ai.spider;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.goal.spider.SpiderAngerGoal;
import net.classicremastered.minecraft.mob.ai.movement.JumpAttackAI;
import net.classicremastered.minecraft.player.Player;

/** Neutral by day (bright), aggressive at night/dark or when provoked. */
public class BetaSpiderAI extends JumpAttackAI {

    public static final long serialVersionUID = 0L;
    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector angerGoals;

    private static final int ANGER_TICKS_MAX = 600; // ~30s
    public int angerTicks = 0;
    public boolean neutral;

    public BetaSpiderAI() {
        this.runSpeed *= 3.95F; // beta-ish zip
    }

    @Override
    protected boolean navigationAllowed() {
        return !approachingWall() && super.navigationAllowed();
    }

    private boolean approachingWall() {
        if (mob == null || attackTarget == null || neutral) return false;
        float dx = attackTarget.x - mob.x, dz = attackTarget.z - mob.z;
        return attackTarget.bb.y0 > mob.bb.y0 + 0.6f && dx * dx + dz * dz < 64;
    }

    @Override
    public void tick(Level level, net.classicremastered.minecraft.mob.Mob mob) {
        this.level = level;
        this.mob = mob;
        if (angerGoals == null) angerGoals = createGoalSelector(new SpiderAngerGoal(this));
        angerGoals.tick();
        super.tick(level, mob);
    }

    @Override
    protected void doAttack() {
        if (neutral) {
            attackTarget = null;
            running = false;
            return;
        }
        Entity previousTarget = attackTarget;
        super.doAttack();
        if (previousTarget == null
                && attackTarget != null
                && !hasRetaliationTarget()
                && !hasLineOfSight(mob, attackTarget)) attackTarget = null;
        if (approachingWall()) {
            mob.yRot =
                    (float)
                                    Math.toDegrees(
                                            Math.atan2(
                                                    attackTarget.z - mob.z, attackTarget.x - mob.x))
                            - 90;
            xxa = 0;
            yya = runSpeed;
            jumping = false;
        }
        if (attackTarget != null && mob.onGround && !mob.isInWater()) {
            float dx = attackTarget.x - mob.x, dz = attackTarget.z - mob.z;
            float distanceSquared = dx * dx + dz * dz;
            if (distanceSquared >= 4
                    && distanceSquared <= 16
                    && random.nextInt(5) == 0
                    && hasLineOfSight(mob, attackTarget)) {
                float distance = (float) Math.sqrt(distanceSquared);
                mob.xd = mob.xd * 0.2f + dx / distance * 0.4f;
                mob.zd = mob.zd * 0.2f + dz / distance * 0.4f;
                mob.yd = 0.4f;
                mob.onGround = false;
            }
        }
    }

    @Override
    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        if (level == null || level.creativeMode) return;
        if (src instanceof Player) angerTicks = ANGER_TICKS_MAX;
        else if (src instanceof Arrow) {
            Entity owner = ((Arrow) src).getOwner();
            if (owner instanceof Player) angerTicks = ANGER_TICKS_MAX;
        } else if (src != null && !src.getClass().equals(this.mob.getClass())) {
            angerTicks = ANGER_TICKS_MAX;
        }
        if (angerTicks > 0) neutral = false;
    }
    // inside BetaSpiderAI.java

}
