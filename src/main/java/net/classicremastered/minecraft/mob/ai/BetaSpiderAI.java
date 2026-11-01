// File: src/com/mojang/minecraft/mob/ai/BetaSpiderAI.java
package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

/** Neutral by day (bright), aggressive at night/dark or when provoked. */
public class BetaSpiderAI extends JumpAttackAI {
    public static final long serialVersionUID = 0L;

    private static final float NEUTRAL_BRIGHTNESS = 0.50F;
    private static final int   ANGER_TICKS_MAX    = 600; // ~30s
    private int angerTicks = 0;
    private boolean neutral;

    public BetaSpiderAI() {
        this.runSpeed *= 3.95F; // beta-ish zip
    }

    private boolean isBright() {
        return (this.mob != null) && (this.mob.getBrightness(0.0F) >= NEUTRAL_BRIGHTNESS);
    }

    @Override
    public void tick(Level level, net.classicremastered.minecraft.mob.Mob mob) {
        this.level = level; this.mob = mob;
        if (angerTicks > 0) angerTicks--;
        neutral = level.creativeMode || (isBright() && angerTicks == 0);
        if (neutral) attackTarget=null;
        super.tick(level, mob); // JumpAttackAI (pounce/chase)
    }

    @Override protected void doAttack() {
        if(neutral) {attackTarget=null;running=false;return;}
        super.doAttack();
    }

    @Override
    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        if (level == null || level.creativeMode) return;
        if (src instanceof Player) angerTicks = ANGER_TICKS_MAX;
        else if (src instanceof Arrow) {
            Entity owner = ((Arrow) src).getOwner();
            if (owner instanceof Player) angerTicks = ANGER_TICKS_MAX;
        } else if (src != null && !src.getClass().equals(this.mob.getClass())) {
            angerTicks = ANGER_TICKS_MAX;
        }
    }
 // inside BetaSpiderAI.java
    @Override
    public boolean attack(net.classicremastered.minecraft.Entity target) {
        // Let the normal melee land first (damage / attackTime, etc.)
        boolean hit = super.attack(target);
        if (!hit) return false;

        // If we hit a player, apply difficulty-based effects
        if (target instanceof net.classicremastered.minecraft.player.Player) {
            net.classicremastered.minecraft.player.Player p = (net.classicremastered.minecraft.player.Player) target;

            net.classicremastered.minecraft.Difficulty diff =
                (level != null && level.minecraft != null && level.minecraft.settings != null)
                    ? level.minecraft.settings.difficulty
                    : net.classicremastered.minecraft.Difficulty.NORMAL;

            switch (diff) {
                case NORMAL:

                    break;

                case HARD:

                    break;

                // PEACEFUL/EASY: no special debuffs
                default:
                    break;
            }
        }

        return true;
    }




}
