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


    public static boolean isFlammable(int id) {
        if (id <= 0 || id >= Block.blocks.length) return false;
        Block b = Block.blocks[id];
        if (b == null) return false;

        // Core flammables only
        if (b == Block.WOOD || b == Block.LOG || b == Block.LEAVES
            || b == Block.BOOKSHELF || b == Block.TNT
            || b == Block.DANDELION || b == Block.ROSE
            || b == Block.BROWN_MUSHROOM || b == Block.RED_MUSHROOM)
            return true;

        // Wool variants
        return id >= Block.RED_WOOL.id && id <= Block.WHITE_WOOL.id;
    }


    private static float burnChance(int id) {
        if (id <= 0) return 0f;
        Block b = Block.blocks[id];
        if (b == null) return 0f;
        if (b == Block.LEAVES) return 0.9f;
        if (b == Block.LOG) return 0.4f;
        if (b == Block.WOOD) return 0.55f;
        if (b == Block.TNT) return 1.0f;
        if (b == Block.BOOKSHELF) return 0.75f;
        if (id >= Block.RED_WOOL.id && id <= Block.WHITE_WOOL.id) return 0.85f;
        if (b == Block.GRASS) return 0.15f;
        if (b == Block.DIRT) return 0.05f;
        if (b == Block.DANDELION || b == Block.ROSE) return 0.65f;
        return 0.35f;
    }


    private void tryIgniteAir(Level level, int x, int y, int z) {
        if (Block.blocks[level.getTile(x, y - 1, z)] == Block.BEDROCK) return;

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
        if (!level.isInBounds(x, y, z)) return false;

        int below = level.getTile(x, y - 1, z);

        // Fire can stay on any solid floor OR any flammable block below (like leaves)
        if (level.isSolidTile(x, y - 1, z) || isFlammable(below))
            return true;

        // Side attachments: fire stuck to flammable blocks on the sides
        if (isFlammable(level.getTile(x + 1, y, z))) return true;
        if (isFlammable(level.getTile(x - 1, y, z))) return true;
        if (isFlammable(level.getTile(x, y, z + 1))) return true;
        if (isFlammable(level.getTile(x, y, z - 1))) return true;

        // Fire sitting directly *inside* flammable block (like leaves cluster)
        if (isFlammable(level.getTile(x, y, z))) return true;

        return false;
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
        if (!hasFlammableNeighbor(level, x, y, z) && level.random.nextInt(6) == 0) {
            level.setTile(x, y, z, 0);
            return;
        }

        level.addToTickNextTick(x, y, z, this.id);
        if (level.minecraft != null && level.minecraft.levelRenderer != null) {
            int cx = x >> 4, cy = y >> 4, cz = z >> 4;
            level.minecraft.levelRenderer.markDirty(cx, cy, cz);
        }
        if (level.minecraft != null && level.minecraft.levelRenderer != null) {
            int cx = x >> 4, cy = y >> 4, cz = z >> 4;
            level.minecraft.levelRenderer.markDirty(cx, cy, cz);

            // NEW: also refresh the chunk slice below so ground gets re-lit
            if (y > 0) {
                int cyBelow = (y - 1) >> 4;
                level.minecraft.levelRenderer.markDirty(cx, cyBelow, cz);
            }
        }
    }

    @Override
    public void onRemoved(Level level, int x, int y, int z) {
        if (level.minecraft != null && level.minecraft.levelRenderer != null) {
            int cx = x >> 4, cy = y >> 4, cz = z >> 4;
            level.minecraft.levelRenderer.markDirty(cx, cy, cz);
        }
        if (level.minecraft != null && level.minecraft.levelRenderer != null) {
            int cx = x >> 4, cy = y >> 4, cz = z >> 4;
            level.minecraft.levelRenderer.markDirty(cx, cy, cz);

            // NEW: also refresh the chunk slice below so ground gets re-lit
            if (y > 0) {
                int cyBelow = (y - 1) >> 4;
                level.minecraft.levelRenderer.markDirty(cx, cyBelow, cz);
            }
        }
    }

    @Override
    public void update(Level level, int x, int y, int z, Random rand) {
     // if standing on non-flammable and no flammable neighbor, burn out quickly
        int below = level.getTile(x, y - 1, z);
        if (!isFlammable(below) && !hasFlammableNeighbor(level, x, y, z)) {
            if (rand.nextInt(4) == 0) { // about 1-in-4 chance per tick ≈ few seconds lifetime
                level.setTile(x, y, z, 0);
                return;
            }
        }

        if (!canStay(level, x, y, z)) {
            level.setTile(x, y, z, 0);
            return;
        }

        // Extinguish if near water (rain or adjacent water blocks)
        if (isNearWater(level, x, y, z)) {
            level.setTile(x, y, z, 0);
            return;
        }

        // Try consuming flammables nearby
        tryConsumeFlammable(level, x + 1, y, z, rand);
        tryConsumeFlammable(level, x - 1, y, z, rand);
        tryConsumeFlammable(level, x, y + 1, z, rand);
        tryConsumeFlammable(level, x, y - 1, z, rand);
        tryConsumeFlammable(level, x, y, z + 1, rand);
        tryConsumeFlammable(level, x, y, z - 1, rand);

        // Chance to spread
        if (rand.nextInt(15) == 0) {
            spreadIfNearFlammable(level, x + 1, y, z, rand);
            spreadIfNearFlammable(level, x - 1, y, z, rand);
            spreadIfNearFlammable(level, x, y + 1, z, rand);
            spreadIfNearFlammable(level, x, y - 1, z, rand);
            spreadIfNearFlammable(level, x, y, z + 1, rand);
            spreadIfNearFlammable(level, x, y, z - 1, rand);
        }

        // Burn out if isolated
        if (!hasFlammableNeighbor(level, x, y, z) && rand.nextInt(10) == 0) {
            level.setTile(x, y, z, 0);
            return;
        }

        level.addToTickNextTick(x, y, z, this.id);
    }

    private boolean isNearWater(Level level, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    int id = level.getTile(x + dx, y + dy, z + dz);
                    if (id == Block.WATER.id || id == Block.STATIONARY_WATER.id)
                        return true;
                }
        return false;
    }

    private void spreadIfNearFlammable(Level level, int x, int y, int z, Random rand) {
        if (!level.isInBounds(x, y, z) || level.getTile(x, y, z) != 0) return;
        if (isNearWater(level, x, y, z)) return;

        // Reduce speed of spread to avoid wildfire behavior
        if (hasFlammableNeighbor(level, x, y, z) && rand.nextFloat() < 0.3f) {
            int below = level.getTile(x, y - 1, z);
            if (isFlammable(below) || level.isSolidTile(x, y - 1, z))
                level.setTile(x, y, z, this.id);
        }

    }


    private boolean hasFlammableNeighbor(Level level, int x, int y, int z) {
        return isFlammable(level.getTile(x + 1, y, z)) || isFlammable(level.getTile(x - 1, y, z))
            || isFlammable(level.getTile(x, y + 1, z)) || isFlammable(level.getTile(x, y - 1, z))
            || isFlammable(level.getTile(x, y, z + 1)) || isFlammable(level.getTile(x, y, z - 1));
    }

    private void tryIgniteAirIfNextToFlammable(Level level, int x, int y, int z) {
        if (Block.blocks[level.getTile(x, y - 1, z)] == Block.BEDROCK) return;
        if (!level.isInBounds(x, y, z)) return;
        if (level.getTile(x, y, z) != 0) return;

        // Check if any neighbor is flammable *and* visible to air
        boolean nearFlammable =
            isFlammable(level.getTile(x + 1, y, z)) ||
            isFlammable(level.getTile(x - 1, y, z)) ||
            isFlammable(level.getTile(x, y, z + 1)) ||
            isFlammable(level.getTile(x, y, z - 1)) ||
            isFlammable(level.getTile(x, y - 1, z)) ||
            isFlammable(level.getTile(x, y + 1, z));

        if (!nearFlammable) return;

        // only light if can actually stay and random chance allows
        if (canStay(level, x, y, z) && level.random.nextFloat() < 0.8f)
            level.setTile(x, y, z, this.id);
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
