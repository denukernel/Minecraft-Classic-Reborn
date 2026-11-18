package net.classicremastered.minecraft.mob.ai.stickfigure;

import java.util.Map;
import java.util.WeakHashMap;
import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.dimensions.StickmansHouse;
import net.classicremastered.minecraft.level.itemstack.MobBattle;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.combat.AvoidTntGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.goal.stickfigure.BossCombatGoal;
import net.classicremastered.minecraft.mob.ai.goal.stickfigure.BossStrikeGoal;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Telegraph, strike, recover. Every second strike is a longer-reaching kick. */
public final class StickFigureAI extends BasicAttackAI {

    private static final long serialVersionUID = 2932833716648278394L;
    public boolean nextKick;
    public final StickFigurePowers powers = new StickFigurePowers();
    private transient StickFigureMovement movement;
    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector combatGoals;
    private transient AvoidTntGoal avoidTnt;
    private transient Map<Entity, Integer> lastEnragedStrike;
    private transient Entity enragedStrikeTarget;
    public transient boolean specialMovement;

    public StickFigureMovement movement() {
        if (movement == null) movement = new StickFigureMovement();
        return movement;
    }

    @Override
    protected boolean navigationAllowed() {
        return !specialMovement
                && !powers.blocksNavigation()
                && (!(mob instanceof StickFigure s) || s.dodgeTicks == 0)
                && super.navigationAllowed();
    }

    @Override
    protected boolean handlesTargetMovement() {
        return true;
    }

    @Override
    protected boolean usesDefaultSwimSteering() {
        return false;
    }

    public boolean hitThisStrike, playedSwing;

    public StickFigureAI() {
        runSpeed = .95f;
        swimSpeed = .04f;
        speedScale = 2f;
    }

    @Override
    public void tick(net.classicremastered.minecraft.level.Level level, Mob mob) {
        if (!StickmansHouse.canFightBoss(level, attackTarget)
                || !StickmansHouse.canFightBoss(level, level.getPlayer()) && hasPlayerSearch()) {
            bind(level, mob);
            clearPlayerSearch();
            clearNavigation();
            retaliationTicks = 0;
            cancelAttack();
        }
        // Refresh serialized AI instances from worlds saved before the speed increase.
        speedScale = 2f;
        // Bosses remain in the world even when no player is nearby.
        noActionTime = 0;
        specialMovement = false;
        movement().tick((StickFigure) mob);
        super.tick(level, mob);
    }

    @Override
    protected boolean usesPlannedWandering() {
        return true;
    }

    @Override
    protected void update() {
        noActionTime = 0;
        swimSpeed = .04f;
        super.update();
    }

    @Override
    protected void doAttack() {
        StickFigure boss = (StickFigure) mob;
        if (movement().recoverArenaFloor(this, boss)) {
            if (evading && avoidTnt != null) avoidTnt.stop();
            evading = false;
            specialMovement = true;
            return;
        }
        boolean wasEvading = evading;
        threats().scan(this);
        if (avoidTnt == null) avoidTnt = new AvoidTntGoal(this);
        if (avoidTnt.canStart()) {
            if (!wasEvading) {
                cancelAttack();
                if (CombatRules.difficulty(level).compareTo(Difficulty.HARD) >= 0) {
                    boss.dodgeTicks = 0;
                    mob.xd = mob.zd = 0;
                }
            }
            avoidTnt.tick();
            return;
        }
        if (wasEvading) avoidTnt.stop();
        var previousTarget = attackTarget;
        var player = level.getPlayer();
        float range = getSightRange();
        Entity enragedTarget = selectEnragedTarget(boss);
        if (enragedTarget != null) {
            setPursuitTarget(enragedTarget);
        } else if (level.isStickmansHouse()
                && player != null
                && mob.distanceToSqr(player) > range * range
                && Math.hypot(player.x - mob.x, player.z - mob.z) <= range
                && StickFigurePowers.findSupport(boss, player) != null) setPursuitTarget(player);
        else selectCombatTarget();
        if (previousTarget != null && attackTarget == null) cancelAttack();
        if (combatGoals == null) combatGoals = createGoalSelector(new BossCombatGoal(this));
        combatGoals.tick();
    }

    private void cancelAttack() {
        enragedStrikeTarget = null;
        powers.cancel((StickFigure) mob);
        movement().cancel();
    }

    private Entity selectEnragedTarget(StickFigure boss) {
        if (!boss.isEnraged()
                || powers.blocksNavigation()
                || MobBattle.target(boss) != null
                || CombatRules.difficulty(level) == Difficulty.PEACEFUL) return null;
        if (boss.strikeTicks > 0
                && enragedStrikeTarget instanceof Mob target
                && target.level == level
                && !target.removed
                && target.health > 0
                && StickmansHouse.canFightBoss(level, target)
                && !CreativeModeHelper.shouldIgnoreTarget(target)) return target;
        enragedStrikeTarget = null;
        Mob nearest = null;
        Mob strike = null;
        int oldestStrike = Integer.MAX_VALUE;
        float nearestDistance = Float.POSITIVE_INFINITY;
        float strikeDistance = Float.POSITIVE_INFINITY;
        for (Mob attacker : StickFigurePowers.nearbyAttackers(boss)) {
            float distance = boss.distanceToSqr(attacker);
            if (distance < nearestDistance) {
                nearest = attacker;
                nearestDistance = distance;
            }
            if (!withinMeleeReach(attacker, 2f)) continue;
            int lastStrike =
                    lastEnragedStrike == null
                            ? Integer.MIN_VALUE
                            : lastEnragedStrike.getOrDefault(attacker, Integer.MIN_VALUE);
            if (lastStrike < oldestStrike
                    || lastStrike == oldestStrike && distance < strikeDistance) {
                strike = attacker;
                oldestStrike = lastStrike;
                strikeDistance = distance;
            }
        }
        return strike != null ? strike : nearest;
    }

    @Override
    public boolean attack(Entity target) {
        if (!super.attack(target)) return false;
        if (mob instanceof StickFigure boss && boss.isEnraged()) {
            enragedStrikeTarget = target;
            if (lastEnragedStrike == null) lastEnragedStrike = new WeakHashMap<>();
            lastEnragedStrike.put(target, boss.tickCount);
        }
        return true;
    }

    public void defaultAttack() {
        tickMeleeGoal();
    }

    public boolean swimToLastSeenPlayer(StickFigure boss) {
        if (!isSearchingForPlayer()) return false;
        movement().swimToLastSeenPlayer(this, boss, lastSeenPlayerPosition());
        return true;
    }

    public void combatSound(String sound, float volume, float pitch) {
        if (level.minecraft == null
                || level.minecraft.settings == null
                || level.minecraft.settings.allowMobSounds)
            level.playSound(sound, mob, volume, pitch);
    }

    @Override
    protected MeleeAttackGoal createMeleeGoal() {
        return new BossStrikeGoal(this);
    }
}
