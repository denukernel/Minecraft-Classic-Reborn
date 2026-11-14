package net.classicremastered.minecraft.particle;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.render.ShapeRenderer;

/** Expanding, full-bright blast sprite using the modern explosion animation. */
public final class ExplosionParticle extends Particle {
    private final float blastSize;

    public ExplosionParticle(Level level, float x, float y, float z, float power) {
        super(level, x, y, z, 0, 0, 0);
        blastSize = Math.min(48f, 7f * power);
        lifetime = 10;
    }

    @Override
    public void tick() {
        if (++age >= lifetime) remove();
    }

    @Override
    public int getParticleTexture() {
        return ParticleManager.EXPLOSION_PASS;
    }

    @Override
    public float getRenderBrightness(float partial) {
        return 1f;
    }

    @Override
    public void render(ShapeRenderer shape, float partial, Vec3D right, Vec3D up) {
        float progress = (age + partial) / lifetime;
        int frame = Math.min(15, (int) (progress * 16));
        size = blastSize * (.65f + .65f * progress);
        renderQuad(shape, partial, right, up, frame % 4 * .25f, frame / 4 * .25f, .2499f);
    }
}
