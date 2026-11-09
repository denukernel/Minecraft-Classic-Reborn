// File: src/com/mojang/minecraft/level/itemstack/ItemRegistry.java
package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.mob.MobRegistry;

public final class ItemRegistry {

    private static boolean bootstrapped = false;

    private ItemRegistry() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        MobRegistry.bootstrapDefaults();
        bootstrapped = true;

        // --- Core items ---
        Item.EMPTY = new Item(0, "Empty", null, 64);
        Item.APPLE = new AppleItem(1);
        Item.SWORD = new SwordItem(2);
        Item.MINING_AXE = new MiningAxeItem(3);
        Item.SHOVEL = new ShovelItem(4);

        Item.BEETROOT = new BeetrootItem(5);
        Item.BOW = new BowItem(6);
        Item.FEATHER = new FeatherItem(7);

        Item.FLINT_AND_STEEL = new FlintAndSteelItem(9);
        Item.items[9] = Item.FLINT_AND_STEEL;
        Item.PHYSICS_GUN = new PhysicsGunItem(10);
        Item.items[11] = new GravityGunItem(11);
        Item.GRAVITYGUN = (GravityGunItem) Item.items[11];

        Item.BUCKET_EMPTY = new BucketItem(12, 0);
        Item.BUCKET_WATER = new BucketItem(13, 1);
        Item.BUCKET_LAVA = new BucketItem(14, 2);

        Item.BAG_OF_SALT = new BagOfSaltItem(15);
        Item.HEALTH_DRINK = new HealthDrinkItem(16);
        Item.DAMAGE_BOTTLE = new DamageBottleItem(17);
        Item.GRAPPLING_HOOK = new GrapplingHookItem(18);
        Item.GLASS_LOOKER = new GlassLookerItem(19);
        Item.ENDER_PEARL = new EnderPearlItem(20);

        Equipment.register();
        Item.items[Item.LEVER_ICON_ID] =
                new Item(Item.LEVER_ICON_ID, "Lever", "/textures/block/lever.png");
        new Item(Item.REDSTONE_DUST_ID, "Redstone Dust", "/items/redstone.png");
        new Item(Item.COAL_ID, "Coal", "/items/coal.png");
        for (MobWandItem.Power power : MobWandItem.Power.values())
            new MobWandItem(80 + power.ordinal(), power);
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
