package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.entity.ThrownEgg;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class EggItem extends Item {
    public EggItem(int id) {
        super(id, "Egg", "/items/egg.png", 16);
    }

    @Override
    public boolean use(Player player, Level level) {
        if (player == null
                || level == null
                || player.level != level
                || level.isNetworkMode()
                || player.isSpectator()
                || player.removed
                || player.health <= 0
                || player.inventory.getSelected() != id + 256
                || player.inventory.getCount(player.inventory.selected) <= 0) return false;

        level.addEntity(new ThrownEgg(level, player));
        level.playSound("item/ender_pearl/throw", player, .5f, .8f);
        consumeSelected(player, level);
        return true;
    }
}
