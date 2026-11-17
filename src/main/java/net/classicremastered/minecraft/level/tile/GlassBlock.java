package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;

public final class GlassBlock extends Block {

    protected GlassBlock(int id) {
        super(id, 49, "Glass");
    }

    public static int retainWater(int previousId, int placedId) {
        return placedId == Block.GLASS.id
                        && (previousId == Block.WATER.id || previousId == Block.STATIONARY_WATER.id)
                ? Block.WATERLOGGED_GLASS.id
                : placedId;
    }

    public final boolean isSolid() {
        return false;
    }

    public final boolean canRenderSide(Level level, int x, int y, int z, int side) {
        return level.getTile(x, y, z) != Block.GLASS.id
                && super.canRenderSide(level, x, y, z, side);
    }

    public final boolean isOpaque() {
        return false;
    }

    @Override
    public int getDrop() {
        return Block.GLASS.id;
    }

    public static void playShatter(Level level, float x, float y, float z) {
        level.playSound("block/glass/break", x, y, z, .9f, 1f);
    }
}
