// File: src/com/mojang/minecraft/level/itemstack/ItemRegistry.java
package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.mob.MobRegistry;

public final class ItemRegistry {

    private static boolean bootstrapped = false;

    private ItemRegistry() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;

        // --- Core items ---
        Item.EMPTY      = new Item(0, "Empty", null, 64);
        Item.APPLE      = new AppleItem(1);
        Item.SWORD      = new SwordItem(2);
        Item.MINING_AXE = new MiningAxeItem(3);
        Item.SHOVEL     = new ShovelItem(4);

        Item.BEETROOT   = new BeetrootItem(5);
        Item.BOW        = new BowItem(6);
        Item.FEATHER    = new FeatherItem(7);

        Item.FLINT_AND_STEEL = new FlintAndSteelItem(9);
        Item.items[9]  = Item.FLINT_AND_STEEL;
        Item.items[10] = new TelekinesisItem(10);

        // --- Spawn eggs ---
        int eggBaseId = 100; // free range, safe above your tools/items
        for (String name : MobRegistry.allNames()) {
            short mobId = MobRegistry.idOfName(name);
            if (mobId < 0) continue; // invalid entry

            // Create spawn egg item
            ItemSpawnEgg egg = new ItemSpawnEgg(eggBaseId + mobId, mobId, name);
            Item.items[egg.id] = egg;
        }
    }
}
