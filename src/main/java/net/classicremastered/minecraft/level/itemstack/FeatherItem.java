package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.player.Player;

public class FeatherItem extends FoodItem {

    public FeatherItem(int id) {
        super(id, "Feather", "/items/feather.png", 64, 0);
    }

    @Override
    protected boolean canEatAtHealth(Player player) {
        return player.level != null && !player.level.creativeMode;
    }

    @Override
    public boolean playsBurp() {
        return false;
    }

    @Override
    protected void finishEating(Player player) {
        player.hurt(null, 1, "choke");
    }
}
