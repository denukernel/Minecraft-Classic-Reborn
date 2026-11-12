package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.entity.ThrownSnowball;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class SnowballItem extends Item {
    public SnowballItem(int id) {
        super(id, "Snowball", "/items/snowball.png", 16);
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null || level.isNetworkMode()) return;

        level.addEntity(new ThrownSnowball(level, player));
        consumeSelected(player, level);
    }
}
