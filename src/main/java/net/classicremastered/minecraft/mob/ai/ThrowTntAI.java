// file: src/com/mojang/minecraft/mob/ai/ThrowTntAI.java
package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.item.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

public class ThrowTntAI extends BasicAI {

    // --- movement / spacing for TNT mode ---
    public float preferredMin  = 4.0F;
    public float preferredMax  = 12.0F;
    public float walkSpeed     = 0.02F;

    // --- TNT projectile params (normal mode) ---
    public int   windupTicks   = 6;
    public int   cooldownTicks = 36;
    public float throwPower    = 0.90F;
    public int   fuseTicks     = 36;

    // --- melee (baseline) ---
    public float meleeRange         = 2.0F;
    public int   meleeIntervalTicks = 5;   // 0.25s
    public int   meleeDamage        = 1;

    // --- revenge (arrow hit → sprint + rapid hits @ 0.1s) ---
    public float revengeRange            = 2.2F;   // small extra leniency
    public int   revengeIntervalTicks    = 2;      // 0.1s @ 20 tps
    public int   revengeBurstTicks       = 60;     // 3.0s of rage (tweak)
    public float revengeSprintMultiplier = 4.0F;   // really book it

    // --- optional periodic frenzy (comment out if not using) ---
    public int   frenzyHitsTarget      = 20;
    public int   frenzyIntervalTicks   = 5;        // 0.25s
    public int   frenzyCooldownTicks   = 200;      // 10s
    public float frenzySprintMultiplier= 4.0F;
    public int   frenzySafetyMaxTicks  = 240;

    // runtime state
    private int  windup = 0, cooldown = 0;

    // revenge state
    private Entity revengeTarget = null;
    private int    revengeTicks  = 0;
    private int    revengeTimer  = 0;

    // frenzy state
    private boolean inFrenzy = false;
    private int     frenzyCooldown = 0;
    private int     frenzyHitsLeft = 0;
    private int     frenzyInterval = 0;
    private int     frenzyTicks    = 0;

    @Override
    public void tick(Level level, Mob mob) {
        super.tick(level, mob);
        if (level.creativeMode) return;               // <- disable whole behavior
        Player playerTarget = level.getNearestPlayer(mob.x, mob.y, mob.z, 32.0F);
        if (playerTarget == null || playerTarget.removed) return;

        // face current main target (player) by default; revenge will override below
        faceSmooth(mob, playerTarget.x - mob.x, playerTarget.z - mob.z);

        float walk = walkSpeed * (running ? 1.4F : 1.0F);
        float runFrenzy = walkSpeed * 1.4F * frenzySprintMultiplier;
        float runRevenge = walkSpeed * 1.4F * revengeSprintMultiplier;

        // ======================================================
        // REVENGE MODE (highest priority): sprint & hit every 0.1s
        // ======================================================
        if (revengeTicks > 0 && revengeTarget != null && !revengeTarget.removed) {
            float rdx = revengeTarget.x - mob.x;
            float rdz = revengeTarget.z - mob.z;
            float rDist = MathHelper.sqrt(rdx * rdx + rdz * rdz);

            faceSmooth(mob, rdx, rdz);
            mob.moveRelative(0.0F, 1.0F, runRevenge);

            if (rDist <= revengeRange) {
                if (revengeTimer > 0) revengeTimer--;
                else {
                    revengeTarget.hurt(mob, 1); // -1 health
                    mob.attackTime = 5;
                    revengeTimer = revengeIntervalTicks;
                }
            }

            revengeTicks--;
            // while raging, skip other behaviors
            return;
        } else if (revengeTicks > 0) {
            // lost target (died/removed); clear rage quickly
            revengeTicks = 0;
            revengeTarget = null;
        }

        // ======================================================
        // FRENZY MODE (if you’re using it): sprint & rapid hits
        // ======================================================
        if (inFrenzy) {
            frenzyTicks++;

            // sprint toward player
            float dx = playerTarget.x - mob.x, dz = playerTarget.z - mob.z;
            float flatDist = MathHelper.sqrt(dx*dx + dz*dz);
            faceSmooth(mob, dx, dz);
            mob.moveRelative(0.0F, 1.0F, runFrenzy);

            if (flatDist <= meleeRange) {
                if (frenzyInterval > 0) frenzyInterval--;
                else if (frenzyHitsLeft > 0) {
                    playerTarget.hurt(mob, 1);
                    mob.attackTime = 5;
                    frenzyHitsLeft--;
                    frenzyInterval = frenzyIntervalTicks;
                }
            }

            if (frenzyHitsLeft <= 0 || frenzyTicks >= frenzySafetyMaxTicks) {
                endFrenzy();
            }
            return;
        }

        // cooldown to next frenzy
        if (frenzyCooldown > 0) frenzyCooldown--;
        else {
            // start frenzy on cooldown expiry
            startFrenzy();
            return;
        }

        // ======================================================
        // NORMAL TNT MODE (spacing + throw)
        // ======================================================
        float dx = playerTarget.x - mob.x;
        float dz = playerTarget.z - mob.z;
        float flatDist = MathHelper.sqrt(dx * dx + dz * dz);

        if (flatDist < preferredMin) {
            mob.moveRelative(0.0F, -1.0F, walk);
            windup = 0;
            return;
        }
        if (flatDist > preferredMax) {
            mob.moveRelative(0.0F, 1.0F, walk);
            windup = 0;
            return;
        }

        if (cooldown > 0) { cooldown--; return; }
        if (!hasLineOfSight(level, mob, playerTarget)) {
            mob.moveRelative(0.0F, 0.6F, walk * 1.25F);
            windup = 0;
            return;
        }

        if (windup < windupTicks) {
            windup++;
            mob.attackTime = Math.max(mob.attackTime, Mob.ATTACK_DURATION);
            return;
        }

        // Throw the straight-shot TNT (explodes on ground/player)
        float dy = (playerTarget.y + 1.55F) - (mob.y + mob.heightOffset);
        float pitchUp = clamp(-35F, -5F - flatDist * 1.8F - dy * 1.2F, -5F);

        float yaw = mob.yRot;
        float yawRad = yaw * ((float)Math.PI / 180F);
        float forward = 0.6F, up = 0.6F;

        float offX = -MathHelper.sin(yawRad) * forward;
        float offZ =  MathHelper.cos(yawRad) * forward;

        level.addEntity(new net.classicremastered.minecraft.item.ImpactPrimedTnt(
                level, mob,
                mob.x + offX,
                mob.y + mob.heightOffset + up,
                mob.z + offZ,
                playerTarget.x, playerTarget.y + 1.55F, playerTarget.z,
                throwPower, fuseTicks, preferredMax
        ).setExplodeOnGround(true)
         .setExplodeOnPlayer(true));

        mob.attackTime = Mob.ATTACK_DURATION;
        windup = 0;
        cooldown = cooldownTicks;
    }

