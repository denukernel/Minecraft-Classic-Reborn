package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.ChestController;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;

import java.util.List;

public final class PyramidBlock extends Block {
    public enum Shape {
        CUBE,
        SLAB,
        STAIRS,
        CHEST,
        ORE
    }

    public final Shape shape;

    public PyramidBlock(int id, String name, int texture, Shape shape) {
        super(id, texture, name);
        this.shape = shape;
        setData(shape == Shape.CHEST ? Tile$SoundType.wood : Tile$SoundType.stone, 1, 1, 1.5f);
        if (shape == Shape.SLAB) setBounds(0, 0, 0, 1, .5f, 1);
        if (shape == Shape.CHEST) setBounds(.0625f, 0, .0625f, .9375f, .875f, .9375f);
    }

    @Override
    public boolean isSolid() {
        return shape == Shape.CUBE || shape == Shape.ORE;
    }

    @Override
    public boolean isOpaque() {
        return isSolid();
    }

    @Override
    public boolean isCube() {
        return isSolid();
    }

    @Override
    public int getRenderPass() {
        // Animated chests are drawn each frame instead of baked into chunk display lists.
        return shape == Shape.CHEST ? -1 : super.getRenderPass();
    }

    @Override
    protected int getTextureId(int side) {
        if (id == 80 || id == 81 || id == 82) return side == 1 ? 129 : side == 0 ? 130 : textureId;
        if (shape == Shape.CHEST) return side == 1 || side == 0 ? 154 : side == 3 ? 153 : 152;
        return textureId;
    }

    @Override
    public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        if (shape == Shape.CUBE || shape == Shape.ORE)
            return super.render(level, x, y, z, renderer);
        float b = getBrightness(level, x, y + 1, z);
        draw(
                renderer,
                x,
                y,
                z,
                shape == Shape.STAIRS || shape == Shape.CHEST
                        ? level.redstone().state(x, y, z).facing
                        : 3,
                b);
        return true;
    }

    private void draw(ShapeRenderer renderer, int x, int y, int z, int facing, float b) {
        if (shape == Shape.CHEST) {
            BlockModels.chest(renderer, x, y, z, facing, b);
            return;
        }
        BlockShape.box(
                renderer,
                x,
                y,
                z,
                x1,
                y1,
                z1,
                x2,
                shape == Shape.STAIRS ? .5f : y2,
                z2,
                getTextureId(1),
                getTextureId(0),
                textureId,
                b,
                b,
                b);
        if (shape == Shape.STAIRS) {
            float sx = facing == 5 ? .5f : 0, ex = facing == 4 ? .5f : 1;
            float sz = facing == 3 ? .5f : 0, ez = facing == 2 ? .5f : 1;
            BlockShape.box(renderer, x, y, z, sx, .5f, sz, ex, 1, ez, 129, 130, 128, b, b, b);
        }
    }

    @Override
    public void renderFullbright(ShapeRenderer renderer) {
        draw(renderer, -2, 0, 0, shape == Shape.CHEST ? 2 : 3, 1);
    }

    @Override
    public void renderPreview(ShapeRenderer renderer) {
        renderer.begin();
        draw(renderer, 0, 0, 0, 3, 1);
        renderer.end();
    }

    @Override
    public void addCollisionBoxes(Level level, int x, int y, int z, AABB query, List<AABB> out) {
        if (shape == Shape.CHEST) {
            AABB bounds = ChestController.bounds(level, new Pos(x, y, z));
            if (query.intersectsInner(bounds)) out.add(bounds);
            return;
        }
        if (shape != Shape.STAIRS) {
            super.addCollisionBoxes(level, x, y, z, query, out);
            return;
        }
        AABB lower = new AABB(x, y, z, x + 1, y + .5f, z + 1);
        if (query.intersectsInner(lower)) out.add(lower);
        int facing = level.redstone().state(x, y, z).facing;
        AABB upper =
                new AABB(
                        x + (facing == 5 ? .5f : 0),
                        y + .5f,
                        z + (facing == 3 ? .5f : 0),
                        x + (facing == 4 ? .5f : 1),
                        y + 1,
                        z + (facing == 2 ? .5f : 1));
        if (query.intersectsInner(upper)) out.add(upper);
    }

    @Override
    public void onPlacedBy(Level level, int x, int y, int z, Player player, int face) {
        if (shape == Shape.STAIRS)
            level.redstone().state(x, y, z).facing = CircuitBlock.horizontalFacing(player);
        if (shape == Shape.CHEST) {
            level.redstone().state(x, y, z).facing = CircuitBlock.horizontalFacing(player);
            ChestController.join(level, new Pos(x, y, z));
        }
        level.onBlockChanged(x, y, z);
    }

    @Override
    public boolean onRightClick(Level level, int x, int y, int z, Player player, int face) {
        if (shape != Shape.CHEST || player.minecraft == null) return false;
        if (!ChestController.canOpen(level, ChestController.view(level, new Pos(x, y, z))))
            return true;
        player.minecraft.setCurrentScreen(
                new net.classicremastered.minecraft.gui.ChestScreen(x, y, z));
        return true;
    }

    @Override
    public int getDrop() {
        return shape == Shape.ORE
                ? net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256
                : id;
    }

    @Override
    public int getDropCount() {
        return shape == Shape.ORE ? 4 : 1;
    }
}
