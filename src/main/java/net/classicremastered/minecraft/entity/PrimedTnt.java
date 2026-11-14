// file: src/com/mojang/minecraft/item/PrimedTnt.java
package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.TntExplosion;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.particle.TerrainParticle;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

import java.util.Random;

public class PrimedTnt extends Entity {

    public static final long serialVersionUID = 0L;

    // --- config ---
    private float gravity = 0.04F;
    private float airDrag = 0.98F;
    private float groundFriction = 0.70F;
    protected float explosionPower = 4.0F; // Classic-style power
    public static final int DEFAULT_FUSE = 80;
    public int life = DEFAULT_FUSE;

    // ignore friendly contact briefly
    private transient Entity ownerRef = null;
    private boolean playerIgniter;
    private boolean impactFuse;

    public PrimedTnt(Level level, float x, float y, float z) {
        this(level, x, y, z, null, DEFAULT_FUSE);
    }

    public PrimedTnt(Level level, float x, float y, float z, Entity owner, int fuse) {
        super(level);
        this.ownerRef = owner;
        this.playerIgniter = owner instanceof net.classicremastered.minecraft.player.Player;
        setSize(0.98F, 0.98F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);
        makeStepSound = false;

        // small random nudge
        float angle = (float) (level.random.nextFloat() * Math.PI * 2.0);
        this.xd = -MathHelper.sin(angle) * 0.02F;
        this.yd = 0.20F;
        this.zd = -MathHelper.cos(angle) * 0.02F;

        this.life = fuse;
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    /** Thrown TNT: aimed by yaw/pitch, custom power and fuse. */
    public PrimedTnt(
            Level level,
            Entity owner,
            float x,
            float y,
            float z,
            float yawDeg,
            float pitchDeg,
            float power,
            int fuseTicks) {
        super(level);
        setSize(0.25F, 0.25F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);
        makeStepSound = false;

        this.ownerRef = owner;
        this.playerIgniter = owner instanceof net.classicremastered.minecraft.player.Player;

        float yaw = yawDeg * (float) Math.PI / 180.0F;
        float pit = pitchDeg * (float) Math.PI / 180.0F;

        float vx = -MathHelper.sin(yaw) * MathHelper.cos(pit);
        float vz = MathHelper.cos(yaw) * MathHelper.cos(pit);
        float vy = -MathHelper.sin(pit);

        this.xd = vx * power;
        this.yd = vy * power + 0.04F; // small arc lift
        this.zd = vz * power;

        this.life = (fuseTicks > 0 ? fuseTicks : DEFAULT_FUSE);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Override
    public void tick() {
        super.tick();
        applyLiquidCurrent();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (impactFuse) {
            Entity owner = ownerRef != null ? ownerRef : playerIgniter ? level.player : null;
            if (ProjectileSweep.move(this, owner) != null) {
                explode();
            } else {
                tickFuse();
            }
            return;
        }

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
        }

        tickFuse();
    }

    public boolean hasPlayerIgniter() {
        return playerIgniter;
    }

    public void restorePlayerIgniter(boolean playerIgniter) {
        this.playerIgniter = playerIgniter;
    }

    public void armOnImpact(Entity thrower) {
        ownerRef = thrower;
        playerIgniter = thrower instanceof net.classicremastered.minecraft.player.Player;
        impactFuse = true;
    }

    public boolean hasImpactFuse() {
        return impactFuse;
    }

    public void setImpactFuse(boolean impactFuse) {
        this.impactFuse = impactFuse;
    }

    public void tickFuse() {
        if (removed) return;
        if (--life <= 0) {
            explode();
        } else if (level.particleEngine != null) {
            level.particleEngine.spawnParticle(new SmokeParticle(level, x, y + .6f, z));
        }
    }

    private void explode() {
        Entity owner = ownerRef != null ? ownerRef : playerIgniter ? level.player : null;
        detonate(this, owner, explosionPower);
    }

    static void detonate(Entity source, Entity owner, float power) {
        if (source.removed) return;
        source.remove();
        Level level = source.level;
        float x = source.x, y = source.y, z = source.z;
        level.playExplosionEffects(x, y, z, power);
        TntExplosion.detonate(level, source, owner, x, y, z, power);
        if (level.particleEngine == null) return;

        // debris particles...
        Random random = new Random();
        float radius = power;
        for (int i = 0; i < 100; i++) {
            float rx = (float) random.nextGaussian() * radius / 4.0F;
            float ry = (float) random.nextGaussian() * radius / 4.0F;
            float rz = (float) random.nextGaussian() * radius / 4.0F;
            float len = MathHelper.sqrt(rx * rx + ry * ry + rz * rz);
            if (len < 0.001F) len = 0.001F;
            float vx = rx / len / len;
            float vy = ry / len / len;
            float vz = rz / len / len;
            level.particleEngine.spawnParticle(
                    new TerrainParticle(level, x + rx, y + ry, z + rz, vx, vy, vz, Block.TNT));
        }
    }

    // --- rendering as a TNT cube with flash overlay ---
    @Override
    public void render(TextureManager textureManager, float partial) {
        int tex =
                textureManager.load(
                        net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE);
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_TEXTURE_BIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        float brightness = 1.0F;
        try {
            brightness = this.getRenderBrightness(partial);
        } catch (Throwable ignored) {
        }

        GL11.glPushMatrix();
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        GL11.glTranslatef(
                xo + (x - xo) * partial - bbWidth * .5f,
                yo + (y - yo) * partial - bbHeight * .5f,
                zo + (z - zo) * partial - bbWidth * .5f);
        GL11.glScalef(bbWidth, bbHeight, bbWidth);

        ShapeRenderer sr = ShapeRenderer.instance;
        Block.TNT.renderPreview(sr);

        // flashing overlay
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);

        float alpha = ((life / 4 + 1) % 2) * 0.4F;
        if (life <= 16) alpha = ((life + 1) % 2) * 0.6F;
        if (life <= 2) alpha = 0.9F;

        GL11.glColor4f(1F, 1F, 1F, alpha);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        Block.TNT.renderPreview(sr);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    @Override
    public boolean isPickable() {
        return !this.removed;
    }
}
