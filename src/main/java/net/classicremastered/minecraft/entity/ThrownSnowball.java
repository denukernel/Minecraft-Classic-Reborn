package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.TerrainParticle;
import net.classicremastered.minecraft.player.Player;

public final class ThrownSnowball extends ThrownEnderPearl {
    public ThrownSnowball(Level level, Player owner) {
        super(level, owner, 1.5f);
    }

    @Override
    protected boolean tracksLanding() {
        return false;
    }

    @Override
    protected Item projectileItem() {
        return Item.SNOWBALL;
    }

    @Override
    protected void onImpact(Mob victim, Vec3D impact) {
        remove();
        if (victim != null && !level.isNetworkMode()) {
            float dx = -xd, dz = -zd;
            if (dx * dx + dz * dz < 1e-8f) {
                dx = impact.x - victim.x;
                dz = impact.z - victim.z;
            }
            victim.animateHurt(dx, dz);
            victim.playHurtSound();
            if (dx * dx + dz * dz >= 1e-8f) victim.knockback(this, 0, dx, dz);
        }
        if (level.particleEngine == null) return;

        for (int i = 0; i < 8; i++) {
            float dx = (level.random.nextFloat() - .5f) * .2f;
            float dz = (level.random.nextFloat() - .5f) * .2f;
            level.particleEngine.spawnParticle(
                    new TerrainParticle(
                            level, impact.x, impact.y, impact.z, dx, .08f, dz, Block.SNOW_BLOCK));
        }
    }
}
