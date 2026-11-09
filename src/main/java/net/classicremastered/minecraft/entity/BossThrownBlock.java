package net.classicremastered.minecraft.entity;
import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.render.*;
import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.particle.SmokeParticle;
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
        if (removed)
            return;
        boolean explosive = owner instanceof StickFigure;
        if (explosive) {
            // Center collision on the rendered cube, including projectiles loaded from older saves.
            if (heightOffset != bbHeight * .5f) {
                heightOffset = bbHeight * .5f;
                setPos(x, y, z);
            }
            yd -= GRAVITY;
        }
        xo = x;
        yo = y;
        zo = z;
        Vec3D start = new Vec3D(x, y, z), end = start.add(xd, yd, zd);
        AABB sweep = bb.expand(xd, yd, zd);
        float radius = bbWidth * .5f;
        float nearest = Float.POSITIVE_INFINITY;
        Mob victim = null;
        for (Object candidate : level.getCubes(sweep)) {
            nearest = Math.min(nearest, impactDistance((AABB) candidate, start, end, radius));
        }
        for (Object candidate : level.findEntities(this, sweep.grow(.001f, .001f, .001f))) {
            if (!(candidate instanceof Mob mob) || mob == owner || mob.removed || mob.health <= 0)
                continue;
            float distance = impactDistance(mob.bb, start, end, radius);
            if (distance < nearest) {
                nearest = distance;
                victim = mob;
            }
        }
        if (nearest < Float.POSITIVE_INFINITY) {
            float fraction = nearest == 0 ? 0 : nearest / start.distance(end);
            setPos(x + xd * fraction, y + yd * fraction, z + zd * fraction);
            if (explosive)
                detonate();
            else {
                if (victim != null)
                    victim.hurt(owner, 5);
                shatter();
            }
            return;
        }
        move(xd, yd, zd);
        yRot += 22;
        if (++age >= (explosive ? 100 : 60)) {
            if (explosive)
                removed = true;
            else
                shatter();
        }
    }

    private void detonate() {
        removed = true;
        level.playSound("random/explode", this, 1f, cubeSize() == 2 ? .8f : 1.2f);
        if (level.particleEngine != null) {
            Block.blocks[blockId].spawnBreakParticles(level, (int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z), level.particleEngine);
            for (int i = 0; i < 16; i++) {
                level.particleEngine.spawnParticle(new SmokeParticle(level, x + level.random.nextFloat() - .5f,
                        y + level.random.nextFloat() - .5f, z + level.random.nextFloat() - .5f));
            }
        }
        level.explode(owner, x, y, z, cubeSize() == 2 ? 3f : 2f, false);
    }

    private float impactDistance(AABB target, Vec3D start, Vec3D end, float radius) {
        if (bb.intersects(target))
            return 0;
        var hit = target.grow(radius, radius, radius).clip(start, end);
        return hit == null ? Float.POSITIVE_INFINITY : start.distance(hit.vec);
    }

    private void shatter() {
        if (removed)
            return;
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
            GL11.glTranslatef(xo + (x - xo) * partial, yo + (y - yo) * partial, zo + (z - zo) * partial);
            GL11.glRotatef(yRot, 1, 1, 0);
            BlockItemRenderer.renderCube(Block.blocks[blockId], tm, getRenderBrightness(partial), cubeSize(), bbWidth);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
