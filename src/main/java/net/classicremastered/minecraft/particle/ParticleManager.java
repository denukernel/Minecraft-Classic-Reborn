package net.classicremastered.minecraft.particle;

import java.util.ArrayList;
import java.util.List;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.render.TextureManager;

//In: com/mojang/minecraft/particle/ParticleManager.java

public final class ParticleManager {

    public List[] particles = new List[2];
    public TextureManager textureManager;

    public ParticleManager(Level var1, TextureManager var2) {
        if (var1 != null) {
            var1.particleEngine = this;
        }
        this.textureManager = var2;
        for (int i = 0; i < 2; ++i)
            this.particles[i] = new ArrayList();
    }

    /** NEW: rebind this manager to a (new) level when worlds change */
    public void setLevel(Level level) {
        if (level != null) {
            level.particleEngine = this;
        }
    }

    /** OPTIONAL: clear particles when switching levels */
    public void clear() {
        for (int i = 0; i < 2; ++i)
            this.particles[i].clear();
    }

    public final void spawnParticle(Entity e) {
        Particle p = (Particle) e;
        int tex = p.getParticleTexture();
        this.particles[tex].add(p);
    }

    public final void tick() {
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < this.particles[i].size(); ++j) {
                Particle p = (Particle) this.particles[i].get(j);
                p.tick();
                if (p.removed)
                    this.particles[i].remove(j--);
            }
        }
    }
}
