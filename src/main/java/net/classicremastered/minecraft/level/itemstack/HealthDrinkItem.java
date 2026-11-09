package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class HealthDrinkItem extends Item {
    public HealthDrinkItem(int id) {
        super(id, "Health Drink", "/items/health_drink.png", 64);
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null) return;

        // Set healing timer to 5 seconds (100 ticks)
        player.healthDrinkTimer = 100;

        if (level != null) {
            level.playSound("random/pop", player, 1.0f, 1.2f);
        }

        // Reduce item stack
        consumeSelected(player,level);
    }
}
