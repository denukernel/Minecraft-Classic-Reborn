package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.gui.ShopScreen;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.player.Player;

import java.util.List;

public final class VillagerTrades {
    public record Offer(int raw, int count, int price, String name)
            implements java.io.Serializable {}

    private VillagerTrades() {}

    private static List<Offer> pool() {
        ItemRegistry.bootstrap();
        return List.of(
                new Offer(Item.APPLE.id + 256, 1, 5, "Apple"),
                new Offer(Equipment.ARROW.id + 256, 5, 5, "Arrows"),
                new Offer(Equipment.SWORDS[0] + 256, 1, 6, "Wood Sword"),
                new Offer(Equipment.ARMOR[0][0] + 256, 1, 7, "Leather Helmet"),
                new Offer(Equipment.ARMOR[0][1] + 256, 1, 12, "Leather Chestplate"));
    }

    public static List<Offer> randomOffers(java.util.Random random) {
        java.util.ArrayList<Offer> offers = new java.util.ArrayList<>(pool());
        java.util.Collections.shuffle(offers, random);
        return List.copyOf(offers.subList(0, 3));
    }

    public static List<Offer> offers(Villager villager, Player player) {
        ItemRegistry.bootstrap();
        if (player.villagerReputation < 0)
            return List.of(new Offer(Item.FEATHER.id + 256, 1, 15, "Feather"));
        return villager.tradeOffers();
    }

    public static void save(Villager villager, net.classicremastered.nbt.CompoundTag tag) {
        var trades = new net.classicremastered.nbt.ListTag("VillagerTrades", (byte) 10);
        for (Offer offer : villager.tradeOffers()) {
            var trade = new net.classicremastered.nbt.CompoundTag(null);
            trade.putInt("Item", offer.raw());
            trade.putInt("Count", offer.count());
            trade.putInt("Price", offer.price());
            trade.putString("Name", offer.name());
            trades.add(trade);
        }
        tag.put(trades);
    }

    public static List<Offer> load(net.classicremastered.nbt.CompoundTag tag) {
        if (!tag.hasKey("VillagerTrades")) return null;
        var trades = tag.getList("VillagerTrades");
        if (trades.size() != 3) return null;
        java.util.ArrayList<Offer> result = new java.util.ArrayList<>();
        for (int i = 0; i < trades.size(); i++) {
            if (!(trades.get(i) instanceof net.classicremastered.nbt.CompoundTag trade))
                return null;
            Offer offer =
                    new Offer(
                            trade.getInt("Item"),
                            trade.getInt("Count"),
                            trade.getInt("Price"),
                            trade.getString("Name"));
            if (!pool().contains(offer) || result.contains(offer)) return null;
            result.add(offer);
        }
        return List.copyOf(result);
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
        List<Offer> offers = offers(villager, player);
        if (index < 0 || index >= offers.size()) return false;
        Offer offer = offers.get(index);
        if (!ShopScreen.purchase(player, offer.raw, offer.count, offer.price)) return false;
        if (!player.level.creativeMode && player.villagerReputation < 100)
            player.villagerReputation++;
        return true;
    }
}
