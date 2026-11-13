package net.classicremastered.minecraft.particle;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.render.ShapeRenderer;

public final class CombatParticle extends Particle {
    public enum Kind {
        CRITICAL,
        WEAK,
        SWEEP,
        KNOCKBACK
    }

    private final Kind kind;

    public CombatParticle(Level level, float x, float y, float z, Kind kind) {
        super(level, x, y, z, 0, 0, 0);
        this.kind = kind;
        lifetime = kind == Kind.SWEEP ? 4 : 8;
        size = kind == Kind.SWEEP ? 11 : kind == Kind.CRITICAL ? 1.8f : 1.25f;
        if (kind == Kind.SWEEP) {
            xd = yd = zd = 0;
        } else {
            gravity = .5f;
            if (kind == Kind.WEAK) rCol = gCol = bCol = .65f;
        }
    }

    @Override
    public int getParticleTexture() {
        return ParticleManager.COMBAT_PASS;
    }

    @Override
    public void render(ShapeRenderer shape, float partial, Vec3D right, Vec3D up) {
        int tile =
                switch (kind) {
                    case CRITICAL -> 0;
                    case WEAK -> 1;
                    case KNOCKBACK -> 10;
                    case SWEEP -> 2 + Math.min(7, (int) ((age + partial) * 8 / lifetime));
                };
        renderQuad(shape, partial, right, up, tile / 16f, 0, 15.99f / 256);
    }
}
