package net.classicremastered.minecraft.level.tile;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;

public final class StillLiquidBlock extends LiquidBlock {

    protected StillLiquidBlock(int var1, LiquidType var2) {
        super(var1, var2);
        this.movingId = var1 - 1;
        this.stillId = var1;
        this.setPhysics(var2 == LiquidType.LAVA);
    }

    @Override
    public void update(Level level, int x, int y, int z, Random random) {
        if (level.isNetworkMode()) {
            return;
        }
        if (this.type == LiquidType.WATER && usesLevelledFlow(level)) {
            updateWater(level, x, y, z);
        } else if (this.type == LiquidType.LAVA) {
            tryIgniteNearLava(level, x, y, z, random);
        }
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (level.isNetworkMode() || reactWithOtherLiquid(level, x, y, z, changedId)) {
            return;
        }

        // A block above the surface cannot open a path for flow.
        if (canFlowInto(level, x, y - 1, z) || canFlowInto(level, x - 1, y, z) || canFlowInto(level, x + 1, y, z)
                || canFlowInto(level, x, y, z - 1) || canFlowInto(level, x, y, z + 1)) {
            if (this.type == LiquidType.WATER && usesLevelledFlow(level)) {
                level.addToTickNextTick(x, y, z, this.id);
            } else if (level.setTileNoUpdate(x, y, z, this.movingId)) {
                level.addToTickNextTick(x, y, z, this.movingId);
            }
        }
    }
}
