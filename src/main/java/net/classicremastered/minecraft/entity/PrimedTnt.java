// file: src/com/mojang/minecraft/item/PrimedTnt.java
package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.particle.TerrainParticle;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class PrimedTnt extends Entity {

    public static final long serialVersionUID = 0L;

    // --- config ---
    private float gravity = 0.04F;
    private float airDrag = 0.98F;
    private float groundFriction = 0.70F;
    protected float explosionPower = 4.0F; // Classic-style power
    public int life = 40; // fuse (ticks)
    private boolean defused = false;

    // ignore friendly contact briefly
    private transient Entity ownerRef = null;
    private int ownerIgnoreTicks = 6; // ~0.3s grace

    public PrimedTnt(Level level, float x, float y, float z) {
        super(level);
        setSize(0.98F, 0.98F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);
        makeStepSound = false;

        // small random nudge
        float angle = (float) (Math.random() * Math.PI * 2.0);
        this.xd = -MathHelper.sin(angle) * 0.02F;
        this.yd = 0.20F;
        this.zd = -MathHelper.cos(angle) * 0.02F;

        this.life = 40; // ~2s
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    /** Thrown TNT: aimed by yaw/pitch, custom power and fuse. */
    public PrimedTnt(Level level, Entity owner, float x, float y, float z, float yawDeg, float pitchDeg, float power,
            int fuseTicks) {
        super(level);
        setSize(0.25F, 0.25F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);
        makeStepSound = false;

        this.ownerRef = owner;

        float yaw = yawDeg * (float) Math.PI / 180.0F;
        float pit = pitchDeg * (float) Math.PI / 180.0F;

        float vx = -MathHelper.sin(yaw) * MathHelper.cos(pit);
        float vz = MathHelper.cos(yaw) * MathHelper.cos(pit);
        float vy = -MathHelper.sin(pit);

        this.xd = vx * power;
        this.yd = vy * power + 0.04F; // small arc lift
        this.zd = vz * power;

        this.life = (fuseTicks > 0 ? fuseTicks : 40);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Override
    public void tick() {
        super.tick();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (ownerIgnoreTicks > 0)
            ownerIgnoreTicks--;

        // gravity + motion
        this.yd -= gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= airDrag;
        this.yd *= airDrag;
        this.zd *= airDrag;

        if (this.onGround) {
            this.xd *= groundFriction;
            this.zd *= groundFriction;
            this.yd *= -0.5F;
            float sp2 = this.xd * this.xd + this.yd * this.yd + this.zd * this.zd;
            if (sp2 > 0.40F) {
                explode();
                return;
            }
        }

        // fuse countdown
        if (!defused) {
            if (life-- == 40) {
                // --- play primed fuse sound when first lit ---
                if (this.level != null) {
                    this.level.playSound("random/fuse", this, 1.0F, 1.0F);
                }
            }

            if (life > 0) {
                this.level.particleEngine.spawnParticle(new SmokeParticle(level, x, y + 0.6F, z));
            } else {
                explode();
            }
        }
    }

    private void explode() {
        if (this.removed)
            return;
        this.remove();

        // --- play explosion sound ---
        if (this.level != null) {
            this.level.playSound("random/explode", this, 1.0F, 1.0F);
        }

        try {
            this.level.explode(this.ownerRef, this.x, this.y, this.z, explosionPower);
        } catch (Throwable ignore) {
        }

        // debris particles...
        Random random = new Random();
        float radius = explosionPower;
        for (int i = 0; i < 100; i++) {
            float rx = (float) random.nextGaussian() * radius / 4.0F;
            float ry = (float) random.nextGaussian() * radius / 4.0F;
            float rz = (float) random.nextGaussian() * radius / 4.0F;
            float len = MathHelper.sqrt(rx * rx + ry * ry + rz * rz);
            if (len < 0.001F)
                len = 0.001F;
            float vx = rx / len / len;
            float vy = ry / len / len;
            float vz = rz / len / len;
            this.level.particleEngine
                    .spawnParticle(new TerrainParticle(level, x + rx, y + ry, z + rz, vx, vy, vz, Block.TNT));
        }
    }

    // --- rendering as a TNT cube with flash overlay ---
    @Override
    public void render(TextureManager textureManager, float partial) {
        int tex = textureManager.load("/terrain.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        if (level != null) {
            if (level.height > 0) {
                by = Math.max(0, Math.min(level.height - 1, by));
            }
        }
        float brightness = 1.0F;
        try {
            brightness = level.getBrightness(bx, by, bz);
        } catch (Throwable ignored) {
        }

        GL11.glPushMatrix();
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        GL11.glTranslatef(xo + (x - xo) * partial - 0.5F, yo + (y - yo) * partial - 0.5F,
                zo + (z - zo) * partial - 0.5F);

        ShapeRenderer sr = ShapeRenderer.instance;
        Block.TNT.renderPreview(sr);

        // flashing overlay
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);

        float alpha = ((life / 4 + 1) % 2) * 0.4F;
        if (life <= 16)
            alpha = ((life + 1) % 2) * 0.6F;
        if (life <= 2)
            alpha = 0.9F;

        GL11.glColor4f(1F, 1F, 1F, alpha);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        Block.TNT.renderPreview(sr);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @Override
    public boolean isPickable() {
        return !this.removed;
    }
}
