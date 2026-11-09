package net.classicremastered.minecraft.mob.ai.goal.zombie;

import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.ai.goal.Goal;

public final class ZombieSunlightGoal extends Goal {

    private final Zombie zombie;
    public ZombieSunlightGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(java.util.EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canStart() {
        return zombie.level != null;
    }

    @Override
    public void tick() { // --- Sunlight burning (skip if variant is sun-immune; also don't burn in/onto
        // water) ---
        if (zombie.isSunlightVulnerable() && zombie.level.shouldUndeadBurnAt(zombie) && !zombie.isInWaterOrOnWater()) {
            // Spawn nice fire & smoke particles as a visual sun-burn illusion
            if (zombie.level.particleEngine != null) {
                if (zombie.level.random.nextInt(4) == 0) {
                    float px = zombie.x + (zombie.level.random.nextFloat() - 0.5f) * zombie.bbWidth;
                    float py = zombie.y - zombie.heightOffset + zombie.level.random.nextFloat() * zombie.bbHeight;
                    float pz = zombie.z + (zombie.level.random.nextFloat() - 0.5f) * zombie.bbWidth;
                    zombie.level.particleEngine.spawnParticle(
                            new net.classicremastered.minecraft.particle.SmokeParticle(zombie.level, px, py, pz));
                }
                if (zombie.level.random.nextInt(3) == 0) {
                    float px = zombie.x + (zombie.level.random.nextFloat() - 0.5f) * zombie.bbWidth;
                    float py = zombie.y - zombie.heightOffset + zombie.level.random.nextFloat() * zombie.bbHeight;
                    float pz = zombie.z + (zombie.level.random.nextFloat() - 0.5f) * zombie.bbWidth;
                    zombie.level.particleEngine
                            .spawnParticle(
                                    new net.classicremastered.minecraft.particle.TerrainParticle(zombie.level, px,
                                            py, pz, 0f, 0f, 0f, net.classicremastered.minecraft.level.tile.Block.FIRE));
                }
            }

            if (++zombie.sunBurnTicks >= 20) {
                zombie.hurt(null, 2);
                zombie.sunBurnTicks = 0;
            }
        } else {
            zombie.sunBurnTicks = 0;
        }

    }
}
