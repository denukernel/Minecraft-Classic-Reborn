package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.tile.Block;

public final class OreMaterials {
    public static final int RAW_IRON = 67;
    public static final int RAW_GOLD = 68;
    public static final int DIAMOND = 69;

    private OreMaterials() {}

    static void register() {
        new Item(RAW_IRON, "Iron Ingot", "/items/raw_iron.png");
        new Item(RAW_GOLD, "Gold Ingot", "/items/raw_gold.png");
        new Item(DIAMOND, "Diamond", "/items/diamond.png");
    }

    public static boolean matches(int expected, int actual) {
        return expected == actual
                || expected == RAW_IRON + 256 && actual == Block.IRON_ORE.id
                || expected == RAW_GOLD + 256 && actual == Block.GOLD_ORE.id
                || expected == DIAMOND + 256 && actual == Block.DIAMOND_ORE_NEW.id;
    }
}
