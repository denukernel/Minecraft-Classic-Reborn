package net.classicremastered.minecraft.mob.ai.stickfigure;

import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.goal.combat.MeleeAttackGoal;
import net.classicremastered.minecraft.mob.ai.goal.stickfigure.BossCombatGoal;
import net.classicremastered.minecraft.mob.ai.goal.stickfigure.BossStrikeGoal;

/**
 * Telegraph, strike, recover. Every second strike is a longer-reaching kick.
 */
public final class StickFigureAI extends BasicAttackAI {

    private static final long serialVersionUID = 2932833716648278394L;
    public boolean nextKick;
    public final StickFigurePowers powers = new StickFigurePowers();
    private transient StickFigureMovement movement;
    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector combatGoals;
    public transient boolean specialMovement;

    public StickFigureMovement movement() {
        if (movement == null)
            movement = new StickFigureMovement();
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
        var previousTarget = attackTarget;
        selectCombatTarget();
        if (previousTarget != null && attackTarget == null) {
            var boss = (StickFigure) mob;
            powers.cancel(boss);
            movement().cancel();
        }
        if (combatGoals == null)
            combatGoals = createGoalSelector(new BossCombatGoal(this));
        combatGoals.tick();
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
