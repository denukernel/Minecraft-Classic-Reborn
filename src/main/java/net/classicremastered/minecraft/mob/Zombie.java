// File: src/com/mojang/minecraft/mob/Zombie.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.goal.zombie.ZombieDifficultyGoal;
import net.classicremastered.minecraft.mob.ai.goal.zombie.ZombieSunlightGoal;
import net.classicremastered.util.MathHelper;

public class Zombie extends HumanoidMob {

    public static final long serialVersionUID = 0L;

    public int sunBurnTicks = 0;

    private int hordeCooldown;

    @Override
    public void hurt(net.classicremastered.minecraft.Entity source, int damage) {
        int previousHealth = health;
        super.hurt(source, damage);
        if (health >= previousHealth
                || level == null
                || level.isNetworkMode()
                || level.creativeMode
                || this instanceof Skeleton
                || hordeCooldown > 0) return;
        net.classicremastered.minecraft.Difficulty difficulty =
                net.classicremastered.minecraft.CombatRules.difficulty(level);
        if (difficulty != net.classicremastered.minecraft.Difficulty.NORMAL
                && difficulty != net.classicremastered.minecraft.Difficulty.HARD) return;
        net.classicremastered.minecraft.Entity attacker =
                source instanceof net.classicremastered.minecraft.entity.Arrow arrow
                        ? arrow.getOwner()
                        : source;
        if (!(attacker instanceof Mob living)
                || living.health <= 0
                || attacker.removed
                || attacker instanceof Zombie && !(attacker instanceof Skeleton)
                || net.classicremastered.minecraft.util.CreativeModeHelper.shouldIgnoreTarget(
                        attacker)) return;
        hordeCooldown = 40;
        float radius = difficulty == net.classicremastered.minecraft.Difficulty.HARD ? 32 : 24;
        for (Object nearby : level.findEntities(this, bb.grow(radius, 12, radius))) {
            if (nearby instanceof Zombie zombie
                    && !(zombie instanceof Skeleton)
                    && zombie.health > 0
                    && !zombie.removed
                    && zombie.ai
                            instanceof
                            net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI combat) {
                combat.bind(level, zombie);
                combat.attackTarget = attacker;
                combat.retaliationTicks = 600;
            }
        }
    }

    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector traits;

    public Zombie(Level level, float x, float y, float z) {
        super(level, x, y, z);
        this.modelName = "zombie";
        this.textureName = "/mob/zombie.png";
        this.heightOffset = 1.62F;
        this.ai = new net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI(true, true, true);
        setSize(0.6F, 1.8F);
        this.soundIdle = "mob/zombie/say";
        this.soundHurt = "mob/zombie/hurt";
        this.coinDrop = 1;
        this.soundDeath = "mob/zombie/death";
    }

    // Zombie.java — add this method
    /** Exposes the variant sunlight policy to the sunlight goal. */
    public final boolean isSunlightVulnerable() {
        return !isSunImmune();
    }

    protected boolean isSunImmune() {
        return false;
    }

    // Zombie.java — replace the entire tick() with this
    @Override
    public void tick() {
        if (hordeCooldown > 0) hordeCooldown--;
        super.tick();
        if (traits == null)
            traits =
                    new net.classicremastered.minecraft.mob.ai.goal.GoalSelector(
                            new ZombieSunlightGoal(this), new ZombieDifficultyGoal(this));
        traits.tick();
    }

    // --- Water check helper ---
    public boolean isInWaterOrOnWater() {
        // Submerged or standing in a water block
        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);

        int id = level.getTile(bx, by, bz);
        if (id == net.classicremastered.minecraft.level.tile.Block.WATER.id
                || id == net.classicremastered.minecraft.level.tile.Block.STATIONARY_WATER.id) {
            return true;
        }

        // Check just below feet for shallow water
        int idBelow = level.getTile(bx, by - 1, bz);
        return idBelow == net.classicremastered.minecraft.level.tile.Block.WATER.id
                || idBelow == net.classicremastered.minecraft.level.tile.Block.STATIONARY_WATER.id;
    }
}
