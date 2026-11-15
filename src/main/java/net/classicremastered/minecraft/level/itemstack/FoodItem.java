package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public abstract class FoodItem extends Item {
    public static final int EAT_TICKS = 32;
    private final int healing;

    protected FoodItem(int id, String name, String texture, int maxStack, int healing) {
        super(id, name, texture, maxStack);
        this.healing = healing;
    }

    public final boolean canEat(Player player) {
        return player != null
                && player.health > 0
                && canEatAtHealth(player)
                && player.inventory.getSelected() == id + 256
                && player.inventory.getCount(player.inventory.selected) > 0;
    }

    protected boolean canEatAtHealth(Player player) {
        return player.health < 20;
    }

    public boolean playsBurp() {
        return true;
    }

    @Override
    public final boolean use(Player player, Level level) {
        if (level == null || !canEat(player)) return false;
        consumeSelected(player, level);
        finishEating(player);
        return true;
    }

    protected void finishEating(Player player) {
        player.health = Math.min(20, player.health + healing);
    }
}
