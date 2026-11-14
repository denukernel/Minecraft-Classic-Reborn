package net.classicremastered.minecraft.level.tile;

public final class MagmaBlock extends Block {
    public MagmaBlock(int id, int texture) {
        super(id, texture, "Magma Block");
        setData(Tile$SoundType.stone, 1f, 1f, .5f);
    }

    @Override
    public int getLightValue() {
        return 3;
    }
}
