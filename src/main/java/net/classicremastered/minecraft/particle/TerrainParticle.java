package net.classicremastered.minecraft.particle;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.render.ShapeRenderer;

public class TerrainParticle extends Particle {

    private static final long serialVersionUID = 1L;

    public TerrainParticle(Level var1, float var2, float var3, float var4, float var5, float var6, float var7,
            Block var8) {
        super(var1, var2, var3, var4, var5, var6, var7);
        this.tex = var8.textureId;
        this.gravity = var8.particleGravity;
        this.rCol = this.gCol = this.bCol = 0.6F;
    }

    public int getParticleTexture() {
        return 1;
    }

    @Override
    public void render(ShapeRenderer shape, float partial, Vec3D right, Vec3D up) {
        float u = (tex % 16 + uo / 4f) / 16f;
        float v = (tex / 16 + vo / 4f) / 16f;
        renderQuad(shape, partial, right, up, u, v, 0.015609375F);
    }
}
