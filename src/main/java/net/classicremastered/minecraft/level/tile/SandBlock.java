package net.classicremastered.minecraft.level.tile;

import java.util.Random;
import net.classicremastered.minecraft.entity.FallingBlockEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;

public final class SandBlock extends Block {
    public SandBlock(int id, int texture) {
        super(id, texture, id == 13 ? "Gravel" : "Sand");
    }

    @Override
    public int getTickDelay() {
        return 2;
    }

    @Override
    public void onAdded(Level level, int x, int y, int z) {
        scheduleFall(level, x, y, z);
    }

    @Override
    public void onPlace(Level level, int x, int y, int z) {
        scheduleFall(level, x, y, z);
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        scheduleFall(level, x, y, z);
    }

    private void scheduleFall(Level level, int x, int y, int z) {
        if (y > 0 && canFallThrough(level, x, y - 1, z)) {
            level.addToTickNextTick(x, y, z, id);
        }
    }

    @Override
    public void update(Level level, int x, int y, int z, Random random) {
        if (!level.isNetworkMode()
                && y > 0
                && level.getTile(x, y, z) == id
                && canFallThrough(level, x, y - 1, z)
                && level.setTile(x, y, z, 0)
                && level.getTile(x, y, z) != id) {
            level.addEntity(new FallingBlockEntity(level, x + .5f, y + .5f, z + .5f, id));
        }
    }

    public static boolean canFallThrough(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y, z)) return false;
        int id = level.getTile(x, y, z);
        return id == 0 || id == FIRE.id || blocks[id].getLiquidType() != LiquidType.NOT_LIQUID;
    }
}
