package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;

public class LeavesBaseBlock extends Block {

    protected LeavesBaseBlock(int var1, int var2, boolean var3) {
        this(var1, var2, "Leaves");
    }

    protected LeavesBaseBlock(int id, int texture, String name) {
        super(id, texture, name);
    }

    public final boolean isSolid() {
        return false;
    }

    public final boolean canRenderSide(Level level, int x, int y, int z, int side) {
        Block neighbor = blocks[level.getTile(x, y, z)];
        if (neighbor instanceof LeavesBaseBlock
                && level.minecraft != null
                && level.minecraft.settings != null
                && level.minecraft.settings.fastGraphics()) return false;
        return super.canRenderSide(level, x, y, z, side);
    }

    public final boolean isOpaque() {
        return false;
    }
}
