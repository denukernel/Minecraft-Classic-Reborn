package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.player.Player;

import java.util.ArrayList;

public final class DefenderAI extends BasicAttackAI {
    private transient Entity stalledTarget;
    private transient int retryTicks;
    private transient int progressTicks;
    private transient float bestDistance = Float.POSITIVE_INFINITY;

    public static boolean canConvert(Mob mob) {
        return !mob.corruptedToDefendPlayer
                && (mob instanceof Spider
                        || mob instanceof Enderman
                        || mob instanceof Zombie
                                && !(mob instanceof BossZombieBase)
                                && !(mob instanceof ScaryMindZombie)
                                && !(mob instanceof ZombieBuilder)
                                && !(mob instanceof HouseVandalizer));
    }

    public static void convert(Mob mob) {
        if (canConvert(mob)) restore(mob);
    }

    static void restore(Mob mob) {
        mob.corruptedToDefendPlayer = true;
        mob.persistent = true;
        mob.ai = new DefenderAI();
    }

    @Override
    protected void doAttack() {
        if (mob instanceof Defender) damage = 0;
        if (retryTicks > 0 && --retryTicks == 0) stalledTarget = null;
        if (!validEnemy(attackTarget) || mob.distanceToSqr(attackTarget) > 256) {
            attackTarget = null;
            bestDistance = Float.POSITIVE_INFINITY;
            progressTicks = 0;
            float nearest = 256;
            for (Object candidate :
                    new ArrayList<>(level.findEntities(mob, mob.bb.grow(16, 8, 16)))) {
                if (candidate instanceof Mob enemy && enemy != stalledTarget && validEnemy(enemy)) {
                    float distance = mob.distanceToSqr(enemy);
                    if (distance < nearest) {
                        nearest = distance;
                        attackTarget = enemy;
                    }
                }
            }
        }
        Player followPlayer =
                mob instanceof Defender defender
                        ? defender.ownerPlayer()
                        : level.player instanceof Player p ? p : null;
        if (attackTarget != null) {
            float dx = attackTarget.x - mob.x, dz = attackTarget.z - mob.z;
            if (withinMeleeReach(attackTarget, 2f) && hasLineOfSight(mob, attackTarget)) {
                holdPosition((float) Math.toDegrees(Math.atan2(-dx, dz)));
                progressTicks = 0;
                bestDistance = Float.POSITIVE_INFINITY;
                if (attackDelay == 0) attack(attackTarget);
            } else {
                float distance = mob.distanceToSqr(attackTarget);
                if (distance < bestDistance - .25f) {
                    bestDistance = distance;
                    progressTicks = 0;
                } else if (++progressTicks >= 60) {
                    stalledTarget = attackTarget;
                    attackTarget = null;
                    retryTicks = 40;
                    bestDistance = Float.POSITIVE_INFINITY;
                    progressTicks = 0;
                    return;
                }
                navigateTo(attackTarget.x, attackTarget.bb.y0, attackTarget.z, 1f);
            }
        } else if (followPlayer != null
                && !followPlayer.isSpectator()
                && followPlayer.health > 0
                && mob.distanceToSqr(followPlayer) > 9) {
            navigateTo(followPlayer.x, followPlayer.bb.y0, followPlayer.z, .8f);
            if (mob.isInWater()) {
                float dx = followPlayer.x - mob.x, dz = followPlayer.z - mob.z;
                mob.yRot = (float) Math.toDegrees(Math.atan2(-dx, dz));
                yya = .8f;
                jumping = true;
            }
        } else if (mob instanceof Defender) {
            holdPosition(mob.yRot);
        }
    }

    @Override
    public void hurt(Entity source, int damage) {
        if (source instanceof net.classicremastered.minecraft.entity.Arrow arrow) {
            source = arrow.getOwner();
        }
        if (validEnemy(source) && !validEnemy(attackTarget)) {
            super.hurt(source, damage);
            attackTarget = source;
            bestDistance = Float.POSITIVE_INFINITY;
            progressTicks = 0;
        }
    }

    @Override
    protected boolean usesWanderNavigation() {
        return false;
    }

    private boolean validEnemy(Entity entity) {
        if (mob instanceof Defender) return Defender.isEnemy(entity);
        return entity instanceof Mob enemy
                && !enemy.removed
                && enemy.health > 0
                && !(enemy instanceof Player)
                && !(enemy instanceof CrossbowTurret)
                && !(enemy instanceof Defender)
                && !enemy.corruptedToDefendPlayer
                && MobRegistry.isTurretHostile(enemy);
    }
}
