package net.classicremastered.minecraft.mob.ai.goal.combat;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

import java.util.EnumSet;

public final class TargetSelectionGoal extends MobGoal<BasicAttackAI> {

    public TargetSelectionGoal(BasicAttackAI ai) {
        super(ai);
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public void tick() {
        if (ai.mob == null || ai.level == null) return;
        Mob battleTarget = net.classicremastered.minecraft.level.itemstack.MobBattle.target(ai.mob);
        if (battleTarget != null) {
            ai.setPursuitTarget(battleTarget);
            return;
        }
        if (CombatRules.difficulty(ai.level)
                == net.classicremastered.minecraft.Difficulty.PEACEFUL) {
            ai.clearPlayerSearch();
            ai.retaliationTicks = 0;
            return;
        }
        if (ai.attackTarget != null
                && (!isAliveEntity(ai.attackTarget)
                        || CreativeModeHelper.shouldIgnoreTarget(ai.attackTarget))) {
            ai.attackTarget = null;
            ai.retaliationTicks = 0;
        }
        if (ai.mob instanceof Zombie && !(ai.mob instanceof Skeleton)) {
            selectZombieTarget();
        } else {
            if (!canRetaliate())
                ai.attackTarget =
                        HostileTargets.nearestForPursuit(
                                ai.mob, ai.getSightRange(), ai.attackTarget);
        }
        float retain =
                ai.mob instanceof net.classicremastered.minecraft.mob.Skeleton
                        ? ai.getSightRange()
                        : Math.max(ai.getSightRange(), 32);
        if (ai.attackTarget != null && distanceSquared(ai.attackTarget) > retain * retain) {
            ai.attackTarget = null;
        }
        ai.setPursuitTarget(ai.attackTarget);
        if (ai.attackTarget == null) {
            ai.retaliationTicks = 0;
            ai.running = false;
            ai.runMultiplier = 1f;
        }
    }

    private boolean canRetaliate() {
        return ai.hasRetaliationTarget()
                && (!(ai.attackTarget instanceof Player)
                        || distanceSquared(ai.attackTarget)
                                        <= ai.getSightRange() * ai.getSightRange()
                                && HostileTargets.canLocate(ai.mob, ai.attackTarget));
    }

    private transient Entity progressTarget, stalledTarget;
    private transient int stationaryTicks, retryTicks;
    private transient float progressX, progressY, progressZ;

    private void selectZombieTarget() {
        float villagerRange =
                switch (CombatRules.difficulty(ai.level)) {
                    case PEACEFUL -> 0;
                    case EASY -> 10;
                    case NORMAL -> 12;
                    case HARD, VERY_HARD, EXTREME -> 14;
                };
        if (villagerRange == 0) {
            ai.attackTarget = null;
            ai.retaliationTicks = 0;
            return;
        }
        if (retryTicks > 0 && --retryTicks == 0) stalledTarget = null;
        // Custom AI subclasses can stop to build, attack at range, or use abilities.
        if (ai.shouldRecoverStalledTargets()) releaseStalledTarget();
        if (canRetaliate()
                && distanceSquared(ai.attackTarget) <= ai.getSightRange() * ai.getSightRange())
            return;
        ai.retaliationTicks = 0;
        float range = ai.getSightRange();
        if (HostileTargets.eligible(ai.attackTarget)
                && distanceSquared(ai.attackTarget) <= range * range
                && HostileTargets.canLocate(ai.mob, ai.attackTarget)) return;
        Entity player = ai.level.getPlayer();
        Entity nearestPlayer = null;
        float playerDistance = range * range;
        if (player != stalledTarget
                && isAliveEntity(player)
                && !CreativeModeHelper.shouldIgnoreTarget(player)
                && distanceSquared(player) <= playerDistance
                && HostileTargets.canLocate(ai.mob, player)) {
            nearestPlayer = player;
            playerDistance = distanceSquared(player);
        }
        Entity nearestVillager = null;
        float villagerDistance = villagerRange * villagerRange;
        for (Object entity : ai.level.findEntities(ai.mob, ai.mob.bb.grow(range, range, range))) {
            if (!(entity instanceof Entity candidate)
                    || candidate == stalledTarget
                    || !isAliveEntity(candidate)) continue;
            float distance = distanceSquared(candidate);
            if (HostileTargets.eligible(candidate)
                    && distance <= playerDistance
                    && HostileTargets.canLocate(ai.mob, candidate)) {
                nearestPlayer = candidate;
                playerDistance = distance;
            } else if (candidate instanceof Villager && distance <= villagerDistance) {
                nearestVillager = candidate;
                villagerDistance = distance;
            }
        }
        ai.attackTarget = nearestPlayer != null ? nearestPlayer : nearestVillager;
    }

    private void releaseStalledTarget() {
        float dx = ai.mob.x - progressX, dy = ai.mob.bb.y0 - progressY, dz = ai.mob.z - progressZ;
        if (ai.attackTarget == null
                || ai.attackTarget != progressTarget
                || dx * dx + dy * dy + dz * dz >= 0.0625f
                || ai.withinMeleeReach(ai.attackTarget, 2f)
                        && ai.hasLineOfSight(ai.mob, ai.attackTarget)) {
            progressTarget = ai.attackTarget;
            progressX = ai.mob.x;
            progressY = ai.mob.bb.y0;
            progressZ = ai.mob.z;
            stationaryTicks = 0;
        } else if (++stationaryTicks >= 40) {
            // Let planned wandering move the zombie before retrying this target.
            stalledTarget = ai.attackTarget;
            retryTicks = 30;
            ai.attackTarget = null;
            ai.retaliationTicks = 0;
        }
    }

    private float distanceSquared(Entity target) {
        float dx = target.x - ai.mob.x, dy = target.y - ai.mob.y, dz = target.z - ai.mob.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isAliveEntity(Entity e) {
        if (e == null) return false;
        if (e instanceof net.classicremastered.minecraft.player.Player) {
            return ((net.classicremastered.minecraft.player.Player) e).health > 0 && !e.removed;
        }
        if (e instanceof net.classicremastered.minecraft.mob.Mob) {
            return ((net.classicremastered.minecraft.mob.Mob) e).health > 0 && !e.removed;
        }
        return !e.removed;
    }
}
