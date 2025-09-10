package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;

public final class WoodBlock extends Block {

    protected WoodBlock(int var1) {
        super(17, "Wood");
        this.textureId = 20;
    }

    @Override
    public void onRemoved(Level level, int x, int y, int z) {
        final int R = 4; // match LeavesBlock.LOG_SCAN_RADIUS
        for (int dx = -R; dx <= R; dx++)
        for (int dy = -R; dy <= R; dy++)
        for (int dz = -R; dz <= R; dz++) {
            int wx = x + dx, wy = y + dy, wz = z + dz;
            if (!level.isInBounds(wx, wy, wz)) continue;
            if (level.getTile(wx, wy, wz) == Block.LEAVES.id) {
                level.addToTickNextTick(wx, wy, wz, Block.LEAVES.id); // re-evaluate quickly
            }
        }
        // super.onRemoved(level, x, y, z); // call if your base expects it
    }

    public final int getDropCount() { return random.nextInt(3) + 3; }
    public final int getDrop() { return WOOD.id; }

    protected final int getTextureId(int face) {
        return face == 1 ? 21 : (face == 0 ? 21 : 20);
    }
}
