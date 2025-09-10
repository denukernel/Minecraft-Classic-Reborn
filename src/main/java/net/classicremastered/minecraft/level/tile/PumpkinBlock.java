package net.classicremastered.minecraft.level.tile;

public final class PumpkinBlock extends Block {
    private final static int sideTex = 188; // pumpkin_side
    private final int faceTex = 189; // pumpkin_face_off

    public PumpkinBlock(int id) {
        super(id, sideTex, "Pumpkin");
        this.setData(Tile$SoundType.wood, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected int getTextureId(int side) {
        if (side == 3) return faceTex; // front face
        return sideTex;
    }
}
