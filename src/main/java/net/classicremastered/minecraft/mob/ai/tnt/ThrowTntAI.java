// file: src/com/mojang/minecraft/mob/ai/ThrowTntAI.java
package net.classicremastered.minecraft.mob.ai.tnt;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.tnt.ThrowTntGoal;
import net.classicremastered.minecraft.mob.ai.goal.tnt.TntFrenzyGoal;
import net.classicremastered.minecraft.mob.ai.goal.tnt.TntRevengeGoal;
import net.classicremastered.util.MathHelper;

public class ThrowTntAI extends BasicAI {

    public Mob playerTarget;
    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector combatGoals;

    // --- movement / spacing for TNT mode ---
    public float preferredMin = 4.0F;
    public float preferredMax = 12.0F;
    public float walkSpeed = 0.02F;

    // --- TNT projectile params (normal mode) ---
    public int windupTicks = 6;
    public int cooldownTicks = 36;
    public float throwPower = 0.90F;
    public int fuseTicks = 36;

    // --- melee (baseline) ---
    public float meleeRange = 2.0F;
    public int meleeIntervalTicks = 5; // 0.25s
    public int meleeDamage = 1;

    // --- revenge (arrow hit → sprint + rapid hits @ 0.1s) ---
    public float revengeRange = 2.2F; // small extra leniency
    public int revengeIntervalTicks = 2; // 0.1s @ 20 tps
    public int revengeBurstTicks = 60; // 3.0s of rage (tweak)
    public float revengeSprintMultiplier = 4.0F; // really book it

    // --- optional periodic frenzy (comment out if not using) ---
    public int frenzyHitsTarget = 20;
    public int frenzyIntervalTicks = 5; // 0.25s
    public int frenzyCooldownTicks = 200; // 10s
    public float frenzySprintMultiplier = 4.0F;
    public int frenzySafetyMaxTicks = 240;

    // runtime state
    public int windup = 0, cooldown = 0;

    // revenge state
    public Entity revengeTarget = null;
    public int revengeTicks = 0;
    public int revengeTimer = 0;

    // frenzy state
    public boolean inFrenzy = false;
    public int frenzyCooldown = 0;
    public int frenzyHitsLeft = 0;
    public int frenzyInterval = 0;
    public int frenzyTicks = 0;
    public boolean combatActive;

    @Override
    protected boolean usesPlannedWandering() {
        return !combatActive;
    }

    @Override
    protected void update() {
        if (combatActive) {
            xxa = yya = yRotA = 0;
            jumping = false;
            return;
        }
        super.update();
    }

    @Override
    public void tick(Level level, Mob mob) {
        playerTarget =
                net.classicremastered.minecraft.mob.ai.combat.HostileTargets.nearest(
                        mob, 32, playerTarget);
        attackTarget = playerTarget;
        combatActive =
                playerTarget != null
                        && playerTarget.health > 0
                        && !playerTarget.removed
                        && !net.classicremastered.minecraft.util.CreativeModeHelper
                                .shouldIgnoreTarget(playerTarget);
        super.tick(level, mob);
        if (!combatActive) return;
        if (revengeTicks > 0 && (revengeTarget == null || revengeTarget.removed)) {
            revengeTicks = 0;
            revengeTarget = null;
        }
        if (combatGoals == null)
            combatGoals =
                    createGoalSelector(
                            new TntRevengeGoal(this),
                            new TntFrenzyGoal(this),
                            new ThrowTntGoal(this));
        combatGoals.tick();
    }

    // ——— Handle getting hurt: enable revenge when hit by an Arrow ———
    @Override
    public void hurt(Entity src, int amount) {
        super.hurt(src, amount);
        if (level != null && level.creativeMode) return; // <- no revenge
        try {
            if (src instanceof Arrow) {
                Entity owner = ((Arrow) src).getOwner();
                if (owner != null && !owner.removed) {
                    this.revengeTarget = owner;
                    this.revengeTicks = this.revengeBurstTicks;
                    this.revengeTimer = 0; // first hit as soon as in range
                    // cancel any current windup so it immediately chases
                    this.windup = 0;
                }
            }
        } catch (Throwable ignored) {
            // If Arrow class differs on your fork, you can broaden:
            // this.revengeTarget = src; this.revengeTicks = revengeBurstTicks;
        }
    }

    // ---------- helpers ----------
    public void faceSmooth(Mob mob, float dx, float dz) {
        float desiredYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = desiredYaw - mob.yRot;
        while (dyaw < -180.0F) dyaw += 360.0F;
        while (dyaw >= 180.0F) dyaw -= 360.0F;
        mob.yRot += dyaw * 0.25F;
    }

    public boolean hasLineOfSight(Level level, Mob mob, Mob target) {
        float sx = mob.x;
        float sy = mob.bb.y0 + mob.bbHeight * .85f;
        float sz = mob.z;

        float tx = target.x;
        float ty = target.bb.y0 + target.bbHeight * .5f;
        float tz = target.z;

        float dx = tx - sx, dy = ty - sy, dz = tz - sz;
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            int bx = MathHelper.floor(sx + dx * t);
            int by = MathHelper.floor(sy + dy * t);
            int bz = MathHelper.floor(sz + dz * t);
            int id = level.getTile(bx, by, bz);
            if (id > 0 && net.classicremastered.minecraft.level.tile.Block.blocks[id].isSolid())
                return false;
        }
        return true;
    }

    public void startFrenzy() {
        this.inFrenzy = true;
        this.frenzyHitsLeft = this.frenzyHitsTarget;
        this.frenzyInterval = 0;
        this.frenzyTicks = 0;
    }

    public void endFrenzy() {
        this.inFrenzy = false;
        this.frenzyCooldown = this.frenzyCooldownTicks;
        this.frenzyHitsLeft = 0;
        this.frenzyInterval = 0;
        this.frenzyTicks = 0;
        this.cooldown = Math.max(this.cooldown, 10);
        this.windup = 0;
    }

    public static float clamp(float min, float val, float max) {
        return (val < min) ? min : (val > max) ? max : val;
    }
}
