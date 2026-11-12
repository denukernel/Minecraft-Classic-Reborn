package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.mob.Cow;
import net.classicremastered.minecraft.player.Player;

public final class SaddleItem extends Item {
    public static final int ID = 209;

    SaddleItem() {
        super(ID, "Saddle", "/items/saddle.png", 1);
    }

    public static boolean equip(Player player, Cow cow) {
        if (cow.level != player.level || player.level.isNetworkMode() || cow.removed || cow.health <= 0
                || cow.isSaddled() || player.inventory.getSelected() != ID + 256
                || player.inventory.getCount(player.inventory.selected) <= 0)
            return false;
        if (!player.level.creativeMode)
            player.inventory.removeSelected(1);
        cow.saddle();
        player.level.playSound("step/cloth", cow, .7f, .85f);
        return true;
    }
}
