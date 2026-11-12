package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.gui.ShopScreen;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.player.Player;

import java.util.List;

public final class VillagerTrades {
    public record Offer(int raw, int count, int price, String name) {}

    private VillagerTrades() {}

    public static List<Offer> offers(Player player) {
        ItemRegistry.bootstrap();
        if (player.villagerReputation < 0)
            return List.of(new Offer(Item.FEATHER.id + 256, 1, 15, "Feather"));
        return List.of(
                new Offer(Item.APPLE.id + 256, 1, 5, "Apple"),
                new Offer(Equipment.ARROW.id + 256, 5, 5, "Arrows"),
                new Offer(Equipment.SWORDS[0] + 256, 1, 6, "Wood Sword"),
                new Offer(Equipment.ARMOR[0][0] + 256, 1, 7, "Leather Helmet"),
                new Offer(Equipment.ARMOR[0][1] + 256, 1, 12, "Leather Chestplate"));
    }

    public static boolean available(Villager villager, Player player) {
        return player != null
                && villager != null
                && player.level != null
                && player.level == villager.level
                && !player.level.isNetworkMode()
                && !player.isSpectator()
                && player.health > 0
                && !villager.removed
                && villager.health > 0
                && !villager.isHostile()
                && villager.tradeCooldown <= 0
                && villager.distanceToSqr(player) <= 25;
    }

    public static boolean purchase(Villager villager, Player player, int index) {
        if (!available(villager, player)) return false;
        List<Offer> offers = offers(player);
        if (index < 0 || index >= offers.size()) return false;
        Offer offer = offers.get(index);
        if (!ShopScreen.purchase(player, offer.raw, offer.count, offer.price)) return false;
        if (!player.level.creativeMode && player.villagerReputation < 100)
            player.villagerReputation++;
        return true;
    }
}
