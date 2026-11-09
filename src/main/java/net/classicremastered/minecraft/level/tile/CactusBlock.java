// File: src/net/classicremastered/minecraft/level/tile/CactusBlock.java
package net.classicremastered.minecraft.level.tile;

import java.util.Random;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.level.Level;

public final class CactusBlock extends Block {
    private static final int SIDE_TEX = 179; // cactus_side
    private static final int TOP_TEX  = 180; // cactus_top

    public CactusBlock(int id) {
        super(id, SIDE_TEX, "Cactus");
        setPhysics(true);
        setBounds(1 / 16f, 0, 1 / 16f, 15 / 16f, 1, 15 / 16f);
        this.setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.4F);
    }

    private boolean hasSupport(Level level, int x, int y, int z) {
        int below = level.getTile(x, y - 1, z);
        return y > 0 && (below == SAND.id || below == id);
    }

    @Override
    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        return hasSupport(level, x, y, z);
    }

    @Override
    public int getTickDelay() {
        return 200;
    }

    @Override
    public void onAdded(Level level, int x, int y, int z) {
        scheduleGrowth(level, x, y, z);
    }

    private boolean canGrow(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y + 1, z)
                || level.getTile(x, y + 1, z) != 0
                || !hasSupport(level, x, y, z)) return false;
        if (level.getTile(x, y - 1, z) == id && level.getTile(x, y - 2, z) == id) return false;
        return clearSide(level, x - 1, y + 1, z)
                && clearSide(level, x + 1, y + 1, z)
                && clearSide(level, x, y + 1, z - 1)
                && clearSide(level, x, y + 1, z + 1);
    }

    private boolean clearSide(Level level, int x, int y, int z) {
        int neighbor = level.getTile(x, y, z);
        return neighbor != LAVA.id && neighbor != STATIONARY_LAVA.id && !level.isSolidTile(x, y, z);
    }

    private void scheduleGrowth(Level level, int x, int y, int z) {
        if (canGrow(level, x, y, z)) level.addToTickNextTick(x, y, z, id);
    }

    @Override
    public void update(Level level, int x, int y, int z, Random random) {
        if (level.isNetworkMode() || level.getTile(x, y, z) != id || !canGrow(level, x, y, z))
            return;
        int age = level.getCactusAge(x, y, z);
        if (age < 15) {
            level.setCactusAge(x, y, z, age + 1);
        } else if (level.setTile(x, y + 1, z, id)) {
            level.setCactusAge(x, y, z, 0);
        }
        scheduleGrowth(level, x, y, z);
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (!level.isNetworkMode()
                && level.getTile(x, y, z) == id
                && !hasSupport(level, x, y, z)
                && level.setTile(x, y, z, 0)) {
            if (level.particleEngine != null)
                spawnBreakParticles(level, x, y, z, level.particleEngine);
            dropItems(level, x, y, z, 1);
        } else if (level.getTile(x, y, z) == id) {
            scheduleGrowth(level, x, y, z);
        }
    }

    @Override
    protected int getTextureId(int side) {
        return side == 0 ? 204 : side == 1 ? TOP_TEX : SIDE_TEX;
    }

    private static final float INSET = 1 / 16f;
    private static final float FAR = 1 - INSET;
    private static final float[][] POINTS = {
        {0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1},
        {1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 1, 1},
        {0, 1, INSET, 1, 1, INSET, 1, 0, INSET, 0, 0, INSET},
        {0, 1, FAR, 0, 0, FAR, 1, 0, FAR, 1, 1, FAR},
        {INSET, 1, 1, INSET, 1, 0, INSET, 0, 0, INSET, 0, 1},
        {FAR, 0, 1, FAR, 0, 0, FAR, 1, 0, FAR, 1, 1}
    };

    @Override
    public void renderSide(ShapeRenderer shape, int x, int y, int z, int face, int texture) {
        float u = texture % 16 * 16, v = texture / 16 * 16;
        int rotation = face == 3 ? 1 : face == 5 ? 2 : 0;
        for (int i = 0; i < 4; i++) {
            int corner = (i + rotation) % 4;
            shape.vertexUV(
                    x + POINTS[face][i * 3],
                    y + POINTS[face][i * 3 + 1],
                    z + POINTS[face][i * 3 + 2],
                    (u + (corner == 0 || corner == 3 ? 0 : 15.99f)) / 256,
                    (v + (corner < 2 ? 0 : 15.99f)) / 256);
        }
    }

    @Override
    public boolean isOpaque() {
        // IMPORTANT: render with transparency (don’t write as opaque)
        return false;
    }

    @Override
    public boolean occludesFace(
            net.classicremastered.minecraft.level.Level level, int x, int y, int z, int face) {
        return false;
    }

    @Override
    public boolean canRenderSide(
            net.classicremastered.minecraft.level.Level level, int x, int y, int z, int face) {
        if (face >= 2) return true;
        return level.getTile(x, y, z) != id && super.canRenderSide(level, x, y, z, face);
    }

    @Override
    public net.classicremastered.minecraft.phys.AABB getCollisionBox(int x, int y, int z) {
        return new net.classicremastered.minecraft.phys.AABB(
                x + 1 / 16f, y, z + 1 / 16f, x + 15 / 16f, y + 15 / 16f, z + 15 / 16f);
    }
}
