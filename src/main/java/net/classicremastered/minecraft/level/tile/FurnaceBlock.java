package net.classicremastered.minecraft.level.tile;

public final class FurnaceBlock extends Block {
    private final int offTex = 182; // furnace_front_off
    private final int onTex  = 183; // furnace_front_on
    private final int sideTex = 184; // furnace_side
    private final int topTex  = 181; // furnace_top
    private boolean lit = false;

    public FurnaceBlock(int id) {
        super(id, 182, "Furnace");
        this.setData(Tile$SoundType.stone, 1.0F, 1.0F, 3.5F);
    }

    @Override
    protected int getTextureId(int side) {
        if (side == 1) return topTex;   // top
        if (side == 0) return topTex;   // bottom (same as top for now)
        if (side == 3) return lit ? onTex : offTex; // front
        return sideTex; // all other sides
    }
}
