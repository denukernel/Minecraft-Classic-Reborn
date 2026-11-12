package net.classicremastered.minecraft.particle;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.util.MathHelper;

public class Particle extends Entity {

    private static final long serialVersionUID = 1L;
    protected float xd;
    protected float yd;
    protected float zd;
    protected int tex;
    protected float uo;
    protected float vo;
    protected int age = 0;
    protected int lifetime = 0;
    protected float size;
    protected float gravity;
    public float rCol;
    public float gCol;
    public float bCol;

    public Particle(Level var1, float var2, float var3, float var4, float var5, float var6, float var7) {
        super(var1);
        this.setSize(0.2F, 0.2F);
        this.heightOffset = this.bbHeight / 2.0F;
        this.setPos(var2, var3, var4);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.rCol = this.gCol = this.bCol = 1.0F;
        this.xd = var5 + (float) (Math.random() * 2.0D - 1.0D) * 0.4F;
        this.yd = var6 + (float) (Math.random() * 2.0D - 1.0D) * 0.4F;
        this.zd = var7 + (float) (Math.random() * 2.0D - 1.0D) * 0.4F;
        float var8 = (float) (Math.random() + Math.random() + 1.0D) * 0.15F;
        var2 = MathHelper.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        this.xd = this.xd / var2 * var8 * 0.4F;
        this.yd = this.yd / var2 * var8 * 0.4F + 0.1F;
        this.zd = this.zd / var2 * var8 * 0.4F;
        this.uo = (float) Math.random() * 3.0F;
        this.vo = (float) Math.random() * 3.0F;
        this.size = (float) (Math.random() * 0.5D + 0.5D);
        this.lifetime = (int) (4.0D / (Math.random() * 0.9D + 0.1D));
        this.age = 0;
        this.makeStepSound = false;
    }

    public Particle setPower(float var1) {
        this.xd *= var1;
        this.yd = (this.yd - 0.1F) * var1 + 0.1F;
        this.zd *= var1;
        return this;
    }

    public Particle scale(float var1) {
        this.setSize(0.2F * var1, 0.2F * var1);
        this.size *= var1;
        return this;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.yd = (float) ((double) this.yd - 0.04D * (double) this.gravity);
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.98F;
        this.yd *= 0.98F;
        this.zd *= 0.98F;
        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
        }

    }

    public void render(ShapeRenderer shape, float partial, Vec3D right, Vec3D up) {
        float u = (tex % 16) / 16f;
        float v = (tex / 16) / 16f;
        renderQuad(shape, partial, right, up, u, v, 0.0624375F);
    }

    protected final void renderQuad(ShapeRenderer shape, float partial, Vec3D right, Vec3D up,
            float u, float v, float uvSize) {
        float radius = 0.1F * size;
        float px = xo + (x - xo) * partial;
        float py = yo + (y - yo) * partial;
        float pz = zo + (z - zo) * partial;
        float rx = right.x * radius, ry = right.y * radius, rz = right.z * radius;
        float ux = up.x * radius, uy = up.y * radius, uz = up.z * radius;
        float light = getRenderBrightness(partial);
        shape.color(rCol * light, gCol * light, bCol * light);
        shape.vertexUV(px - rx - ux, py - ry - uy, pz - rz - uz, u, v + uvSize);
        shape.vertexUV(px + rx - ux, py + ry - uy, pz + rz - uz, u + uvSize, v + uvSize);
        shape.vertexUV(px + rx + ux, py + ry + uy, pz + rz + uz, u + uvSize, v);
        shape.vertexUV(px - rx + ux, py - ry + uy, pz - rz + uz, u, v);
    }

    public int getParticleTexture() {
        return 0;
    }
}
