package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.entity.ThrownDamageBottle;

public class DamageBottleItem extends Item {
    public DamageBottleItem(int id) {
        super(id, "Damage Bottle", "/items/damage_bottle.png", 64);
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null) return;

        // Spawn thrown damage bottle
        if (!level.isNetworkMode()) {
            level.addEntity(new ThrownDamageBottle(level, player, player.x, player.y + 0.12f, player.z, player.yRot, player.xRot, 1.2f));
            level.playSound("random/pop", player, 0.5f, 0.8f);
        }

        // Reduce item stack
        consumeSelected(player,level);
    }
}
