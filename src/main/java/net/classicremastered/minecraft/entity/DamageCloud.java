package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.TextureManager;
import java.util.List;

public class DamageCloud extends Entity {
    private int ticksLeft = 60; // 3 seconds

    public DamageCloud(Level level, float x, float y, float z) {
        super(level);
        this.setSize(2.5f, 1.5f);
        this.heightOffset = 0.75f;
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        ticksLeft--;
        if (ticksLeft <= 0) {
            remove();
            return;
        }

        xo = x;
        yo = y;
        zo = z;

        // Spawn smoke particles in area
        if (level.particleEngine != null) {
            for (int i = 0; i < 4; i++) {
                float rx = (float) (Math.random() - 0.5) * 2.0f;
                float ry = (float) (Math.random() - 0.5) * 1.0f;
                float rz = (float) (Math.random() - 0.5) * 2.0f;
                level.particleEngine.spawnParticle(new net.classicremastered.minecraft.particle.SmokeParticle(level, x + rx, y + ry, z + rz));
            }
        }

        // Damage mobs in area periodically (every 10 ticks)
        if (ticksLeft % 10 == 0) {
            List targets = level.blockMap.getEntities(this, bb);
            for (Object obj : targets) {
                if (obj instanceof net.classicremastered.minecraft.mob.Mob) {
                    net.classicremastered.minecraft.mob.Mob m = (net.classicremastered.minecraft.mob.Mob) obj;
                    if (m.isPickable() && m.health > 0) {
                        m.hurt(null, 2); // deal 2 damage (1 heart)
                    }
                }
            }
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        // Invisible entity, visual representation is via particles
    }

    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isPushable() { return false; }
}
