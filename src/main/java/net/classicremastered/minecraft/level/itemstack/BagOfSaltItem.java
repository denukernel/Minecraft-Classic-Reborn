package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class BagOfSaltItem extends Item {
    public BagOfSaltItem(int id) {
        super(id, "Bag of Salt", "/items/bag_of_salt.png", 64);
    }

    @Override
    public void use(Player player, Level level) {
        // Does nothing
    }
}
