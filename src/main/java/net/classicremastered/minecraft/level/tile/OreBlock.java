package net.classicremastered.minecraft.level.tile;

public class OreBlock extends Block {

    public OreBlock(int var1, int var2) {
        super(var1, var2, "Ore");
    }

    public final int getDrop() {
        return this == COAL_ORE
                ? net.classicremastered.minecraft.level.itemstack.Item.COAL_ID + 256
                : (this == GOLD_ORE ? GOLD_BLOCK.id : (this == IRON_ORE ? IRON_ORE.id : this.id));
    }

    public final int getDropCount() {
        return this == IRON_ORE ? 1 : random.nextInt(3) + 1;
    }
}
