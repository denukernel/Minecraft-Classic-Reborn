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
            level.addEntity(
                    new ThrownEnderPearl(level, player, player.x, player.y, player.z, player.yRot, player.xRot, 1.5f));
            level.playSound("random/click", player, 0.8f, 1.2f);
        }

        // Reduce item stack
        consumeSelected(player, level);
    }
}
