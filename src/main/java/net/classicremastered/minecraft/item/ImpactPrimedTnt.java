// file: src/com/mojang/minecraft/item/ImpactPrimedTnt.java
package net.classicremastered.minecraft.item;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.particle.TerrainParticle;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

/**
 * Sniper-accurate TNT:
 * - Explodes on touching the ground.
 * - Explodes on touching a Player (owner briefly ignored).
 * - Optional "straight" (no drop) flight toward a target, like a hitscan-ish lob.
 * - Lifetime can be set to reach a given attack range (distance = speed * life).
 */
public class ImpactPrimedTnt extends Entity {

    public static final long serialVersionUID = 0L;

    // --- flight tuning ---
    private float gravity = 0.00F;        // 0 for “sniper-straight”; set to 0.04F for arc
    private float airDrag = 1.00F;        // 1 for no slow-down in straight mode
    private float groundFriction = 0.70F; // visual bounce before explosion is moot (we explode on ground)
    private float speed;                  // cached launch speed (blocks/tick)

    // --- explosion / fuse ---
    private float explosionPower = 4.0F;  // Classic-style power
    public int life = 40;                 // fuse (ticks)
    private boolean defused = false;

    // --- collision logic ---
    private transient Entity ownerRef = null;
    private int ownerIgnoreTicks = 6;     // ignore owner for first ~0.3s
    private boolean explodeOnGround = true;
    private boolean explodeOnPlayer = true;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    /** Vanilla-ish: primed fuse at rest (2s fuse). */
    public ImpactPrimedTnt(Level level, float x, float y, float z) {
        super(level);
        setSize(0.98F, 0.98F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);
        makeStepSound = false;

        // small random nudge
        float angle = (float)(Math.random() * Math.PI * 2.0);
        this.xd = -MathHelper.sin(angle) * 0.02F;
        this.yd =  0.20F;
        this.zd = -MathHelper.cos(angle) * 0.02F;

        // default “rest” behavior uses gravity/drag like normal TNT
        this.gravity = 0.04F;
        this.airDrag = 0.98F;

        this.speed = (float) Math.sqrt(xd*xd + yd*yd + zd*zd);
        this.life = 40; // ~2s
        this.xo = x; this.yo = y; this.zo = z;
    }

    /**
     * Classic-style throw (yaw/pitch + power + fuse).
     * Keep gravity for arc; not as accurate as sniper constructor below.
     */
    public ImpactPrimedTnt(Level level, Entity owner,
                           float x, float y, float z,
                           float yawDeg, float pitchDeg, float power, int fuseTicks) {
        super(level);
        setSize(0.25F, 0.25F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);
        makeStepSound = false;

        this.ownerRef = owner;

        float yaw = yawDeg   * (float)Math.PI / 180.0F;
        float pit = pitchDeg * (float)Math.PI / 180.0F;

        float vx = -MathHelper.sin(yaw) * MathHelper.cos(pit);
        float vz =  MathHelper.cos(yaw) * MathHelper.cos(pit);
        float vy = -MathHelper.sin(pit);

        this.xd = vx * power;
        this.yd = vy * power + 0.04F;
        this.zd = vz * power;

        // “normal” throw flight
        this.gravity = 0.04F;
        this.airDrag = 0.98F;

        this.speed = power;
        this.life = (fuseTicks > 0 ? fuseTicks : 40);
        this.xo = x; this.yo = y; this.zo = z;
    }

    /**
     * Sniper-accurate straight shot toward a target point.
     * - No gravity / no drag (bullet-like).
     * - Life is clamped to reach at most maxDistance (attack range).
     *
     * @param maxDistance how far it may travel before fuse ends (use your mob's attack range)
     */
    public ImpactPrimedTnt(Level level, Entity owner,
                           float x, float y, float z,
                           float targetX, float targetY, float targetZ,
                           float power, int fuseTicks, float maxDistance) {
        super(level);
        setSize(0.25F, 0.25F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);
        makeStepSound = false;

        this.ownerRef = owner;

        // Direction vector to the target (no spread)
        float dx = targetX - x;
        float dy = targetY - y;
        float dz = targetZ - z;
        float len = MathHelper.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 1.0E-4F) len = 1.0F;
        dx /= len; dy /= len; dz /= len;

        // Straight flight: no gravity, no drag
        this.gravity = 0.00F;
        this.airDrag = 1.00F;

        this.xd = dx * power;
        this.yd = dy * power;
        this.zd = dz * power;
        this.speed = power;

