package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

/** Base class for non-stackable tools (maxStackSize=1). */
public class ToolItem extends Item {

    public ToolItem(int id, String name, String texture) {
        super(id, name, texture, 1);
    }

    @Override
    public void use(Player player, Level level) {
        // Tools don't do a right-click action by default.
        // Attack/mining behavior is handled in click/mining code.
    }
}