    // ——— Handle getting hurt: enable revenge when hit by an Arrow ———
    @Override
    public void hurt(Entity src, int amount) {
        super.hurt(src, amount);
        if (level != null && level.creativeMode) return;  // <- no revenge
        try {
            if (src instanceof Arrow) {
                Entity owner = ((Arrow) src).getOwner();
                if (owner != null && !owner.removed) {
                    this.revengeTarget = owner;
                    this.revengeTicks  = this.revengeBurstTicks;
                    this.revengeTimer  = 0; // first hit as soon as in range
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
    private void faceSmooth(Mob mob, float dx, float dz) {
        float desiredYaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = desiredYaw - mob.yRot;
        while (dyaw < -180.0F) dyaw += 360.0F;
        while (dyaw >= 180.0F) dyaw -= 360.0F;
        mob.yRot += dyaw * 0.25F;
    }

    private boolean hasLineOfSight(Level level, Mob mob, Player target) {
        float sx = mob.x;
        float sy = mob.y + mob.heightOffset + 0.6F;
        float sz = mob.z;

        float tx = target.x;
        float ty = target.y + 0.8F;
        float tz = target.z;

        float dx = tx - sx, dy = ty - sy, dz = tz - sz;
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            int bx = MathHelper.floor(sx + dx * t);
            int by = MathHelper.floor(sy + dy * t);
            int bz = MathHelper.floor(sz + dz * t);
            int id = level.getTile(bx, by, bz);
            if (id > 0 && net.classicremastered.minecraft.level.tile.Block.blocks[id].isSolid()) return false;
        }
        return true;
    }

    private void startFrenzy() {
        this.inFrenzy       = true;
        this.frenzyHitsLeft = this.frenzyHitsTarget;
        this.frenzyInterval = 0;
        this.frenzyTicks    = 0;
    }

    private void endFrenzy() {
        this.inFrenzy       = false;
        this.frenzyCooldown = this.frenzyCooldownTicks;
        this.frenzyHitsLeft = 0;
        this.frenzyInterval = 0;
        this.frenzyTicks    = 0;
        this.cooldown       = Math.max(this.cooldown, 10);
        this.windup         = 0;
    }

    private static float clamp(float min, float val, float max) {
        return (val < min) ? min : (val > max) ? max : val;
    }
}
