// File: src/com/mojang/minecraft/mob/Zombie.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.util.MathHelper;

public class Zombie extends HumanoidMob {
    public static final long serialVersionUID = 0L;

    private int sunBurnTicks = 0;

    public Zombie(Level level, float x, float y, float z) {
        super(level, x, y, z);
        this.modelName = "zombie";
        this.textureName = "/mob/zombie.png";
        this.heightOffset = 1.62F;
        this.ai = new net.classicremastered.minecraft.mob.ai.SmartHostileAI();
        setSize(0.6F, 1.8F);
        this.soundIdle = "mob/zombie/say";
        this.soundHurt = "mob/zombie/hurt";
        this.coinDrop = 1;
        this.soundDeath = "mob/zombie/death";
    }

    // Zombie.java — add this method
    protected boolean isSunImmune() {
        return false;
    }

// Zombie.java — replace the entire tick() with this
    @Override
    public void tick() {
        super.tick();
        if (this.level == null)
            return;

        Difficulty diff = (level.minecraft != null && level.minecraft.settings != null)
                ? level.minecraft.settings.difficulty
                : Difficulty.NORMAL;

        // --- Sunlight burning (skip if variant is sun-immune; also don't burn in/onto
        // water) ---
        if (!isSunImmune() && this.level.shouldUndeadBurnAt(this) && !this.isInWaterOrOnWater()) {
            if (++sunBurnTicks >= 20) {
                this.hurt(null, 2);
                sunBurnTicks = 0;
            }
        } else {
            sunBurnTicks = 0;
        }

        // --- AI behavior by difficulty ---
        if (this.ai instanceof net.classicremastered.minecraft.mob.ai.BasicAttackAI) {
            net.classicremastered.minecraft.mob.ai.BasicAttackAI a = (net.classicremastered.minecraft.mob.ai.BasicAttackAI) this.ai;

            switch (diff) {
            case PEACEFUL:
                a.damage = 0;
                a.runSpeed = 0.8f;
                a.attackTarget = null; // no attacking
                break;

            case EASY:
                a.damage = 4;
                a.runSpeed = 0.95f;
                acquireVillagerTarget(a, 10f);
                break;

            case NORMAL:
                a.damage = 6;
                a.runSpeed = 1.05f;
                acquireVillagerTarget(a, 12f);
                break;

            case HARD:
                a.damage = 9;
                a.runSpeed = 1.20f;
                acquireVillagerTarget(a, 14f);
                break;
            }
        }
    }

// --- Water check helper ---
    private boolean isInWaterOrOnWater() {
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

    // --- Villager target finder ---
    private void acquireVillagerTarget(net.classicremastered.minecraft.mob.ai.BasicAttackAI a, float radius) {
        java.util.List list = level.findEntities(this, this.bb.grow(radius, 4, radius));
        if (list == null)
            return;
        for (Object o : list) {
            if (o instanceof net.classicremastered.minecraft.mob.Villager) {
                net.classicremastered.minecraft.mob.Villager v = (net.classicremastered.minecraft.mob.Villager) o;
                if (!v.removed && v.health > 0) {
                    a.attackTarget = v;
                    a.running = true;
                    return;
                }
            }
        }
    }
}
