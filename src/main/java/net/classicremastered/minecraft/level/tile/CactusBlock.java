// File: src/net/classicremastered/minecraft/level/tile/CactusBlock.java
package net.classicremastered.minecraft.level.tile;

public final class CactusBlock extends Block {
    private static final int SIDE_TEX = 179; // cactus_side
    private static final int TOP_TEX  = 180; // cactus_top

    public CactusBlock(int id) {
        super(id, SIDE_TEX, "Cactus");
        // sound, particle gravity, hardness (tweak as you like)
        this.setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.4F);
    }

    @Override
    protected int getTextureId(int side) {
        return (side == 0 || side == 1) ? TOP_TEX : SIDE_TEX;
    }

    @Override
    public boolean isOpaque() {
        // IMPORTANT: render with transparency (don’t write as opaque)
        return false;
    }

    @Override
    public int getRenderPass() {
        // Draw in translucent pass like GLASS/LEAVES to avoid “see-through world”
        return 1;
    }
}
