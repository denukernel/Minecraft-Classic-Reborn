package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.phys.AABB;

public final class SnowBlock extends Block {
    public enum Kind {
        GRASS,
        LAYER,
        FULL
    }

    static final int LAYER_ID = 94;
    private static final int SNOW_TEXTURE = 23;
    private final Kind kind;

    SnowBlock(int id, String name, Kind kind) {
        super(id, SNOW_TEXTURE, name);
        this.kind = kind;
        if (kind == Kind.LAYER) {
            setBounds(0, 0, 0, 1, .125f, 1);
        }
        setData(Tile$SoundType.grass, 1, 1, kind == Kind.LAYER ? 0 : .6f);
    }

    @Override
    protected int getTextureId(int side) {
        if (kind != Kind.GRASS || side == 1) return SNOW_TEXTURE;
        return side == 0 ? 2 : 25;
    }

    @Override
    public boolean isCube() {
        return id != LAYER_ID;
    }

    @Override
    public boolean isSolid() {
        return id != LAYER_ID;
    }

    @Override
    public boolean isOpaque() {
        return id != LAYER_ID;
    }

    @Override
    public AABB getCollisionBox(int x, int y, int z) {
        return kind == Kind.LAYER ? null : super.getCollisionBox(x, y, z);
    }

    @Override
    public boolean canRenderSide(Level level, int x, int y, int z, int side) {
        return kind == Kind.LAYER && side == 1 || super.canRenderSide(level, x, y, z, side);
    }

    @Override
    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        return kind != Kind.LAYER || level.getTile(x, y, z) == 0 && supported(level, x, y, z);
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (kind == Kind.LAYER && !supported(level, x, y, z)) level.setTile(x, y, z, 0);
    }

    private static boolean supported(Level level, int x, int y, int z) {
        Block below = blocks[level.getTile(x, y - 1, z)];
        return below != null && (below.isSolid() || below instanceof LeavesBaseBlock);
    }

    @Override
    public int getDrop() {
        return kind == Kind.GRASS ? DIRT.id : Item.SNOWBALL.id + 256;
    }

    @Override
    public int getDropCount() {
        return kind == Kind.FULL ? 4 : 1;
    }
}
