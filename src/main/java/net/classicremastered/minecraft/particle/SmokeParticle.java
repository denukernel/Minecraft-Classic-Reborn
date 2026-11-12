package net.classicremastered.minecraft.particle;

import net.classicremastered.minecraft.level.Level;

public class SmokeParticle extends Particle {

    private static final long serialVersionUID = 1L;

    public SmokeParticle(Level var1, float var2, float var3, float var4) {
        this(var1, var2, var3, var4, 0);
    }

    public SmokeParticle(Level level, float x, float y, float z, float rise) {
        super(level, x, y, z, 0, 0, 0);
        this.xd *= 0.1F;
        this.yd = this.yd * 0.1F + rise;
        this.zd *= 0.1F;
        this.rCol = this.gCol = this.bCol = (float) (Math.random() * 0.30000001192092896D);
        this.lifetime = (int) (8.0D / (Math.random() * 0.8D + 0.2D));
        this.noPhysics = true;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.tex = 7 - (this.age << 3) / this.lifetime;
        this.yd = (float) ((double) this.yd + 0.004D);
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.96F;
        this.yd *= 0.96F;
        this.zd *= 0.96F;
        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
        }
    }
}
