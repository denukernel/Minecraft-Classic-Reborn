package net.classicremastered.minecraft.level.tile;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;

public final class SaplingBlock extends FlowerBlock {

    protected SaplingBlock(int var1, int var2) {
        super(6, 15);
        float var3 = 0.4F;
        this.setBounds(0.5F - var3, 0.0F, 0.5F - var3, var3 + 0.5F, var3 * 2.0F, var3 + 0.5F);
    }

    @Override
    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        return level.getTile(x, y, z) == 0 && supports(level.getTile(x, y - 1, z));
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (level.getTile(x, y, z) == id
                && !supports(level.getTile(x, y - 1, z))
                && level.setTile(x, y, z, 0)) {
            dropItems(level, x, y, z, 1f);
        }
    }

    public final void update(Level level, int x, int y, int z, Random rand) {
        int var6 = level.getTile(x, y - 1, z);
        if (!supports(var6)) {
            onNeighborChange(level, x, y, z, var6);
            return;
        }
        if (level.isLit(x, y, z)) {
            if (rand.nextInt(5) == 0) {
                level.setTileNoUpdate(x, y, z, 0);
                if (!level.maybeGrowTree(x, y, z)) {
                    level.setTileNoUpdate(x, y, z, this.id);
                }
            }

        } else {
            level.setTile(x, y, z, 0);
        }
    }
}
