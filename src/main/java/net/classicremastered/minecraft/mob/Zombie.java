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
        if (difficulty.ordinal() < net.classicremastered.minecraft.Difficulty.NORMAL.ordinal())
            return;
        net.classicremastered.minecraft.Entity attacker =
                source instanceof net.classicremastered.minecraft.entity.Arrow arrow
                        ? arrow.getOwner()
                        : source;
        if (attacker instanceof CrossbowTurret || !(attacker instanceof Mob living)
                || living.health <= 0
                || attacker.removed
                || attacker instanceof Zombie && !(attacker instanceof Skeleton)
                || net.classicremastered.minecraft.util.CreativeModeHelper.shouldIgnoreTarget(
                        attacker)) return;
        hordeCooldown = 40;
        float radius = difficulty.ordinal() >= net.classicremastered.minecraft.Difficulty.HARD.ordinal()
                ? 32
                : 24;
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

    public final boolean canBurnInSunlight() {
        var headgear =
                equipment == null
                        ? null
                        : net.classicremastered.minecraft.level.itemstack.Equipment.gear(
                                equipment[0]);
        return isSunlightVulnerable()
                && (headgear == null || headgear.slot != 0)
                && !((!equipmentRolled || legacyArmor) && helmet);
    }

    protected boolean isSunImmune() {
        return false;
    }

    // Zombie.java — replace the entire tick() with this
    @Override
    public void tick() {
        if (hordeCooldown > 0) hordeCooldown--;
        if (riding != null
                && !(riding instanceof net.classicremastered.minecraft.entity.BoatEntity)
                && canBurnInSunlight()
                && !isInWaterOrOnWater()
                && net.classicremastered.minecraft.CombatRules.difficulty(level)
                        == net.classicremastered.minecraft.Difficulty.EXTREME
                && level.shouldUndeadBurnAt(this)) unmount();
        super.tick();
        if (remainingFireTicks > 0 && isInWaterOrOnWater())
            remainingFireTicks = 0;
        if (traits == null)
            traits =
                    new net.classicremastered.minecraft.mob.ai.goal.GoalSelector(
                            new ZombieSunlightGoal(this), new ZombieDifficultyGoal(this));
        traits.tick();
    }

    // --- Water check helper ---
    public boolean isInWaterOrOnWater() {
        if (isInWater())
            return true;
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        return level.isWater(bx, MathHelper.floor(bb.y0), bz)
                || level.isWater(bx, MathHelper.floor(bb.y0 - .08f), bz);
    }
}
