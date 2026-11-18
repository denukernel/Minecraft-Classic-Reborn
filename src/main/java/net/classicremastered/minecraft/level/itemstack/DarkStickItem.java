package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.dimensions.StickmansHouse;
import net.classicremastered.minecraft.player.Player;

public final class DarkStickItem extends Item {
    public static final int ID = 242;

    DarkStickItem() {
        super(ID, "Dark Stick", "/items/dark_stick.png", 1);
    }

    @Override
    public boolean use(Player player, Level level) {
        if (player == null
                || level == null
                || player.level != level
                || player.minecraft == null
                || player.minecraft.level != level
                || player.health <= 0
                || player.removed
                || player.isSpectator()
                || player.inventory.getSelected() != ID + 256
                || player.inventory.getCount(player.inventory.selected) <= 0) return false;
        if (level.isNetworkMode() || player.minecraft.isOnline() || player.minecraft.online) {
            player.minecraft.hud.addChat("&cThe Dark Stick works in local worlds.");
            return false;
        }
        Level destination = StickmansHouse.travel(level, player);
        player.minecraft.setLevel(destination);
        player.minecraft.hud.addChat("&7Entered &f" + destination.name);
        return true;
    }
}
