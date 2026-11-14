package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.FireBlock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.BlockItemRenderer;
import net.classicremastered.minecraft.render.BlockTextureAtlas;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.util.CreativeModeHelper;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

/** The Stick Figure's deflectable magma projectile. */
public final class BossFireball extends Entity {
    private static final long serialVersionUID = 1L;
    public static final float GRAVITY = .025f;
    private static final float EXPLOSION_POWER = 3f;
    private final Mob owner;
    private Player deflectedBy;
    private int age;

    public BossFireball(Level level, Mob owner, float x, float y, float z, Vec3D velocity) {
        super(level);
        this.owner = owner;
        setSize(.5f, .5f);
        heightOffset = bbHeight * .5f;
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
        xd = velocity.x;
        yd = velocity.y;
        zd = velocity.z;
        ignite(120);
    }

    @Override
    public boolean isPickable() {
        return !removed && deflectedBy == null;
    }

    public void deflect(Player player) {
        if (!isPickable()
                || player.level != level
                || player.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(player)) return;
        double yaw = Math.toRadians(-player.aimYaw(1) - 180);
        double pitch = Math.toRadians(-player.xRot);
        float speed = 1.15f;
        xd = (float) (Math.sin(yaw) * Math.cos(pitch)) * speed;
        yd = (float) Math.sin(pitch) * speed;
        zd = (float) (Math.cos(yaw) * Math.cos(pitch)) * speed;
        deflectedBy = player;
        age = 0;
        level.playSound("random/bow", this, .9f, 1.5f);
    }

    @Override
    public void tick() {
        if (removed) return;
        if (owner.removed
                || owner.health <= 0
                || owner.level != level
                || level.creativeMode
                || CombatRules.difficulty(level) == Difficulty.PEACEFUL) {
            remove();
            return;
        }
        xo = x;
        yo = y;
        zo = z;
        yd -= GRAVITY;
        Mob attacker = deflectedBy == null ? owner : deflectedBy;
        ProjectileSweep.Impact impact = ProjectileSweep.move(this, attacker);
        if (remainingFireTicks > 0 && (isInWater() || isUnderWater())) extinguishFire();
        if (impact != null) {
            Mob victim = impact.mob();
            if (victim != null) {
                victim.hurt(attacker, 6);
                if (remainingFireTicks > 0) victim.ignite(80);
                if (deflectedBy != null
                        && victim instanceof StickFigure boss
                        && boss.ai instanceof StickFigureAI ai) ai.powers.stagger(boss);
            }
            remove();
            level.playExplosionEffects(x, y, z, EXPLOSION_POWER, .7f, 1.4f);
            if (level.particleEngine != null)
                Block.MAGMA.spawnBreakParticles(
                        level,
                        (int) Math.floor(x),
                        (int) Math.floor(y),
                        (int) Math.floor(z),
                        level.particleEngine);
            level.explode(attacker, x, y, z, EXPLOSION_POWER);
            if (remainingFireTicks > 0 && !level.isNetworkMode()) igniteGround();
            return;
        }
        if (level.particleEngine != null && remainingFireTicks > 0) {
            for (int i = 1; i <= 2; i++) {
                float along = i / 2f;
                level.particleEngine.spawnParticle(
                        new SmokeParticle(
                                        level,
                                        xo + (x - xo) * along,
                                        yo + (y - yo) * along,
                                        zo + (z - zo) * along,
                                        .035f)
                                .scale(2f));
            }
        }
        if (++age >= 120) remove();
    }

    private void igniteGround() {
        int centerX = MathHelper.floor(x),
                centerY = MathHelper.floor(y),
                centerZ = MathHelper.floor(z);
        int fires = 0;
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int bx = centerX + dx, bz = centerZ + dz;
                    for (int by = centerY + 1; by >= centerY - (int) EXPLOSION_POWER; by--) {
                        if (!level.isInBounds(bx, by, bz)
                                || level.getTile(bx, by, bz) != 0
                                || !level.isSolidTile(bx, by - 1, bz)) continue;
                        if (level.setTile(bx, by, bz, Block.FIRE.id)
                                && level.getTile(bx, by, bz) == Block.FIRE.id) {
                            if (++fires == 3) return;
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                    xo + (x - xo) * partial, yo + (y - yo) * partial, zo + (z - zo) * partial);
            GL11.glRotatef((age + partial) * 15, 1, 1, 0);
            BlockItemRenderer.renderCube(
                    Block.MAGMA, tm, Math.max(.65f, getRenderBrightness(partial)), 1, bbWidth);
        } finally {
            GL11.glPopMatrix();
        }
        if (remainingFireTicks > 0) renderFlames(tm, partial);
    }

    private void renderFlames(TextureManager tm, float partial) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load(BlockTextureAtlas.RESOURCE));
            float width = bbWidth * 1.65f;
            ShapeRenderer shape = ShapeRenderer.instance;
            shape.begin();
            shape.color(1, 1, 1);
            FireBlock.renderFlames(
                    shape,
                    xo + (x - xo) * partial - width * .5f,
                    yo + (y - yo) * partial - width * .5f,
                    zo + (z - zo) * partial - width * .5f,
                    width,
                    width * 1.4f);
            shape.end();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
