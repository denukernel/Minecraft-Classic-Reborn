package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.ai.BasicAttackAI;
import net.classicremastered.minecraft.particle.TerrainParticle;
import net.classicremastered.util.MathHelper;

final class Creeper$1 extends BasicAttackAI {

    public static final long serialVersionUID = 0L;
    final Creeper creeper;

    Creeper$1(Creeper var1) {
        this.creeper = var1;
    }

    public final boolean attack(Entity target) {
        // Use BasicAttackAI's melee attack first
        if (!super.attack(target)) {
            return false;
        }
        // Extra creeper behavior: deal bonus damage on melee contact
        this.mob.hurt(target, 6);
        return true;
    }

    @Override
    public final void beforeRemove() {
        // Don’t explode (or boom) in Creative mode
        if (this.level != null && this.level.creativeMode) {
            return;
        }

        // --- Explosion sound ---
        if (this.level != null) {
            // Slight pitch variance ±10%
            float pitch = 1.0F + (this.random.nextFloat() - 0.5F) * 0.2F;
            // If your Level has playSound(name, x,y,z, vol, pitch)
            this.level.playSound("random/explode", this.mob.x, this.mob.y, this.mob.z, 1.0F, pitch);

            // If your API differs, try one of these instead:
            // SoundManager.play("random.explode", this.mob.x, this.mob.y, this.mob.z, 1.0F,
            // pitch);
            // this.level.playSoundAtEntity(this.mob, "random.explode", 1.0F, pitch);
        }

        float power = 4.0F;
        this.level.explode(this.mob, this.mob.x, this.mob.y, this.mob.z, power);

        // Debris particles (unchanged)
        for (int i = 0; i < 500; ++i) {
            float rx = (float) this.random.nextGaussian() * power / 4.0F;
            float ry = (float) this.random.nextGaussian() * power / 4.0F;
            float rz = (float) this.random.nextGaussian() * power / 4.0F;
            float dist = MathHelper.sqrt(rx * rx + ry * ry + rz * rz);
            float vx = rx / dist / dist;
            float vy = ry / dist / dist;
            float vz = rz / dist / dist;
            this.level.particleEngine.spawnParticle(new TerrainParticle(this.level, this.mob.x + rx, this.mob.y + ry,
                    this.mob.z + rz, vx, vy, vz, Block.LEAVES));
        }
    }

}
