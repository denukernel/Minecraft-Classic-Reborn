package net.classicremastered.minecraft.level.tile;

public final class DiamondBlock extends Block {
    public DiamondBlock(int id) {
        super(id, 186, "Diamond Block"); // 186 = diamond_block
        this.setData(Tile$SoundType.metal, 1.0F, 1.0F, 5.0F);
        this.explodes = false;
    }
}
