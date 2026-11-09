package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class AppleItem extends Item {

    public AppleItem(int id) {
        super(id, "Apple", "/items/apple.png", 16); // stackable up to 16
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null) return;
        

        if (player.health < 20) {
            player.health = Math.min(player.health + 4, 20);
            if (player.minecraft != null && player.minecraft.hud != null) {
                //player.minecraft.hud.addChat("&aYou ate an apple!");
            }

            consumeSelected(player,level);
        } else if (player.minecraft != null && player.minecraft.hud != null) {
            //player.minecraft.hud.addChat("&7You are already full!");
        }
    }
}
