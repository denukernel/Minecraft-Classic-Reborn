package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.render.ShapeRenderer;

/**
 * Temporary, climbable work scaffold. The builder removes its own blocks after
 * placement.
 */
public final class BuilderScaffoldBlock extends LadderBlock {
    BuilderScaffoldBlock(int id) {
        super(id, 5);
        textureId = 60;
        setBounds(0, 0, 0, 1, 1, 1);
        setData(Tile$SoundType.wood, 1, 1, .4f);
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
    }

    @Override
    public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        float light = level.getRenderBrightness(x, y, z);
        renderer.color(light, light, light);
        for (int side = 0; side < 6; side++)
            renderSide(renderer, x, y, z, side);
        return true;
    }

    @Override
    public int getDropCount() {
        return 0;
    }
}
