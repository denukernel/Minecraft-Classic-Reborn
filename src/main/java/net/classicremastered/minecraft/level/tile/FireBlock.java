package net.classicremastered.minecraft.level.tile;

import java.util.Random;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.Tessellator;

public final class FireBlock extends Block {

    private static final int TICK_DELAY = 3; // ~0.15s at 20 TPS

    // 🔥 Debug-editable rotation (global for all fire blocks)
    public static float globalRotation = 0f; // degrees, change in debugger

    public FireBlock(int id, int tex) {
        super(id, tex, "Fire");
        this.setData(Tile$SoundType.none, 1.0F, 1.0F, 0.0F);
        this.setTickDelay(TICK_DELAY);
        this.setBounds(0f, 0f, 0f, 1f, 1f, 1f);
        this.setPhysics(true);
    }

    @Override
    public int getLightValue() {
        return 14;
    }

    // --- flammability helpers ---
    private static boolean isWool(int id) {
        return id >= Block.RED_WOOL.id && id <= Block.WHITE_WOOL.id;
    }

    private static boolean isFlammable(int id) {
        if (id <= 0 || id >= Block.blocks.length) return false;
        Block b = Block.blocks[id];
        if (b == null) return false;
        return b == Block.WOOD || b == Block.LOG || b == Block.LEAVES || b == Block.BOOKSHELF
                || b == Block.TNT || isWool(id) || b == Block.DANDELION || b == Block.ROSE;
    }

    private static float burnChance(int id) {
        if (id <= 0) return 0f;
        if (id == Block.DANDELION.id || id == Block.ROSE.id) return 0.65f;
        if (id == Block.LEAVES.id) return 0.90f;
        if (isWool(id)) return 0.85f;
        if (id == Block.BOOKSHELF.id) return 0.75f;
        if (id == Block.WOOD.id) return 0.55f;
        if (id == Block.LOG.id) return 0.35f;
        if (id == Block.TNT.id) return 1.00f;
        return 0.40f;
    }

