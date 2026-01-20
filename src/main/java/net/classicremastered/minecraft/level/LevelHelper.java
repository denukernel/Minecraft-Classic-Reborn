package net.classicremastered.minecraft.level;

import java.util.Random;
import net.classicremastered.minecraft.level.tile.Block;

public final class LevelHelper {

    private final Level level;

    public LevelHelper(Level level) {
        this.level = level;
    }

    /** Shared neighbor updates for finite and infinite levels. */
    public void updateNeighbors(int x, int y, int z, int changedId) {
        updateTile(x - 1, y, z, changedId);
        updateTile(x + 1, y, z, changedId);
        updateTile(x, y - 1, z, changedId);
        updateTile(x, y + 1, z, changedId);
        updateTile(x, y, z - 1, changedId);
        updateTile(x, y, z + 1, changedId);
    }

    private void updateTile(int x, int y, int z, int changedId) {
        if (!level.isInBounds(x, y, z)) return;
        int id = level.getTile(x, y, z);
        if (id <= 0) return;
        Block b = Block.blocks[id];
        if (b != null) b.onNeighborChange(level, x, y, z, changedId);
    }

    /** Unified light value accessor (delegates to LightEngine). */
    public float getBrightness(int x, int y, int z) {
        return level.lightEngine != null ? level.lightEngine.getBrightness(x, y, z) : 1.0f;
    }

    /** Shared block tick scheduling. */
    public void scheduleTick(int x, int y, int z, int id) {
        if (level.isNetworkMode()) return;
        int delay = (id > 0 && Block.blocks[id] != null) ? Block.blocks[id].getTickDelay() : 1;
        level.addToTickNextTick(x, y, z, id);
    }

    /** Simple shared random tick handler. */
    public void randomUpdate(Random rand) {
        int w = level.width, h = level.height, d = level.depth;
        for (int i = 0; i < 16; i++) {
            int x = rand.nextInt(w);
            int y = rand.nextInt(d);
            int z = rand.nextInt(h);
            int id = level.getTile(x, y, z);
            if (id > 0 && Block.physics[id]) {
                Block.blocks[id].update(level, x, y, z, rand);
            }
        }
    }
}
