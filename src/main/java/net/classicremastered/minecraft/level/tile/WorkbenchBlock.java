package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.gui.GuiCrafting;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class WorkbenchBlock extends Block {

    public WorkbenchBlock(int id, int tex) {
        super(id, tex, "Workbench");
        this.setData(Tile$SoundType.wood, 1.0F, 1.0F, 2.0F);
    }

    @Override
    public boolean onRightClick(Level level, int x, int y, int z, Player player, int face) {
        if (player.minecraft != null) {
            GuiCrafting gui = new GuiCrafting();
            player.minecraft.setCurrentScreen(gui);
            return true;
        }
        return false;
    }

    @Override
    protected int getTextureId(int side) {
        if (side == 0 || side == 1) return 4;
        return 35;
    }

    @Override
    public int getDrop() {
        return this.id;
    }

    @Override
    public int getDropCount() {
        return 1;
    }
}
