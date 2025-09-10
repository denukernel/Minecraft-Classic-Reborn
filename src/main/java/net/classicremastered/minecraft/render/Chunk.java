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
    boolean[] dirty = new boolean[2];
    public boolean loaded;
    public boolean inBuildQueue = false;

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
        ++chunkUpdates;
        int x0 = this.x;
        int y0 = this.y;
        int z0 = this.z;
        int x1 = this.x + this.width;
        int y1 = this.y + this.height;
        int z1 = this.z + this.depth;

        // mark both passes dirty
        for (int p = 0; p < 2; ++p) {
            this.dirty[p] = true;
        }

        for (int pass = 0; pass < 2; ++pass) {
            boolean anyOtherPass = false;
            boolean drewSomething = false;

            GL11.glNewList(this.baseListId + pass, GL11.GL_COMPILE);
            renderer.begin();

            for (int x = x0; x < x1; ++x) {
                for (int y = y0; y < y1; ++y) {
                    for (int z = z0; z < z1; ++z) {
                        int id = this.level.getTile(x, y, z);
                        if (id <= 0)
                            continue;
                        Block b = Block.blocks[id];
                        if (b == null)
                            continue;

                        // Force: water = pass 1, lava = pass 0
                        int renderPass = b.getRenderPass();
                        if (b.getLiquidType() == net.classicremastered.minecraft.level.liquid.LiquidType.WATER) {
                            renderPass = 1;
                        } else if (b.getLiquidType() == net.classicremastered.minecraft.level.liquid.LiquidType.LAVA) {
                            renderPass = 0;
                        }

                        if (renderPass != pass) {
                            anyOtherPass = true;
                        } else {
                            drewSomething |= b.render(this.level, x, y, z, renderer);
                        }
                    }
                }
            }

            renderer.end();
            GL11.glEndList();

            if (drewSomething) {
                this.dirty[pass] = false;
            }
            if (!anyOtherPass)
                break;
        }
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
        this.level = null;
    }

    public final int appendLists(int[] out, int idx, int pass) {
        if (!this.visible)
            return idx;
        if (this.dirty[pass])
            return idx; // skip if not rebuilt yet
        out[idx++] = this.baseListId + pass;
        return idx;
    }

    public final void clip(Frustrum var1) {
        this.visible = var1.isBoxInFrustrum((float) this.x, (float) this.y, (float) this.z,
                (float) (this.x + this.width), (float) (this.y + this.height), (float) (this.z + this.depth));
    }

}
