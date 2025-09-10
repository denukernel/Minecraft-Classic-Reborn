package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

/** Classic-style Flint & Steel: RMB-on-TNT handled by TNTBlock#onRightClick. */
public class FlintAndSteelItem extends ToolItem {

    public FlintAndSteelItem(int id) {
        super(id, "Flint and Steel", "/items/flint_and_steel.png"); // put your PNG here
    }

    @Override
    public void use(Player player, Level level) {
        // No generic right-click-in-air behavior in Classic (yet).
        // If you later want to light fires, handle it here by placing a flame block.
    }
}
