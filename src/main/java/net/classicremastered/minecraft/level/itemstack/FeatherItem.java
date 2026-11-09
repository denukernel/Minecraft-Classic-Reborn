package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class FeatherItem extends Item {

    public FeatherItem(int id) {
        super(id, "Feather", "/items/feather.png", 64);
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null) return;

        // Creative players don’t take damage
        if (level != null && level.creativeMode) return;

        // Hurt player by 1 heart (2 hp)
        player.hurt(null, 1);

        // Optionally reduce item stack
        consumeSelected(player,level);

        // Feedback (if you want chat text):
        // if (player.minecraft != null && player.minecraft.hud != null) {
        //     player.minecraft.hud.addChat("&cThe feather cut you!");
        // }
    }
}
