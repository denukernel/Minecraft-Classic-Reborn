package net.classicremastered.minecraft.level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.phys.AABB;

public final class LevelInfiniteFlat extends Level {
    private final SimpleChunkManager chunks;
    public final long randomSeed;

    public LevelInfiniteFlat(long seed, int depthY) {
        super();
        this.randomSeed = seed;

        this.width = 16;
        this.height = 16;
        this.depth = depthY;

        this.blocks = new byte[this.width * this.depth * this.height];

        this.blockMap = new BlockMap(this.width, this.depth, this.height);
        try {
            java.lang.reflect.Field f = this.blockMap.getClass().getDeclaredField("infiniteMode");
            f.setAccessible(true);
            f.setBoolean(this.blockMap, true);
        } catch (Throwable ignored) {}

        this.entityList = new Entity[0];
        this.blockers = new int[this.width * this.height];
        Arrays.fill(this.blockers, this.depth);

        // explicitly flat mode
        this.chunks = new SimpleChunkManager(seed, depthY, SimpleChunkManager.Mode.FLAT);
        this.waterLevel = Math.max(1, depthY / 2);

        this.findSpawn();
        this.ySpawn = Math.min(34, depthY - 1);
    }

    @Override
    public int getTile(int x, int y, int z) {
        if (y < 0 || y >= this.depth) return 0;

        if (y == 0) {
            int raw = chunks.getBlock(x, 0, z) & 255;
            if (this.creativeMode) return raw;
            return raw != 0 ? raw : Block.BEDROCK.id;
        }
        return chunks.getBlock(x, y, z) & 255;
    }

    private void markSliceAndNeighbors(int x, int y, int z) {
        if (this.minecraft == null || this.minecraft.levelRenderer == null)
            return;
        final int cx = x >> 4, sy = y >> 4, cz = z >> 4;
        final var lr = this.minecraft.levelRenderer;
        lr.markDirty(cx, sy, cz);
        int lx = x & 15, ly = y & 15, lz = z & 15;
        if (lx == 0)
            lr.markDirty(cx - 1, sy, cz);
        if (lx == 15)
            lr.markDirty(cx + 1, sy, cz);
        if (lz == 0)
            lr.markDirty(cx, sy, cz - 1);
        if (lz == 15)
            lr.markDirty(cx, sy, cz + 1);
        if (ly == 0)
            lr.markDirty(cx, sy - 1, cz);
        if (ly == 15)
            lr.markDirty(cx, sy + 1, cz);
    }

    @Override
    public boolean setTile(int x, int y, int z, int id) {
        if (y < 0 || y >= this.depth)
            return false;

        // Survival: block any modification to the bedrock floor
        if (y == 0 && !this.creativeMode)
            return false;

        int old = getTile(x, y, z);
        if (old == id)
            return false;

        chunks.setBlock(x, y, z, (byte) id);

        if (old > 0 && Block.blocks[old] != null)
            Block.blocks[old].onRemoved(this, x, y, z);
        if (id > 0 && Block.blocks[id] != null)
            Block.blocks[id].onAdded(this, x, y, z);

        updateNeighborsAt(x, y, z, id);
        markSliceAndNeighbors(x, y, z);
        return true;
    }

    @Override
    public boolean setTileNoNeighborChange(int x, int y, int z, int id) {
        if (y < 0 || y >= this.depth)
            return false;
        if (y == 0 && !this.creativeMode)
            return false;

        int old = getTile(x, y, z);
        if (old == id)
            return false;

        chunks.setBlock(x, y, z, (byte) id);

        if (old > 0 && Block.blocks[old] != null)
            Block.blocks[old].onRemoved(this, x, y, z);
        if (id > 0 && Block.blocks[id] != null)
            Block.blocks[id].onAdded(this, x, y, z);

        markSliceAndNeighbors(x, y, z);
        return true;
    }

    @Override
    public boolean setTileNoUpdate(int x, int y, int z, int id) {
        if (y < 0 || y >= this.depth)
            return false;
        if (y == 0 && !this.creativeMode)
            return false;

        chunks.setBlock(x, y, z, (byte) id);
        markSliceAndNeighbors(x, y, z);
        return true;
    }

    @Override
    public boolean netSetTile(int x, int y, int z, int id) {
        return setTile(x, y, z, id);
    }

    @Override
    public boolean netSetTileNoNeighborChange(int x, int y, int z, int id) {
        return setTileNoNeighborChange(x, y, z, id);
    }

    // === Infinite bounds ===
    @Override
    public boolean isInBounds(int x, int y, int z) {
        return y >= 0 && y < this.depth;
    }

    @Override
    public void findSpawn() {
        this.xSpawn = 0;
        this.zSpawn = 0;
        this.ySpawn = Math.min(this.depth - 1, getHighestTile(0, 0));
        this.rotSpawn = 0f;
    }

    public SimpleChunkManager chunks() {
        return chunks;
    }

    public int getWidth() {
        return 30000000;
    }

    public int getLength() {
        return 30000000;
    }

    public int getHeight() {
        return this.depth;
    }

    // === Terrain queries ===
    @Override
    public int getHighestTile(int x, int z) {
        for (int y = this.depth - 1; y >= 0; --y) {
            int id = getTile(x, y, z);
            if (id == 0)
                continue;
            Block b = Block.blocks[id];
            if (b != null && b.getLiquidType() == LiquidType.NOT_LIQUID)
                return y + 1;
        }
        return 0;
    }

