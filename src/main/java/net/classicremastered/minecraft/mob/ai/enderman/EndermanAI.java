package net.classicremastered.minecraft.mob.ai.enderman;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.GoalSelector;
import net.classicremastered.minecraft.mob.ai.goal.enderman.EndermanMeleeGoal;
import net.classicremastered.minecraft.mob.ai.goal.enderman.EndermanNavigation;
import net.classicremastered.minecraft.mob.ai.goal.enderman.EndermanSunEscapeGoal;
import net.classicremastered.minecraft.mob.ai.goal.enderman.EndermanTargetGoal;
import net.classicremastered.minecraft.mob.ai.goal.enderman.EndermanTeleportation;
import net.classicremastered.minecraft.mob.ai.goal.enderman.EndermanWaterEscapeGoal;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class EndermanAI extends BasicAI {

    private static final long serialVersionUID = 1L;
    public boolean angry;
    public int teleportCooldown;
    public int sunTeleportCooldown;
    public int waterDamageTimer;
    public int farFromTargetTicks;
    private transient EndermanTeleportation teleportation;
    private transient EndermanNavigation navigation;

    @Override
    protected boolean usesWanderNavigation() {
        return true;
    }

    @Override
    protected void registerGoals(GoalSelector selector) {
        selector.addGoal(0, new EndermanWaterEscapeGoal(this));
        selector.addGoal(1, new EndermanSunEscapeGoal(this));
        selector.addGoal(2, new EndermanTargetGoal(this));
        selector.addGoal(3, new EndermanMeleeGoal(this));
    }

    @Override
    protected void update() {
        super.update();
        if (teleportCooldown > 0) teleportCooldown--;
        if (sunTeleportCooldown > 0) sunTeleportCooldown--;
    }

    public boolean hasPlayer() {
        return mob instanceof Enderman
                && level != null
                && net.classicremastered.minecraft.mob.ai.combat.HostileTargets.nearest(
                                mob, 64, attackTarget)
                        != null;
    }

    public boolean canTargetPlayer() {
        return mob instanceof Enderman
                && level != null
                && mob.health > 0
                && CombatRules.difficulty(level) != Difficulty.PEACEFUL;
    }

    public void clearTarget() {
        angry = false;
        farFromTargetTicks = 0;
        clearPlayerSearch();
        if (mob instanceof Enderman) ((Enderman) mob).isAttacking = false;
        running = false;
        if (navigation != null) navigation.clear();
    }

    public EndermanTeleportation teleportation() {
        if (teleportation == null) teleportation = new EndermanTeleportation(this);
        return teleportation;
    }

    public EndermanNavigation navigation() {
        if (navigation == null) navigation = new EndermanNavigation();
        return navigation;
    }

    @Override
    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        if (CreativeModeHelper.shouldIgnoreTarget(src)
                || src instanceof Player && level != null && Player.creativeInvulnerable) return;
        angry = true;
    }
}
