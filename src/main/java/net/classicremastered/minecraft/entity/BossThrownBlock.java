package net.classicremastered.minecraft.entity;
import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.render.*;

import org.lwjgl.opengl.GL11;

public final class BossThrownBlock extends Entity {
    private static final long serialVersionUID = -8182415197836441336L;
    public static final float GRAVITY = .025f;
    public static final float SMALL_BLOCK_WIDTH = .7f;
    private int cubeSize = 1;
    public int cubeSize() {
        return Math.max(1, cubeSize);
    }

    public int blockCount() {
        return cubeSize() * cubeSize() * cubeSize();
    }
    private final Mob owner;
    public final int blockId;
    private int age;
    private final boolean creativeThrow;
    public BossThrownBlock(Level level, Mob owner, int id, float x, float y, float z, Vec3D velocity) {
        super(level);
        this.owner = owner;
        creativeThrow = owner instanceof net.classicremastered.minecraft.player.Player && level.creativeMode;
        blockId = id;
        setSize(SMALL_BLOCK_WIDTH, SMALL_BLOCK_WIDTH);
        heightOffset = bbHeight * .5f;
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
        xd = velocity.x;
        yd = velocity.y;
        zd = velocity.z;
    }

    public BossThrownBlock(Level level, Mob owner, int id, float x, float y, float z, Vec3D velocity, int size) {
        this(level, owner, id, x, y, z, velocity);
        cubeSize = size == 2 ? 2 : 1;
        setSize(cubeSize, cubeSize);
        heightOffset = cubeSize * .5f;
        setPos(x, y, z);
    }

    @Override
    public void tick() {
        if (removed) return;
        boolean explosive = owner instanceof StickFigure;
        boolean large = explosive && cubeSize() == 2;
        if (explosive) {
            // Center collision on the rendered cube, including projectiles loaded from older saves.
            if (heightOffset != bbHeight * .5f) {
                heightOffset = bbHeight * .5f;
                setPos(x, y, z);
            }
        }
        yd -= GRAVITY;
        xo = x;
        yo = y;
        zo = z;
        ProjectileSweep.Impact impact =
                large ? ProjectileSweep.moveAgainstBlocks(this) : ProjectileSweep.move(this, owner);
        if (impact != null) {
            if (explosive) {
                detonate(large ? impact.blockContact() : new Vec3D(x, y, z));
            } else if (blockId == Block.TNT.id) {
                PrimedTnt.detonate(this, owner, 4f);
            } else {
                Mob target = impact.mob();
                boolean dodged =
                        target instanceof net.classicremastered.minecraft.mob.Enderman enderman
                                && enderman.tryEvadeProjectile();
                if (target != null && !dodged) target.hurt(owner, 5);
                shatter();
            }
            return;
        }
        if (explosive && level.particleEngine != null) {
            float spread = bbWidth * .7f;
            var random = java.util.concurrent.ThreadLocalRandom.current();
            for (int i = 0; i < cubeSize(); i++) {
                level.particleEngine.spawnParticle(
                        new SmokeParticle(
                                        level,
                                        (xo + x) * .5f + (random.nextFloat() - .5f) * spread,
                                        (yo + y) * .5f + (random.nextFloat() - .5f) * spread,
                                        (zo + z) * .5f + (random.nextFloat() - .5f) * spread,
                                        .025f)
                                .scale(1.5f + cubeSize() * .5f));
            }
        }
        if (++age >= (explosive ? 100 : 60)) {
            if (explosive) removed = true;
            else shatter();
        }
    }

    private void detonate(Vec3D contact) {
        removed = true;
        float power = cubeSize() == 2 ? 5f : 2f;
        level.playExplosionEffects(
                contact.x, contact.y, contact.z, power, 1f, cubeSize() == 2 ? .8f : 1.2f);
        if (level.particleEngine != null) {
            Block.blocks[blockId].spawnBreakParticles(
                    level,
                    (int) Math.floor(contact.x),
                    (int) Math.floor(contact.y),
                    (int) Math.floor(contact.z),
                    level.particleEngine);
        }
        level.explode(owner, contact.x, contact.y, contact.z, power, false);
    }

    private void shatter() {
        if (removed) return;
        removed = true;
        if (level.particleEngine != null)
            Block.blocks[blockId].spawnBreakParticles(level, (int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z), level.particleEngine);
        if (!creativeThrow)
            for (int i = 0; i < blockCount(); i++)
                level.addEntity(new DroppedBlock(level, x, y, z, blockId));
        level.playSound("random/anvil_land", this, .5f, 1.5f);
    }

    @Override
    public void render(TextureManager tm, float partial) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                    xo + (x - xo) * partial, yo + (y - yo) * partial, zo + (z - zo) * partial);
            BlockItemRenderer.renderCube(
                    Block.blocks[blockId], tm, getRenderBrightness(partial), cubeSize(), bbWidth);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
