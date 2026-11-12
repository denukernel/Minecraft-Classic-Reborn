package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;

/** Wall-mounted ladder. Each face has a block id so existing chunk formats store orientation. */
public class LadderBlock extends Block {
    private final int face;

    public LadderBlock(int id, int face) {
        super(id, 213, "Ladder");
        this.face = face;
        float thickness = 3f / 16;
        switch (face) {
            case 2 -> setBounds(0, 0, 1 - thickness, 1, 1, 1);
            case 3 -> setBounds(0, 0, 0, 1, 1, thickness);
            case 4 -> setBounds(1 - thickness, 0, 0, 1, 1, 1);
            case 5 -> setBounds(0, 0, 0, thickness, 1, 1);
            default -> throw new IllegalArgumentException("Invalid ladder face");
        }
        setData(Tile$SoundType.wood, 1, 1, .4f);
    }

    public static boolean isLadder(int id) {
        return id >= 0 && id < blocks.length && blocks[id] instanceof LadderBlock;
    }

    public static Block forFace(int face) {
        return switch (face) {
            case 2 -> LADDER_NORTH;
            case 3 -> LADDER_SOUTH;
            case 4 -> LADDER_WEST;
            case 5 -> LADDER;
            default -> throw new IllegalArgumentException("Invalid ladder face");
        };
    }

    @Override
    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        return face >= 2
                && face <= 5
                && level.getTile(x, y, z) == 0
                && hasSupport(level, x, y, z, face);
    }

    private static boolean hasSupport(Level level, int x, int y, int z, int face) {
        Pos support = new Pos(x, y, z).step(face ^ 1);
        if (!level.isInBounds(support.x(), support.y(), support.z())) return false;
        Block block = blocks[level.getTile(support.x(), support.y(), support.z())];
        return block != null
                && block.occludesFace(level, support.x(), support.y(), support.z(), face);
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (level.getTile(x, y, z) == id
                && !hasSupport(level, x, y, z, face)
                && level.setTile(x, y, z, 0)) {
            onBreak(level, x, y, z);
        }
    }

    @Override
    public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        float light = level.getRenderBrightness(x, y, z);
        renderer.color(light, light, light);
        renderSide(renderer, x, y, z, face);
        return true;
    }

    @Override
    public int getDrop() {
        return LADDER.id;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public AABB getCollisionBox(int x, int y, int z) {
        return null;
    }
}
