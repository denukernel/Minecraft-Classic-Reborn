package net.classicremastered.minecraft.level.tile;

public final class DiamondOreBlock extends OreBlock {
    public DiamondOreBlock(int id) {
        super(id, 187); // 187 = diamond_ore texture index (see TextureManager applyBlockTilesToTerrainAtlas)
        this.setData(Tile$SoundType.stone, 1.0F, 1.0F, 3.0F);
        this.explodes = false;
    }
}
