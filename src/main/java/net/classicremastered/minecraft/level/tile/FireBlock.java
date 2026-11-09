package net.classicremastered.minecraft.level.tile;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;

public final class FireBlock extends Block {
    private static final int[][] NEIGHBORS = {
        {0,-1,0}, {0,1,0}, {0,0,-1}, {0,0,1}, {-1,0,0}, {1,0,0}
    };

    public FireBlock(int id, int tex) {
        super(id, tex, "Fire");
        this.setData(Tile$SoundType.none, 1f, 1f, 0f);
        this.setTickDelay(30);
        this.setPhysics(true);
    }

    @Override public int getLightValue() { return 14; }
    @Override public boolean isSolid() { return false; }
    @Override public boolean isOpaque() { return false; }
    @Override public boolean isCube() { return false; }
    @Override public AABB getCollisionBox(int x, int y, int z) { return null; }
    @Override public int getDropCount() { return 0; }
    @Override public int getDrop() { return 0; }
    @Override public void onBreak(Level level, int x, int y, int z) {}

    public static boolean isFlammable(int id) {
        if (id <= 0 || id >= Block.blocks.length) return false;
        Block block = Block.blocks[id];
        return block == Block.WOOD || block == Block.LOG || block == Block.LEAVES
                || block == Block.BOOKSHELF || block == Block.TNT || block == Block.DANDELION
                || block == Block.ROSE || id >= Block.RED_WOOL.id && id <= Block.WHITE_WOOL.id;
    }

    static boolean hasFuel(Level level, int x, int y, int z) {
        for (int[] side : NEIGHBORS) {
            if (isFlammable(level.getTile(x + side[0], y + side[1], z + side[2]))) return true;
        }
        return false;
    }

    public static boolean canStay(Level level, int x, int y, int z) {
        return level.isInBounds(x, y, z)
                && (level.isSolidTile(x, y - 1, z) || hasFuel(level, x, y, z));
    }

    public static void extinguish(Level level, int x, int y, int z) {
        if (level != null && level.getTile(x, y, z) == Block.FIRE.id) level.setTile(x, y, z, 0);
    }

    @Override public void onAdded(Level level, int x, int y, int z) {
        if (level.isNetworkMode()) {
            return;
        }
        if (!canStay(level, x, y, z)) extinguish(level, x, y, z);
        else level.addToTickNextTick(x, y, z, id);
    }

    @Override public void onNeighborChange(Level level, int x, int y, int z, int changedBlock) {
        if (level.isNetworkMode()) {
            return;
        }
        if (!canStay(level, x, y, z)) extinguish(level, x, y, z);
    }

    @Override public void update(Level level, int x, int y, int z, Random random) {
        if (level.isNetworkMode() || level.getTile(x, y, z) != id)
            return;
        if (!canStay(level, x, y, z)) {
            extinguish(level, x, y, z);
            return;
        }
        if (!hasFuel(level, x, y, z)) {
            if (random.nextInt(4) == 0) {
                extinguish(level, x, y, z);
                return;
            }
        } else {
            for (int[] side : NEIGHBORS) {
                int tx = x + side[0], ty = y + side[1], tz = z + side[2];
                if (!level.isInBounds(tx, ty, tz)) continue;
                int fuel = level.getTile(tx, ty, tz);
                if (fuel == Block.TNT.id) {
                    TNTBlock.ignite(level, tx, ty, tz);
                } else if (isFlammable(fuel) && random.nextInt(
                        fuel == Block.LEAVES.id ? 4 : fuel == Block.LOG.id ? 12 : 8) == 0) {
                    int replacement = canStay(level, tx, ty, tz) && random.nextBoolean() ? id : 0;
                    level.setTile(tx, ty, tz, replacement);
                }
            }

            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int tx = x + dx, ty = y + dy, tz = z + dz;
                    if (level.isInBounds(tx, ty, tz) && level.getTile(tx, ty, tz) == 0
                            && hasFuel(level, tx, ty, tz) && random.nextInt(dy > 0 ? 24 : 40) == 0) {
                        level.setTile(tx, ty, tz, id);
                    }
                }
            }
        }
        if (level.getTile(x, y, z) == id) level.addToTickNextTick(x, y, z, id);
    }

    @Override public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        renderer.color(1, 1, 1);
        if (level.isSolidTile(x, y - 1, z) || isFlammable(level.getTile(x, y - 1, z))) {
            renderFlames(renderer, x, y, z, 1f, 1.36f);
        } else {
            if (isFlammable(level.getTile(x - 1, y, z)))
                plane(renderer, x+.04f,z, x+.04f,z+1, x+.20f,z, x+.20f,z+1, y,1f,false);
            if (isFlammable(level.getTile(x + 1, y, z)))
                plane(renderer, x+.96f,z+1, x+.96f,z, x+.80f,z+1, x+.80f,z, y,1f,true);
            if (isFlammable(level.getTile(x, y, z - 1)))
                plane(renderer, x+1,z+.04f, x,z+.04f, x+1,z+.20f, x,z+.20f, y,1f,true);
            if (isFlammable(level.getTile(x, y, z + 1)))
                plane(renderer, x,z+.96f, x+1,z+.96f, x,z+.80f, x+1,z+.80f, y,1f,false);
            if (isFlammable(level.getTile(x, y + 1, z)))
                renderFlames(renderer, x, y+.42f, z, 1f, .58f);
        }
        return true;
    }

    /** Outer sheets and crossed inner flames, in the same cutout batch as terrain. */
    public static void renderFlames(ShapeRenderer renderer, float x, float y, float z, float width, float height) {
        float farX = x + width, farZ = z + width;
        float inset = width * .13f;
        plane(renderer, x,z, farX,z, x,z+inset, farX,z+inset, y,height*.76f,false);
        plane(renderer, farX,farZ, x,farZ, farX,farZ-inset, x,farZ-inset, y,height*.82f,true);
        plane(renderer, x,farZ, x,z, x+inset,farZ, x+inset,z, y,height*.80f,true);
        plane(renderer, farX,z, farX,farZ, farX-inset,z, farX-inset,farZ, y,height*.74f,false);
        float near = width * .28f, far = width * .72f;
        plane(renderer, x,z+near, farX,z+near, x,z+far, farX,z+far, y,height*1.08f,true);
        plane(renderer, x,z+far, farX,z+far, x,z+near, farX,z+near, y,height,false);
        plane(renderer, x+near,z, x+near,farZ, x+far,z, x+far,farZ, y,height*.96f,false);
        plane(renderer, x+far,z, x+far,farZ, x+near,z, x+near,farZ, y,height*1.06f,true);
    }

    private static void plane(ShapeRenderer r, float ax,float az,float bx,float bz,
            float tx,float tz,float ux,float uz,float y,float height,boolean mirrored) {
        int tile = Block.FIRE.textureId;
        float u0 = (tile % 16 + .01f) / 16f, u1 = (tile % 16 + .99f) / 16f;
        float v0 = (tile / 16 + .01f) / 16f, v1 = (tile / 16 + .99f) / 16f;
        float left = mirrored ? u1 : u0, right = mirrored ? u0 : u1;
        r.vertexUV(ax,y,az,left,v1);
        r.vertexUV(bx,y,bz,right,v1);
        r.vertexUV(ux,y+height,uz,right,v0);
        r.vertexUV(tx,y+height,tz,left,v0);
        r.vertexUV(tx,y+height,tz,left,v0);
        r.vertexUV(ux,y+height,uz,right,v0);
        r.vertexUV(bx,y,bz,right,v1);
        r.vertexUV(ax,y,az,left,v1);
    }
}
