package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.entity.SignEntity;
import net.classicremastered.minecraft.gui.SignEditScreen;
import net.classicremastered.minecraft.player.Player;

public final class SignItem extends Item {
    SignItem(int id) {
        super(id, "Oak Sign", "/items/oak_sign.png", 16);
    }

    public boolean place(Player player, int x, int y, int z, int face) {
        var level = player.level;
        var game = player.minecraft;
        if (face != 1
                || game == null
                || game.isOnline()
                || level.isNetworkMode()
                || player.inventory.getSelected() != id + 256
                || player.inventory.getCount(player.inventory.selected) <= 0) return false;
        var sign = new SignEntity(level, x, y + 1, z, -player.yRot);
        if (!sign.hasSupport()
                || !level.getCubes(sign.bb).isEmpty()
                || !level.blockMap.getEntities(sign, sign.bb).isEmpty()) return false;

        level.addEntity(sign);
        consumeSelected(player, level);
        game.setCurrentScreen(new SignEditScreen(sign));
        return true;
    }
}
