package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class GlassLookerItem extends Item {
    public GlassLookerItem(int id) {
        super(id, "Glass Looker", "/items/glass_looker.png", 64);
    }

    @Override
    public void use(Player player, Level level) {
        // Handled in FOV setup inside Renderer
    }
}
