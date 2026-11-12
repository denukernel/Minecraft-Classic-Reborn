package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

final class RawMeatItem extends Item {
    private final int healing;

    RawMeatItem(int id, String name, String texture, int healing) {
        super(id, name, texture);
        this.healing = healing;
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || player.health <= 0 || player.health >= 20) return;

        player.health = Math.min(20, player.health + healing);
        consumeSelected(player, level);
    }
}
