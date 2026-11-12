package net.classicremastered.minecraft.level.tile;

import java.util.List;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;

/** Straight stairs. Modern placements store their whole shape in the block id. */
public final class StairBlock extends Block {
    public static final int FIRST = 160;
    private static final int LEGACY = 85;
    private final int material;
    private final int facing;
    private final boolean upsideDown;
    private final Block surface;

    StairBlock(int id, int material, int facing, boolean upsideDown, Block surface) {
        super(id, surface.textureId, new String[] { "Sandstone Stairs", "Oak Stairs",
                "Cobblestone Stairs", "Stone Brick Stairs" }[material]);
        this.material = material;
        this.facing = facing;
        this.upsideDown = upsideDown;
        this.surface = surface;
        setData(material == 1 ? Tile$SoundType.wood : Tile$SoundType.stone, 1f, 1f, 1.5f);
    }

    static Block[] register(Block sandstone, Block oak, Block cobble, Block bricks) {
        Block[] surfaces = { sandstone, oak, cobble, bricks };
        Block[] items = new Block[4];
        for (int material = 0; material < surfaces.length; material++) {
            for (int half = 0; half < 2; half++) {
                for (int face = 2; face <= 5; face++) {
                    int id = id(material, face, half != 0);
                    Block stair = new StairBlock(id, material, face, half != 0, surfaces[material]);
                    if (half == 0 && face == 2)
                        items[material] = stair;
                }
            }
        }
        items[0] = new StairBlock(LEGACY, 0, 3, false, sandstone);
        return items;
    }

    public static int id(int material, int facing, boolean upsideDown) {
        return FIRST + material * 8 + (upsideDown ? 4 : 0) + facing - 2;
    }

    public int placementId(Player player, int face, float hitHeight) {
        int direction = player == null ? 3 : CircuitBlock.horizontalFacing(player);
        return id(material, direction, face == 0 || face >= 2 && hitHeight > .5f);
    }

    public boolean isInventoryVariant() {
        return id == getDrop();
    }

    private AABB[] shape(Level level, int x, int y, int z) {
        int direction = id == LEGACY && level != null ? level.redstone().state(x, y, z).facing : facing;
        float split = y + .5f;
        return new AABB[] {
                new AABB(x, upsideDown ? split : y, z, x + 1, upsideDown ? y + 1 : split, z + 1),
                new AABB(x + (direction == 5 ? .5f : 0), upsideDown ? y : split,
                        z + (direction == 3 ? .5f : 0), x + (direction == 4 ? .5f : 1),
                        upsideDown ? split : y + 1, z + (direction == 2 ? .5f : 1)) };
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
    public int getDrop() {
        return material == 0 ? LEGACY : id(material, 2, false);
    }

    @Override
    public void addCollisionBoxes(Level level, int x, int y, int z, AABB query, List<AABB> out) {
        for (AABB box : shape(level, x, y, z)) {
            if (query.intersectsInner(box))
                out.add(box);
        }
    }

    @Override
    public MovingObjectPosition clip(Level level, int x, int y, int z, Vec3D start, Vec3D end) {
        MovingObjectPosition best = null;
        for (AABB box : shape(level, x, y, z)) {
            MovingObjectPosition hit = box.clip(start, end);
            if (hit != null && (best == null
                    || start.distanceSquared(hit.vec) < start.distanceSquared(best.vec))) {
                best = new MovingObjectPosition(x, y, z, hit.face, hit.vec);
            }
        }
        return best;
    }

    private void draw(Level level, int x, int y, int z, ShapeRenderer renderer, float light) {
        for (AABB box : shape(level, x, y, z)) {
            BlockShape.box(renderer, x, y, z, box.x0 - x, box.y0 - y, box.z0 - z, box.x1 - x,
                    box.y1 - y, box.z1 - z, surface.getTextureId(1), surface.getTextureId(0),
                    surface.getTextureId(2), light, light, light);
        }
    }

    @Override
    public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        draw(level, x, y, z, renderer, getBrightness(level, x, y + 1, z));
        return true;
    }

    @Override
    public void renderFullbright(ShapeRenderer renderer) {
        draw(null, -2, 0, 0, renderer, 1);
    }

    @Override
    public void renderPreview(ShapeRenderer renderer) {
        renderer.begin();
        draw(null, 0, 0, 0, renderer, 1);
        renderer.end();
    }
}
