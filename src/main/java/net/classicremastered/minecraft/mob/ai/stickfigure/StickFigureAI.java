package net.classicremastered.minecraft.mob.ai.stickfigure;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.dimensions.StickmansHouse;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.combat.AvoidTntGoal;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.goal.stickfigure.BossCombatGoal;
import net.classicremastered.minecraft.mob.ai.goal.stickfigure.BossStrikeGoal;

/** Telegraph, strike, recover. Every second strike is a longer-reaching kick. */
public final class StickFigureAI extends BasicAttackAI {

    private static final long serialVersionUID = 2932833716648278394L;
    public boolean nextKick;
    public final StickFigurePowers powers = new StickFigurePowers();
    private transient StickFigureMovement movement;
    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector combatGoals;
    private transient AvoidTntGoal avoidTnt;
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
        if (movement().recoverArenaFloor(this, (StickFigure) mob)) {
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
                    ((StickFigure) mob).dodgeTicks = 0;
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
        if (level.isStickmansHouse()
                && player != null
                && mob.distanceToSqr(player) > range * range
                && Math.hypot(player.x - mob.x, player.z - mob.z) <= range
                && StickFigurePowers.findSupport((StickFigure) mob, player) != null)
            setPursuitTarget(player);
        else selectCombatTarget();
        if (previousTarget != null && attackTarget == null) cancelAttack();
        if (combatGoals == null) combatGoals = createGoalSelector(new BossCombatGoal(this));
        combatGoals.tick();
    }

    private void cancelAttack() {
        powers.cancel((StickFigure) mob);
        movement().cancel();
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