    private void tryIgniteAir(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y, z)) return;
        if (level.getTile(x, y, z) != 0) return;
        if (canStay(level, x, y, z)) level.setTile(x, y, z, this.id);
    }

    private void tryConsumeFlammable(Level level, int x, int y, int z, Random rand) {
        if (!level.isInBounds(x, y, z)) return;
        int id = level.getTile(x, y, z);
        if (!isFlammable(id)) return;
        if (id == Block.TNT.id) {
            net.classicremastered.minecraft.level.tile.TNTBlock.ignite(level, x, y, z);
            return;
        }
        if (rand.nextFloat() < burnChance(id)) {
            level.setTile(x, y, z, 0);
            if (canStay(level, x, y, z) && rand.nextFloat() < 0.6f) {
                level.setTile(x, y, z, this.id);
            }
        }
    }

    @Override public boolean isSolid() { return false; }
    @Override public boolean isOpaque() { return false; }
    @Override public boolean isCube() { return true; }
    @Override public int getRenderPass() { return 1; }
    @Override public AABB getCollisionBox(int x, int y, int z) { return null; }
    @Override public AABB getSelectionBox(int x, int y, int z) {
        float inset = 0.01f;
        return new AABB(x + inset, y, z + inset, x + 1 - inset, y + 1, z + 1 - inset);
    }
    @Override public int getDropCount() { return 0; }
    @Override public int getDrop() { return 0; }
    @Override public void onBreak(Level level, int x, int y, int z) {}

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedBlock) {
        if (!canStay(level, x, y, z)) level.setTile(x, y, z, 0);
    }

    private boolean canStay(Level level, int x, int y, int z) {
        return level.isSolidTile(x, y - 1, z) || level.isSolidTile(x + 1, y, z)
            || level.isSolidTile(x - 1, y, z) || level.isSolidTile(x, y, z + 1)
            || level.isSolidTile(x, y, z - 1);
    }

    public static void extinguish(Level level, int x, int y, int z) {
        if (level != null) level.setTile(x, y, z, 0);
    }

    @Override
    public void onAdded(Level level, int x, int y, int z) {
        if (!canStay(level, x, y, z)) {
            level.setTile(x, y, z, 0);
            return;
        }
        level.addToTickNextTick(x, y, z, this.id);
        if (level.minecraft != null && level.minecraft.levelRenderer != null) {
            int cx = x >> 4, cy = y >> 4, cz = z >> 4;
            level.minecraft.levelRenderer.markDirty(cx, cy, cz);
        }
    }

    @Override
    public void onRemoved(Level level, int x, int y, int z) {
        if (level.minecraft != null && level.minecraft.levelRenderer != null) {
            int cx = x >> 4, cy = y >> 4, cz = z >> 4;
            level.minecraft.levelRenderer.markDirty(cx, cy, cz);
        }
    }

    @Override
    public void update(Level level, int x, int y, int z, Random rand) {
        if (!canStay(level, x, y, z)) {
            level.setTile(x, y, z, 0);
            return;
        }
        tryConsumeFlammable(level, x + 1, y, z, rand);
        tryConsumeFlammable(level, x - 1, y, z, rand);
        tryConsumeFlammable(level, x, y + 1, z, rand);
        tryConsumeFlammable(level, x, y - 1, z, rand);
        tryConsumeFlammable(level, x, y, z + 1, rand);
        tryConsumeFlammable(level, x, y, z - 1, rand);

        if (rand.nextInt(12) == 0) {
            tryIgniteAirIfNextToFlammable(level, x + 1, y, z);
            tryIgniteAirIfNextToFlammable(level, x - 1, y, z);
            tryIgniteAirIfNextToFlammable(level, x, y + 1, z);
            if (rand.nextInt(3) == 0) tryIgniteAirIfNextToFlammable(level, x, y - 1, z);
            tryIgniteAirIfNextToFlammable(level, x, y, z + 1);
            tryIgniteAirIfNextToFlammable(level, x, y, z - 1);
        }

        if (!hasFlammableNeighbor(level, x, y, z) && rand.nextInt(12) == 0) {
            level.setTile(x, y, z, 0);
            return;
        }
        level.addToTickNextTick(x, y, z, this.id);
    }

    private boolean hasFlammableNeighbor(Level level, int x, int y, int z) {
        return isFlammable(level.getTile(x + 1, y, z)) || isFlammable(level.getTile(x - 1, y, z))
            || isFlammable(level.getTile(x, y + 1, z)) || isFlammable(level.getTile(x, y - 1, z))
            || isFlammable(level.getTile(x, y, z + 1)) || isFlammable(level.getTile(x, y, z - 1));
    }

    private void tryIgniteAirIfNextToFlammable(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y, z)) return;
        if (level.getTile(x, y, z) != 0) return;
        if (isFlammable(level.getTile(x + 1, y, z)) || isFlammable(level.getTile(x - 1, y, z))
         || isFlammable(level.getTile(x, y + 1, z)) || isFlammable(level.getTile(x, y - 1, z))
         || isFlammable(level.getTile(x, y, z + 1)) || isFlammable(level.getTile(x, y, z - 1))) {
            if (canStay(level, x, y, z)) level.setTile(x, y, z, this.id);
        }
    }

    @Override
    public boolean render(Level lvl, int x, int y, int z, ShapeRenderer sr) {
        Tessellator t = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslatef(x + 0.5f, y, z + 0.5f);

        if (globalRotation != 0f) {
            GL11.glRotatef(globalRotation, 0f, 1f, 0f);
        }
        GL11.glTranslatef(-0.5f, 0f, -0.5f);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);

        int tex = this.textureId;
        float u0 = (tex % 16) / 16.0F;
        float v0 = (tex / 16) / 16.0F;
        float u1 = u0 + 1.0F / 16F;
        float v1 = v0 + 1.0F / 16F;

        float h = 1.5f;
        float topInset = 0.3f; // 🔥 make smaller at top

        float b0 = 0f, b1 = 1f;          // bottom edges
        float t0 = 0f + topInset;        // top edges (shrunk in)
        float t1 = 1f - topInset;

        t.startDrawingQuads();
        t.setColorOpaque_F(1f, 1f, 1f);

        // north face
        t.addVertexWithUV(b0, 0, b0, u0, v1);
        t.addVertexWithUV(t0, h, t0, u0, v0);
        t.addVertexWithUV(t1, h, t0, u1, v0);
        t.addVertexWithUV(b1, 0, b0, u1, v1);

        // south face
        t.addVertexWithUV(b1, 0, b1, u0, v1);
        t.addVertexWithUV(t1, h, t1, u0, v0);
        t.addVertexWithUV(t0, h, t1, u1, v0);
        t.addVertexWithUV(b0, 0, b1, u1, v1);

        // west face
        t.addVertexWithUV(b0, 0, b1, u0, v1);
        t.addVertexWithUV(t0, h, t1, u0, v0);
        t.addVertexWithUV(t0, h, t0, u1, v0);
        t.addVertexWithUV(b0, 0, b0, u1, v1);

        // east face
        t.addVertexWithUV(b1, 0, b0, u0, v1);
        t.addVertexWithUV(t1, h, t0, u0, v0);
        t.addVertexWithUV(t1, h, t1, u1, v0);
        t.addVertexWithUV(b1, 0, b1, u1, v1);

        t.draw();

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        return true;
    }
}
