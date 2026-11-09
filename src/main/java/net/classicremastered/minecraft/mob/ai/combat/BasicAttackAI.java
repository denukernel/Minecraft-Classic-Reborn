package net.classicremastered.minecraft.mob.ai.combat;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.GoalSelector;
import net.classicremastered.minecraft.mob.ai.goal.combat.AvoidTntGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.DodgeProjectileGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.HostileMeleeGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.HostileThreats;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.TargetSelectionGoal;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Target acquisition and melee combat; shared BasicAI navigation owns route steering. */
public class BasicAttackAI extends BasicAI {

    public static final long serialVersionUID = 0L;
    public int damage = 6;
    public boolean evading;
    private final boolean arrowDodgingEnabled;
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
        this.arrowDodgingEnabled = arrowDodgingEnabled;
        this.tntAvoidanceEnabled = tntAvoidanceEnabled;
        this.recoverStalledTargets = recoverStalledTargets;
    }

    public final boolean isArrowDodgingEnabled() {
        return arrowDodgingEnabled;
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
        return mob instanceof net.classicremastered.minecraft.mob.StickFigure
                ? 40
                : mob instanceof net.classicremastered.minecraft.mob.Skeleton
                        ? 20
                        : mob instanceof net.classicremastered.minecraft.mob.Zombie ? 35 : 16;
    }

    public int retaliationTicks;
    private transient GoalSelector combatGoals;
    private transient MeleeAttackGoal melee;

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
        if (!arrowDodgingEnabled && !tntAvoidanceEnabled) {
            tickCombatGoals();
            return;
        }
        threats().scan(this);
        if (avoidance == null) {
            avoidance = createGoalSelector();
            if (arrowDodgingEnabled) avoidance.addGoal(0, new DodgeProjectileGoal(this));
            if (tntAvoidanceEnabled) avoidance.addGoal(1, new AvoidTntGoal(this));
            avoidance.addGoal(2, new HostileMeleeGoal(this));
        }
        avoidance.tick();
    }

    /** Runs the common targeting and melee goals after optional avoidance. */
    public final void tickCombatGoals() {
        if (combatGoals == null) {
            melee = createMeleeGoal();
            combatGoals = createGoalSelector(new TargetSelectionGoal(this), melee);
            // Target selection and movement occupy independent controls.
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
        if (src != null && !src.getClass().equals(this.mob.getClass())) {
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
        if (from == null || to == null || level == null) return false;
        float fromFeetY = from.bb != null ? from.bb.y0 : from.y - from.heightOffset;
        float toFeetY = to.bb != null ? to.bb.y0 : to.y - to.heightOffset;
        float fromEyeY = fromFeetY + (from.bbHeight > 0 ? from.bbHeight * 0.85f : 0.9f);
        float toMidY = toFeetY + (to.bbHeight > 0 ? to.bbHeight * 0.50f : 0.5f);
        Vec3D a = new Vec3D(from.x, fromEyeY, from.z);
        Vec3D b = new Vec3D(to.x, toMidY, to.z);
        return level.clip(a, b) == null;
    }
}
