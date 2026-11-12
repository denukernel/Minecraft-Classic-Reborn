package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.gui.GuiCrafting;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class WorkbenchBlock extends Block {

    private static final int TOP_TEXTURE = 53;
    private static final int FRONT_TEXTURE = 54;
    private static final int SIDE_TEXTURE = 55;

    public WorkbenchBlock(int id) {
        super(id, FRONT_TEXTURE, "Workbench");
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
        return switch (side) {
            case 0 -> 4;
            case 1 -> TOP_TEXTURE;
            case 2, 4 -> FRONT_TEXTURE;
            default -> SIDE_TEXTURE;
        };
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
