package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;

public final class FurnaceBlock extends Block {
    public FurnaceBlock(int id) {
        super(id, 182, "Furnace");
        setData(Tile$SoundType.stone, 1.0F, 1.0F, 3.5F);
    }

    @Override
    public void onPlacedBy(Level level, int x, int y, int z, Player player, int face) {
        level.redstone().state(x, y, z).facing = CircuitBlock.horizontalFacing(player);
        level.onBlockChanged(x, y, z);
    }

    @Override
    protected void renderInside(
            Level level, ShapeRenderer renderer, int x, int y, int z, int face) {
        renderSide(
                renderer, x, y, z, face, textureFor(face, level.redstone().state(x, y, z).facing));
    }

    @Override
    protected int getTextureId(int face) {
        return textureFor(face, 3);
    }

    @Override
    public void renderFullbright(ShapeRenderer renderer) {
        for (int face = 0; face < 6; face++) {
            float shade = face == 1 ? 1 : face == 0 ? .5f : face < 4 ? .8f : .6f;
            renderer.color(shade, shade, shade);
            renderSide(renderer, -2, 0, 0, face, textureFor(face, 2));
        }
    }

    private static int textureFor(int face, int front) {
        return face < 2 ? 181 : face == front ? 182 : 184;
    }
}
