package net.classicremastered.minecraft.level.tile;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;

public final class StillLiquidBlock extends LiquidBlock {

    protected StillLiquidBlock(int var1, LiquidType var2) {
        super(var1, var2);
        this.movingId = var1 - 1;
        this.stillId = var1;
        this.setPhysics(false);
    }

    public final void update(Level level, int x, int y, int z, Random rand) {
    }

    @Override
    public final void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        boolean needsFlow = false;

        // check neighbors safely (don’t use finite width/height)
        if (level.isInBounds(x - 1, y, z) && level.getTile(x - 1, y, z) == 0)
            needsFlow = true;
        if (level.isInBounds(x + 1, y, z) && level.getTile(x + 1, y, z) == 0)
            needsFlow = true;
        if (level.isInBounds(x, y, z - 1) && level.getTile(x, y, z - 1) == 0)
            needsFlow = true;
        if (level.isInBounds(x, y, z + 1) && level.getTile(x, y, z + 1) == 0)
            needsFlow = true;
        if (level.isInBounds(x, y - 1, z) && level.getTile(x, y - 1, z) == 0)
            needsFlow = true;

        // water vs lava → stone
        if (changedId != 0) {
            LiquidType other = Block.blocks[changedId].getLiquidType();
            if ((this.type == LiquidType.WATER && other == LiquidType.LAVA)
                    || (this.type == LiquidType.LAVA && other == LiquidType.WATER)) {
                level.setTile(x, y, z, Block.STONE.id);
                return;
            }
        }

        // turn into flowing liquid if needed
        if (needsFlow) {
            level.setTileNoUpdate(x, y, z, this.movingId);
            level.addToTickNextTick(x, y, z, this.movingId);
        }
    }

}