    @Override
    public boolean isLit(int x, int y, int z) {
        if (y < 0)
            return false;
        if (y >= this.depth)
            return true;
        for (int yy = y + 1; yy < this.depth; yy++) {
            int id = getTile(x, yy, z);
            if (id > 0) {
                Block b = Block.blocks[id];
                if (b != null && b.isOpaque())
                    return false;
            }
        }
        return true;
    }

    @Override
    public float getBrightness(int x, int y, int z) {
        if (y < 0)
            return 0.05f;
        if (y >= this.depth)
            return 1.0f;
        if (isLit(x, y, z))
            return getDayFactorSmooth();

        int id = getTile(x, y, z);
        if (id > 0) {
            Block b = Block.blocks[id];
            if (b != null && b.getLightValue() > 0)
                return 0.8f;
        }

        final int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] d : dirs) {
            int nid = getTile(x + d[0], y + d[1], z + d[2]);
            if (nid > 0) {
                Block b = Block.blocks[nid];
                if (b != null && b.getLightValue() > 0)
                    return 0.6f;
            }
        }
        return 0.35f;
    }

    // === Block ticks (fire spread, sand, liquids, etc.) ===
    private java.util.ArrayList<NextTickListEntry> tickList = new java.util.ArrayList<>();

    @Override
    public void tick() {
        tickCount++;

        // --- scheduled updates (fire, fluids, TNT, etc.) ---
        int processed = 0;
        int maxPerTick = 100; // cap
        for (int i = 0; i < tickList.size() && processed < maxPerTick; i++) {
            NextTickListEntry e = tickList.remove(0);
            if (e.ticks > 0) {
                e.ticks--;
                tickList.add(e);
            } else {
                int id = getTile(e.x, e.y, e.z);
                if (id == e.block && id > 0 && Block.blocks[id] != null) {
                    Block.blocks[id].update(this, e.x, e.y, e.z, random);
                }
            }
            processed++;
        }

        // --- random block updates around player ---
        if (player != null) {
            int updates = 20; // smaller batch (keeps perf ok)
            int range = 32;
            for (int i = 0; i < updates; i++) {
                int x = (int) player.x + random.nextInt(range) - range / 2;
                int z = (int) player.z + random.nextInt(range) - range / 2;
                int y = random.nextInt(this.depth);

                int id = getTile(x, y, z);
                if (id > 0 && Block.physics[id]) {
                    Block.blocks[id].update(this, x, y, z, random);
                }
            }
        }
    }

    // allow FireBlock.addToTickNextTick to work
    @Override
    public void addToTickNextTick(int x, int y, int z, int blockId) {
        if (blockId <= 0)
            return;
        NextTickListEntry e = new NextTickListEntry(x, y, z, blockId);
        e.ticks = Block.blocks[blockId].getTickDelay();
        tickList.add(e);
    }

    // === Explosions (TNT chain fix) ===
    @Override
    public void explode(Entity src, float fx, float fy, float fz, float radius) {
        int minX = (int) (fx - radius - 1), maxX = (int) (fx + radius + 1);
        int minY = (int) (fy - radius - 1), maxY = (int) (fy + radius + 1);
        int minZ = (int) (fz - radius - 1), maxZ = (int) (fz + radius + 1);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                if (y < 0 || y >= this.depth)
                    continue;
                for (int z = minZ; z < maxZ; z++) {
                    float dx = x + 0.5f - fx, dy = y + 0.5f - fy, dz = z + 0.5f - fz;
                    if (dx * dx + dy * dy + dz * dz < radius * radius) {
                        int id = getTile(x, y, z);
                        if (id > 0) {
                            Block b = Block.blocks[id];
                            if (b != null && b.canExplode()) {
                                if (id == Block.TNT.id) {
                                    net.classicremastered.minecraft.level.tile.TNTBlock.ignite(this, x, y, z);
                                } else {
                                    b.dropItems(this, x, y, z, 0.3F);
                                    setTile(x, y, z, 0);
                                    b.explode(this, x, y, z);
                                }
                            }
                        }
                    }
                }
            }
        }

        List list = this.blockMap.getEntities(src, minX, minY, minZ, maxX, maxY, maxZ);
        for (Object o : list) {
            if (!(o instanceof Entity))
                continue;
            Entity e = (Entity) o;
            float dist = e.distanceTo(fx, fy, fz) / radius;
            if (dist <= 1.0f) {
                float scale = 1.0f - dist;
                e.hurt(src, (int) (scale * 15f + 1f));
            }
        }
    }

    @Override
    public boolean isSolidTile(int x, int y, int z) {
        int id = getTile(x, y, z);
        return id > 0 && Block.blocks[id] != null && Block.blocks[id].isSolid();
    }

    @Override
    public ArrayList getCubes(AABB box) {
        ArrayList out = new ArrayList();
        int x0 = (int) Math.floor(box.x0), x1 = (int) Math.floor(box.x1);
        int y0 = (int) Math.floor(box.y0), y1 = (int) Math.floor(box.y1);
        int z0 = (int) Math.floor(box.z0), z1 = (int) Math.floor(box.z1);

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (y < 0 || y >= this.depth)
                    continue;
                for (int z = z0; z <= z1; z++) {
                    int id = getTile(x, y, z);
                    if (id == 0)
                        continue;
                    Block b = Block.blocks[id];
                    if (b == null)
                        continue;
                    AABB bb = b.getCollisionBox(x, y, z);
                    if (bb != null && box.intersectsInner(bb))
                        out.add(bb);
                }
            }
        }
        return out;
    }
}
