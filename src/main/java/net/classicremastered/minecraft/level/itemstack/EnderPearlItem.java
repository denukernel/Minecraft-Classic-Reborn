package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.entity.ThrownEnderPearl;

public class EnderPearlItem extends Item {
    public EnderPearlItem(int id) {
        super(id, "Ender Pearl", "/items/ender_pearl.png", 16);
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null) return;

        // Start at the camera so a throw cannot spawn across a nearby wall.
        if (!level.isNetworkMode()) {
            level.addEntity(new ThrownEnderPearl(level, player, 1.5f));
            float pitch = .4f / (.8f + level.random.nextFloat() * .4f);
            level.playSound("item/ender_pearl/throw", player, .5f, pitch);
        }

        // Reduce item stack
        consumeSelected(player, level);
    }
}