        // Life so it reaches (up to) the attack range
        int lifeByRange = (maxDistance > 0 && power > 0)
                ? Math.max(4, (int)Math.ceil(maxDistance / power) + 2)
                : 40;

        this.life = (fuseTicks > 0)
                ? Math.min(fuseTicks, lifeByRange)
                : lifeByRange;

        this.xo = x; this.yo = y; this.zo = z;
    }

    // ------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();

        // last pos for interpolation
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (ownerIgnoreTicks > 0) ownerIgnoreTicks--;

        // gravity (0 in “sniper” mode)
        this.yd -= gravity;

        // move with collisions
        this.move(this.xd, this.yd, this.zd);

        // drag (1.0 in “sniper” mode)
        this.xd *= airDrag;
        this.yd *= airDrag;
        this.zd *= airDrag;

        // Ground impact -> explode immediately (if enabled)
        if (explodeOnGround && this.onGround) {
            // small damp just for visual consistency; then explode
            this.xd *= groundFriction;
            this.zd *= groundFriction;
            this.yd *= -0.5F;
            explode();
            return;
        }

        // Entity contact: explode on Player touch (owner ignored briefly)
        if (explodeOnPlayer) {
            List hits = this.level.findEntities(this, this.bb.grow(0.1F, 0.1F, 0.1F));
            for (int i = 0; i < hits.size(); i++) {
                Entity e = (Entity) hits.get(i);
                if (e == this) continue;
                if (ownerIgnoreTicks > 0 && e == ownerRef) continue;

                if (e instanceof Player) {
                    explode();
                    return;
                }
            }
        }

        // Fuse countdown / smoke
        if (!defused) {
            if (life-- > 0) {
                // small trail for flavor
                this.level.particleEngine.spawnParticle(
                        new SmokeParticle(level, x, y + 0.2F, z));
            } else {
                explode();
            }
        }
    }

    // ------------------------------------------------------------
    // Explosion
    // ------------------------------------------------------------
    private void explode() {
        if (this.removed) return;
        this.remove();

        try {
            // Adapt if your Level uses a different signature.
            this.level.explode(this.ownerRef, this.x, this.y, this.z, explosionPower);
        } catch (Throwable ignore) {
            // If your fork lacks explode API, wire it as needed or leave particles only.
        }

        // debris particles (terrain-colored)
        Random random = new Random();
        float radius = explosionPower;
        for (int i = 0; i < 100; i++) {
            float rx = (float)random.nextGaussian() * radius / 4.0F;
            float ry = (float)random.nextGaussian() * radius / 4.0F;
            float rz = (float)random.nextGaussian() * radius / 4.0F;
            float len = MathHelper.sqrt(rx*rx + ry*ry + rz*rz);
            if (len < 0.001F) len = 0.001F;
            float vx = rx / len / len;
            float vy = ry / len / len;
            float vz = rz / len / len;
            this.level.particleEngine.spawnParticle(
                    new TerrainParticle(level, x + rx, y + ry, z + rz, vx, vy, vz, Block.TNT));
        }
    }

    // ------------------------------------------------------------
    // Rendering (TNT cube + flashing overlay)
    // ------------------------------------------------------------
    @Override
    public void render(TextureManager textureManager, float partial) {
        int tex = textureManager.load("/terrain.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        if (level != null && level.height > 0) {
            by = Math.max(0, Math.min(level.height - 1, by));
        }
        float brightness = 1.0F;
        try {
            brightness = level.getBrightness(bx, by, bz);
        } catch (Throwable ignored) {}

        GL11.glPushMatrix();
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        GL11.glTranslatef(
                xo + (x - xo) * partial - 0.5F,
                yo + (y - yo) * partial - 0.5F,
                zo + (z - zo) * partial - 0.5F
        );

        ShapeRenderer sr = ShapeRenderer.instance;
        Block.TNT.renderPreview(sr);

        // flashing overlay near detonation
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);

        float alpha = ((life / 4 + 1) % 2) * 0.4F;
        if (life <= 16) alpha = ((life + 1) % 2) * 0.6F;
        if (life <=  2) alpha = 0.9F;

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
    public boolean isPickable() { return !this.removed; }

    // --- Optional setters if you want to tweak at runtime ---
    public ImpactPrimedTnt setExplosionPower(float p) { this.explosionPower = p; return this; }
    public ImpactPrimedTnt setExplodeOnGround(boolean b) { this.explodeOnGround = b; return this; }
    public ImpactPrimedTnt setExplodeOnPlayer(boolean b) { this.explodeOnPlayer = b; return this; }
}
