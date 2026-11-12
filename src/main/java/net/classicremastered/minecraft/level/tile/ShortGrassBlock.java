package net.classicremastered.minecraft.level.tile;

import java.util.Random;
import net.classicremastered.minecraft.level.Level;

final class ShortGrassBlock extends FlowerBlock {
    ShortGrassBlock(int id, int texture) {
        super(id, texture, "Short Grass");
        setBounds(.15f, 0, .15f, .85f, .75f, .85f);
        setData(Tile$SoundType.grass, .7f, 1, 0);
    }

    @Override
    public int getDropCount() {
        return 0;
    }

    @Override
    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        return level.getTile(x, y, z) == 0 && supported(level, x, y, z);
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (!supported(level, x, y, z)) level.setTile(x, y, z, 0);
    }

    @Override
    public void update(Level level, int x, int y, int z, Random random) {
        if (!supported(level, x, y, z)) level.setTile(x, y, z, 0);
    }

    private static boolean supported(Level level, int x, int y, int z) {
        int below = level.getTile(x, y - 1, z);
        return below == GRASS.id || below == DIRT.id;
    }
}
