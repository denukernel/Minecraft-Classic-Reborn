package net.classicremastered.minecraft.mob.ai.combat;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.level.VillagerDoorManager;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.GoalSelector;
import net.classicremastered.minecraft.mob.ai.goal.combat.AvoidTntGoal;

import net.classicremastered.minecraft.mob.ai.goal.combat.HostileMeleeGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.HostileThreats;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.TargetSelectionGoal;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Target acquisition and melee combat; shared BasicAI navigation owns route steering. */
public class BasicAttackAI extends BasicAI {

    public static final long serialVersionUID = 0L;
    public int damage = 6;
    public boolean evading;

    private final boolean tntAvoidanceEnabled;
    private final boolean recoverStalledTargets;
    private transient HostileThreats threats;
    private transient GoalSelector avoidance;

    public BasicAttackAI() {
        this(false, false);
    }

    public BasicAttackAI(boolean arrowDodgingEnabled, boolean tntAvoidanceEnabled) {
        this(arrowDodgingEnabled, tntAvoidanceEnabled, false);
    }

    public BasicAttackAI(
            boolean arrowDodgingEnabled,
            boolean tntAvoidanceEnabled,
            boolean recoverStalledTargets) {

        this.tntAvoidanceEnabled = tntAvoidanceEnabled;
        this.recoverStalledTargets = recoverStalledTargets;
    }

    public final boolean isArrowDodgingEnabled() {
        return false;
    }

    public final boolean isTntAvoidanceEnabled() {
        return tntAvoidanceEnabled;
    }

    public final boolean shouldRecoverStalledTargets() {
        return recoverStalledTargets;
    }

    public final HostileThreats threats() {
        if (threats == null) threats = new HostileThreats();
        return threats;
    }

    @Override
    protected boolean navigationAllowed() {
        return !evading && super.navigationAllowed();
    }

    public float getSightRange() {
        if (mob instanceof StickFigure) return 40;
        if (mob instanceof Skeleton) return 20;
        if (mob instanceof Zombie) return 35;
        if (mob instanceof Creeper) return 19;
        return 16;
    }

    public int retaliationTicks;
    private transient GoalSelector combatGoals;
    private transient MeleeAttackGoal melee;
    private transient TargetSelectionGoal targetSelection;

    public final boolean hasRetaliationTarget() {
        return retaliationTicks > 0
                && isAliveTarget(attackTarget)
                && !CreativeModeHelper.shouldIgnoreTarget(attackTarget);
    }

    @Override
    protected void update() {
        if (retaliationTicks > 0) retaliationTicks--;
        super.update();
        if (mob == null || level == null || mob.health <= 0) return;
        doAttack();
    }

    protected void doAttack() {
        if (!tntAvoidanceEnabled) {
            tickCombatGoals();
            return;
        }
        threats().scan(this);
        if (avoidance == null) {
            avoidance = createGoalSelector();

            if (tntAvoidanceEnabled) avoidance.addGoal(1, new AvoidTntGoal(this));
            avoidance.addGoal(2, new HostileMeleeGoal(this));
        }
        avoidance.tick();
    }

    public void tickMountedCombat(net.classicremastered.minecraft.mob.Mob rider) {
        bind(rider.level, rider);
        if (retaliationTicks > 0)
            retaliationTicks--;
        if (attackDelay > 0)
            attackDelay--;
        tickCombatGoals();
    }

    /** Runs the common targeting and melee goals after optional avoidance. */
    public final void tickCombatGoals() {
        selectCombatTarget();
        if (approachClosedHouse()) return;
        tickMeleeGoal();
    }

    private boolean approachClosedHouse() {
        if (!(mob instanceof Zombie zombie)
                || mob instanceof Skeleton
                || mob.corruptedToDefendPlayer
                || mob.riding != null
                || !navigationAllowed()
                || hasRetaliationTarget()
                || hasPlayerSearch()
                || CombatRules.difficulty(level) == Difficulty.PEACEFUL
                || (attackTarget != null
                        && (!(attackTarget instanceof Villager)
                                || hasLineOfSight(mob, attackTarget)))) return false;
        var approach = level.villagerDoors().siegeApproach(zombie, VillagerDoorManager.SIEGE_RANGE);
        if (approach == null) return false;

        // A door approach must be a fixed navigation goal, separate from the resident.
        attackTarget = null;
        running = false;
        navigateTo(approach.x(), approach.y(), approach.z(), runSpeed);
        return true;
    }

    protected final void selectCombatTarget() {
        if (targetSelection == null) targetSelection = new TargetSelectionGoal(this);
        targetSelection.tick();
    }

    protected final void tickMeleeGoal() {
        if (combatGoals == null) {
            melee = createMeleeGoal();
            combatGoals = createGoalSelector(melee);
            melee.setFlags(
                    java.util.EnumSet.of(
                            net.classicremastered.minecraft.mob.ai.goal.Goal.Flag.MOVE,
                            net.classicremastered.minecraft.mob.ai.goal.Goal.Flag.LOOK));
        }
        combatGoals.tick();
    }

    public final boolean withinMeleeReach(Entity target, float reach) {
        if (mob == null || target == null || mob.bb == null || target.bb == null) return false;
        float dx = target.x - mob.x, dz = target.z - mob.z;
        return dx * dx + dz * dz < reach * reach
                && target.bb.y1 > mob.bb.y0 - .2f
                && target.bb.y0 < mob.bb.y1 + .2f;
    }

    protected MeleeAttackGoal createMeleeGoal() {
        return new MeleeAttackGoal(this);
    }

    public boolean attack(Entity target) {
        if (melee == null) melee = createMeleeGoal();
        return melee.hit(target);
    }

    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        if (this.level == null || this.mob == null) return;
        if (net.classicremastered.minecraft.util.CreativeModeHelper.isCreativePlayer(src)) return;

        if (src instanceof Arrow) {
            src = ((Arrow) src).getOwner();
        }
        boolean defenderHit =
                src instanceof net.classicremastered.minecraft.mob.Mob defender
                        && defender.corruptedToDefendPlayer
                        && !mob.corruptedToDefendPlayer
                        && net.classicremastered.minecraft.mob.MobRegistry.isTurretHostile(mob);
        if (src != null && (defenderHit || !src.getClass().equals(this.mob.getClass()))) {
            this.attackTarget = src;
            retaliationTicks = 200;
        }
    }

    static boolean isAliveTarget(Entity entity) {
        return entity != null
                && !entity.removed
                && (!(entity instanceof net.classicremastered.minecraft.mob.Mob living)
                        || living.health > 0);
    }

    public boolean hasLineOfSight(Entity from, Entity to) {
        return HostileTargets.hasLineOfSight(from, to);
    }
}
