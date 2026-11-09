package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class BeetrootItem extends Item {

    public BeetrootItem(int id) {
        super(id, "Beetroot", "/items/beetroot.png", 16); // stackable up to 16
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null) return;
        

        if (player.health < 20) {
            player.health = Math.min(player.health + 2, 20); // heals 1 heart
            if (player.minecraft != null && player.minecraft.hud != null) {
                //player.minecraft.hud.addChat("&aYou ate a beetroot!");
            }

            consumeSelected(player,level);
        } else if (player.minecraft != null && player.minecraft.hud != null) {
            //player.minecraft.hud.addChat("&7You are already full!");
        }
    }
}
