package net.classicremastered.minecraft.level.tile;

public class OreBlock extends Block {

    public OreBlock(int var1, int var2) {
        super(var1, var2, "Ore");
    }

    public final int getDrop() {
        return this == COAL_ORE
                ? net.classicremastered.minecraft.level.itemstack.Item.COAL_ID + 256
                : this == GOLD_ORE
                        ? net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_GOLD
                                + 256
                        : this == IRON_ORE
                                ? net.classicremastered.minecraft.level.itemstack.OreMaterials
                                                .RAW_IRON
                                        + 256
                                : this == DIAMOND_ORE_NEW
                                        ? net.classicremastered.minecraft.level.itemstack
                                                        .OreMaterials.DIAMOND
                                                + 256
                                        : this.id;
    }

    public final int getDropCount() {
        return this == COAL_ORE ? random.nextInt(3) + 1 : 1;
    }
}
