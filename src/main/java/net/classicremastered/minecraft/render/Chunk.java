package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

public final class Chunk {

    private Level level;
    private int baseListId = -1;
    private static ShapeRenderer renderer = ShapeRenderer.instance;
    public static int chunkUpdates = 0;
    private int x;
    private int y;
    private int z;
    private int width;
    private int height;
    private int depth;
    public boolean visible = false;
    public boolean[] dirty = new boolean[2];
    public boolean[] empty = new boolean[2];
    public boolean loaded;
    public boolean inBuildQueue = false;
    public boolean compiledOnce = false;
    // Shared scratch storage: chunk compilation runs only on the GL thread.
    private static final int[][] passBlocks = new int[2][4096];

    public Chunk(Level var1, int var2, int var3, int var4, int var5, int var6) {
        this.level = var1;
        this.x = var2;
        this.y = var3;
        this.z = var4;
        this.width = this.height = this.depth = 16;
        MathHelper.sqrt((float) (this.width * this.width + this.height * this.height + this.depth * this.depth));
        this.baseListId = var6;
        this.setAllDirty();
    }

    public final void update() {
        if (this.level == null)
            return;
        long t0 = System.nanoTime();
        this.level.lightEngine.beginMesh(this.x, this.y, this.z);
        try {
        ++chunkUpdates;
        int x0 = this.x;
        int y0 = this.y;
        int z0 = this.z;
        int[] counts = {0, 0};
        // Read each cell once, in the contiguous order used by both level formats.
        // Store the id with the local position to avoid looking it up again per pass.
        for (int ly = 0; ly < 16; ly++) for (int lz = 0; lz < 16; lz++)
            for (int lx = 0; lx < 16; lx++) {
                int id = level.getTile(x0 + lx, y0 + ly, z0 + lz);
                        if (id <= 0
                                || Block.blocks[id] == null
                                || level.redstone().isMoving(x0 + lx, y0 + ly, z0 + lz, id))
                            continue;
                Block block = Block.blocks[id];
                int pass = block.getLiquidType() == net.classicremastered.minecraft.level.liquid.LiquidType.WATER
                    ? 1 : block.getLiquidType() == net.classicremastered.minecraft.level.liquid.LiquidType.LAVA
                    ? 0 : block.getRenderPass();
                if (pass >= 0 && pass < 2)
                    passBlocks[pass][counts[pass]++] = (id << 12) | (ly << 8) | (lz << 4) | lx;
            }

        // initialize passes
        for (int p = 0; p < 2; ++p) {
            this.dirty[p] = true;
            this.empty[p] = true;
        }

        for (int pass = 0; pass < 2; ++pass) {
            boolean drewSomething = false;

            GL11.glNewList(this.baseListId + pass, GL11.GL_COMPILE);
            if (counts[pass] > 0) {
                renderer.begin();
                for (int i = 0; i < counts[pass]; i++) {
                    int packed = passBlocks[pass][i];
                    drewSomething |= Block.blocks[packed >>> 12].render(level,
                        x0 + (packed & 15), y0 + ((packed >>> 8) & 15),
                        z0 + ((packed >>> 4) & 15), renderer);
                }
                renderer.end();
            }
            GL11.glEndList();

            this.dirty[pass] = false;
            this.empty[pass] = !drewSomething;

        }
        this.compiledOnce = true;
        } finally {
            this.level.lightEngine.endMesh();
        }
        net.classicremastered.minecraft.util.Debug.recordChunkMesh(System.nanoTime() - t0);
    }

    public final float distanceSquared(Player var1) {
        float var2 = var1.x - (float) this.x;
        float var3 = var1.y - (float) this.y;
        float var4 = var1.z - (float) this.z;
        return var2 * var2 + var3 * var3 + var4 * var4;
    }

    public final void setAllDirty() {
        for (int i = 0; i < 2; i++) {
            this.dirty[i] = true;
        }
    }

    public final void dispose() {
        this.setAllDirty();
        this.compiledOnce = false;
        this.level = null;
        this.inBuildQueue = false;
        this.loaded = false;
    }

    /** Infinite meshes own their two lists; finite lists are freed as one range. */
    public final void disposeLists() {
        if (baseListId > 0) GL11.glDeleteLists(baseListId, 2);
        baseListId = -1;
        dispose();
    }

    public final boolean outsideColumns(int cx, int cz, int radius) {
        return Math.abs((long) (x >> 4) - cx) > radius || Math.abs((long) (z >> 4) - cz) > radius;
    }

    public final int appendLists(int[] out, int idx, int pass) {
        if (!this.visible)
            return idx;
        if (!this.compiledOnce)
            return idx; // not built yet
        if (this.empty[pass])
            return idx; // skip empty display lists
        out[idx++] = this.baseListId + pass;
        return idx;
    }

    public final void clip(Frustrum var1) {
        if (var1 == null) {
            this.visible = true;
            return;
        }
        this.visible = var1.isBoxInFrustrum((float) this.x, (float) this.y, (float) this.z,
                (float) (this.x + this.width), (float) (this.y + this.height), (float) (this.z + this.depth));
    }

}
