package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.tile.BuilderDoorBlock;
import net.classicremastered.minecraft.player.Player;

public final class BuilderDoorItem extends Item {
    BuilderDoorItem(int id) {
        super(id, "Oak Door", "/items/oak_door.png", 64);
    }

    public boolean place(Player player, int x, int y, int z, int face) {
        var level = player.level;
        var game = player.minecraft;
        if (face < 0 || face >= 6 || game == null || game.isOnline() || level.isNetworkMode()
                || player.inventory.getSelected() != id + 256
                || player.inventory.getCount(player.inventory.selected) <= 0)
            return false;
        Pos bottom = new Pos(x, y, z).step(face);
        if (!BuilderDoorBlock.install(level, bottom, player))
            return false;
        consumeSelected(player, level);
        return true;
    }
}
