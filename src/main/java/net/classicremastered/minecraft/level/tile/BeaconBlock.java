package net.classicremastered.minecraft.level.tile;

public final class BeaconBlock extends Block {
    public BeaconBlock(int id) {
        super(id, 185, "Beacon"); // 185 = beacon
        this.setData(Tile$SoundType.stone, 1.0F, 1.0F, 2.0F);
    }

    @Override
    public int getLightValue() {
        return 15; // make it glow
    }
}
