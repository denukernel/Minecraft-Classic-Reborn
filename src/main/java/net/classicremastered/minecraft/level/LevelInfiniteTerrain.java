package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.generator.BiomeLevelGenerator;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LevelInfiniteTerrain extends Level {
    final SimpleChunkManager chunks;
    public final long randomSeed;
    public final boolean biomesEnabled;

    public LevelInfiniteTerrain(long seed, int depthY) {
        this(seed, depthY, false);
    }

    public LevelInfiniteTerrain(long seed, int depthY, boolean biomesEnabled) {
        this(seed, depthY, biomesEnabled, false);
    }

    public LevelInfiniteTerrain(long seed, int depthY, boolean biomesEnabled, boolean flatBiomes) {
        this(seed, depthY, biomesEnabled, flatBiomes, true);
    }

    LevelInfiniteTerrain(
            long seed, int depthY, boolean biomesEnabled, boolean flatBiomes, boolean oceans) {
        super();
        this.flatBiomes = biomesEnabled && flatBiomes;
        this.oceanBiomes = biomesEnabled && !flatBiomes && oceans;
        this.randomSeed = seed;
        this.biomesEnabled = biomesEnabled;
        this.biomeSeed = biomesEnabled ? seed : null;

        this.width = 255; // dummy finite values (stub)
        this.height = 2;
        this.depth = depthY;

        // legacy array (used by base Level for lighting/neighbors)
        this.blocks = new byte[this.width * this.depth * this.height];

        this.blockMap = new BlockMap(this.width, this.depth, this.height);
        try {
            java.lang.reflect.Field f = this.blockMap.getClass().getDeclaredField("infiniteMode");
            f.setAccessible(true);
            f.setBoolean(this.blockMap, true);
        } catch (Throwable ignored) {
        }

        this.entityList = new Entity[0];
        this.blockers = new int[this.width * this.height];
        Arrays.fill(this.blockers, this.depth);

        // terrain mode
        this.chunks =
                new SimpleChunkManager(
                        seed,
                        depthY,
                        biomesEnabled
                                ? SimpleChunkManager.Mode.BIOMES
                                : SimpleChunkManager.Mode.TERRAIN,
                        this.oceanBiomes);
        this.chunks.level = this;
        this.waterLevel = Math.max(1, depthY / 2 - (biomesEnabled ? 2 : 0));

        this.findSpawn();
        if (!biomesEnabled) this.ySpawn = Math.min(34, depthY - 1);
    }

    // === Core block access ===
    // === Core block access ===

    @Override
    public void explode(
            Entity src, float fx, float fy, float fz, float power, boolean dropBlocks) { // fixed
        int minX = (int) (fx - power - 1.0F);
        int maxX = (int) (fx + power + 1.0F);
        int minY = (int) (fy - power - 1.0F);
        int maxY = (int) (fy + power + 1.0F);
        int minZ = (int) (fz - power - 1.0F);
        int maxZ = (int) (fz + power + 1.0F);

        // --- Block destruction --- // fixed
        for (int x = minX; x < maxX; ++x) {
            for (int y = maxY - 1; y >= minY; --y) {
                if (y < 0 || y >= this.depth) continue; // fixed: Y-only bounds
                for (int z = minZ; z < maxZ; ++z) {
                    float dx = (x + 0.5F) - fx;
                    float dy = (y + 0.5F) - fy;
                    float dz = (z + 0.5F) - fz;
                    float dist2 = dx * dx + dy * dy + dz * dz;

                    if (dist2 < power * power) {
                        int id = getTile(x, y, z); // fixed: chunk-aware
                        if (id > 0) {
                            Block b = Block.blocks[id];
                            if (b != null && canBlastDestroy(b, src)) {
                                if (b == Block.TNT) {
                                    // --- TNT chain reaction fix --- // added
                                    setTile(x, y, z, 0); // clear TNT block
                                    b.explode(this, x, y, z); // spawn PrimedTnt entity
                                } else {
                                    // normal explodable blocks
                                    if (dropBlocks) b.dropItems(this, x, y, z, 0.3F);
                                    setTile(x, y, z, 0);
                                    b.explode(this, x, y, z);
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Entity damage (unchanged) ---
        List entities =
                this.blockMap.getEntities(
                        src,
                        (float) minX,
                        (float) minY,
                        (float) minZ,
                        (float) maxX,
                        (float) maxY,
                        (float) maxZ);

        for (int i = 0; i < entities.size(); ++i) {
            Entity e = (Entity) entities.get(i);
            float dist = e.distanceTo(fx, fy, fz) / power;
            if (dist <= 1.0F) {
                float scale = 1.0F - dist;
                e.hurt(src, (int) (scale * 15.0F + 1.0F));
            }
        }
    }

    @Override
    public boolean setTileNoUpdate(int x, int y, int z, int id) {
        if (y < 0 || y >= this.depth) return false;
        int old = getTile(x, y, z);
        if (old == id) return false;

        chunks.setBlock(x, y, z, (byte) id);
        updateFlowLevelMap(x, y, z, id);

        // no neighbor updates or hooks; still mark chunk slices for rebuild
        markSliceAndNeighbors(x, y, z);
        return true;
    }

    // Add fields in SimpleChunk
    public boolean corrupted26M = false;
    public boolean corrupted30M = false;

    // === Terrain queries ===
    // Find the first solid, non-liquid block when scanning downward; return top air
    // Y (surface)
    // === Terrain queries (infinite-aware) ===

    // Find the first solid, non-liquid block scanning downward; return the air y
    // above it (surface)
    // === Terrain queries (infinite-aware) ===

    // Return air Y right above the first solid, non-liquid block.
    // Starts near expected surface instead of scanning full depth.
    @Override
    public int getHighestTile(int x, int z) {
        int yStart = Math.min(this.depth - 2, Math.max(2, this.waterLevel + 64));
        for (int y = yStart; y > 0; --y) {
            int id = getTile(x, y, z);
            if (id == 0) continue;
            Block b = Block.blocks[id];
            if (b != null && b.getLiquidType() == LiquidType.NOT_LIQUID) {
                return y + 1; // air above solid
            }
        }
        return 1; // safe fallback above bedrock
    }

    // Sky exposure with bounded upward scan so caves are actually dark.
    @Override
    public boolean isLit(int x, int y, int z) {
        if (y < 0) return false;
        if (y >= this.depth) return true;
        final int MAX_SCAN = 64;
        int yMax = Math.min(this.depth - 1, y + MAX_SCAN);
        for (int yy = y + 1; yy <= yMax; yy++) {
            int id = getTile(x, yy, z);
            if (id > 0) {
                Block b = Block.blocks[id];
                if (b != null && b.isOpaque()) return false;
            }
        }
        return true;
    }

    @Override
    public void updateNeighborsAt(int x, int y, int z, int changedId) {
        updateTileInf(x - 1, y, z, changedId);
        updateTileInf(x + 1, y, z, changedId);
        updateTileInf(x, y - 1, z, changedId);
        updateTileInf(x, y + 1, z, changedId);
        updateTileInf(x, y, z - 1, changedId);
        updateTileInf(x, y, z + 1, changedId);
    }

    // Chunk-aware neighbor notifier
    private void updateTileInf(int x, int y, int z, int changedId) {
        if (!isInBounds(x, y, z)) return;
        int nid = getTile(x, y, z);
        if (nid <= 0) return;
        Block b = Block.blocks[nid];
        if (b != null) b.onNeighborChange(this, x, y, z, changedId);
    }

    // put these at the top of LevelInfiniteTerrain
    private int bossBattleCounter = 0; // counts soundtrack ticks
    private boolean bossBattleWarned = false;

    // === Tick system ===
    @Override
    public void tick() {
        tickCount++;
        tickDayNightCycle();

        tickScheduledBlocks(128);

        // --- Random block updates near player ---
        if (player != null) {
            int updates = 20;
            int range = 32;
            for (int i = 0; i < updates; i++) {
                int x = (int) player.x + random.nextInt(range) - range / 2;
                int z = (int) player.z + random.nextInt(range) - range / 2;
                int y = random.nextInt(this.depth);

                int id = getTile(x, y, z);
                if (id != Block.WATER.id && id != Block.LAVA.id && shouldRandomTick(id, x, y, z)) {
                    Block.blocks[id].update(this, x, y, z, random);
                }
            }
        }

        // Shared persistence, rider protection, and monster lifetime rules.
        despawnDistantMobs();

        // --- Mob spawn pass ---
        // Spawn mobs when this level is authoritative (not network mode). Don't rely on
        // `minecraft` being present.
        if (!this.isNetworkMode() && this.tickCount % 20 == 0) {
            MobSpawner spawner = new MobSpawner(this);
            spawner.spawn(6, this.player, null, true);
        }

        // --- Cleanup old chunks ---
        cleanupInactiveChunks(600); // unload after 600 ticks (~30s at 20TPS)

        // === Apocalypse systems ===
        if (player != null) {
            long dist = (long) Math.max(Math.abs(player.x), Math.abs(player.z));
            var loaded = chunks.getAllChunks().values();

            // --- 26M+ corruption spread ---
            if (dist >= 26_000_000L) {
                for (SimpleChunk c : loaded) {
                    if (!c.loaded) continue;
                    if (!c.corrupted26M) { // corrupt once
                        for (int i = 0; i < 4; i++) {
                            int lx = random.nextInt(SimpleChunk.SIZE);
                            int lz = random.nextInt(SimpleChunk.SIZE);
                            int base = random.nextInt(depth - 4) + 2;
                            for (int y = base; y < base + 3 && y < depth; y++) {
                                int idx = SimpleChunk.idx(lx, y, lz, depth);
                                if (random.nextInt(10) < 2) {
                                    c.blocks[idx] = 0;
                                } else {
                                    c.blocks[idx] =
                                            (byte)
                                                    (random.nextBoolean()
                                                            ? Block.GRAVEL.id
                                                            : Block.COBBLESTONE.id);
                                }
                            }
                        }
                        c.meshed = false;
                        c.corrupted26M = true;
                    }
                }
            } else {
                // under 26M → restore corrupted chunks
                for (SimpleChunk c : loaded) {
                    if (c.corrupted26M) {
                        chunks.generateChunk(c);
                        c.meshed = false;
                        c.corrupted26M = false;
                    }
                }
            }

            // --- 30M+ rainbow corruption ---
            if (dist >= 30_000_000L) {
                for (SimpleChunk c : loaded) {
                    if (!c.loaded) continue;
                    if (!c.corrupted30M) { // corrupt once
                        for (int i = 0; i < 16; i++) {
                            int lx = random.nextInt(SimpleChunk.SIZE);
                            int lz = random.nextInt(SimpleChunk.SIZE);
                            int y = random.nextInt(depth - 1);
                            int idx = SimpleChunk.idx(lx, y, lz, depth);
                            c.blocks[idx] =
                                    switch (random.nextInt(6)) {
                                        case 0 -> (byte) Block.RED_WOOL.id;
                                        case 1 -> (byte) Block.YELLOW_WOOL.id;
                                        case 2 -> (byte) Block.GREEN_WOOL.id;
                                        case 3 -> (byte) Block.CYAN_WOOL.id;
                                        case 4 -> (byte) Block.BLUE_WOOL.id;
                                        case 5 -> (byte) Block.PURPLE_WOOL.id;
                                        default -> (byte) Block.GRASS.id;
                                    };
                        }
                        c.meshed = false;
                        c.corrupted30M = true;
                    }
                }
            } else {
                // under 30M → restore rainbow chunks
                for (SimpleChunk c : loaded) {
                    if (c.corrupted30M) {
                        chunks.generateChunk(c);
                        c.meshed = false;
                        c.corrupted30M = false;
                    }
                }
            }

            // Keep the distant-world lighting effect subordinate to the world gamerule.
            if (dist >= 26_000_000L && doDayNightCycle) {
                this.timeOfDay = Math.floorMod(this.timeOfDay + random.nextInt(200) - 100, dayLength);
            }

            // --- 30M+ boss soundtrack messages ---
            if (dist >= 30_000_000L && this.minecraft != null && this.minecraft.soundPC != null) {
                bossBattleCounter++;

                // First-time warning
                if (!bossBattleWarned && this.minecraft.hud != null) {
                    this.minecraft.hud.addChat(
                            "&4⚠ You are now fighting the world. A boss battle begins!");
                    bossBattleWarned = true;
                }

                // After ~1 cycle, second message
                if (bossBattleCounter == 8 * 6
                        && this.minecraft != null
                        && this.minecraft.hud != null
                        && !bossBattleMessage2) {
                    this.minecraft.hud.addChat(
                            "&cYou will suffer the trip until you fall into the void, no one told"
                                    + " you to get there.");
                    bossBattleMessage2 = true;
                }
            }

            // --- 34M+ rainbow sky ---
            if (dist >= 34_000_000L) {
                this.forceRainbowSky = true;
                if (this.tickCount % 600 == 0
                        && this.minecraft != null
                        && this.minecraft.hud != null) {
                    this.minecraft.hud.addChat("&5The sky is no longer tied to time...");
                }
            } else {
                this.forceRainbowSky = false;
            }
        }
    }

    private boolean bossBattleMessage2 = false;

    private void cleanupInactiveChunks(int timeout) {
        // Run only once every 5 seconds (100 ticks at 20 TPS)
        if (this.tickCount % 100 != 0) return;

        int removed = 0;
        int maxPerTick = 4; // limit how many chunks get removed per cycle

        var it = chunks.getAllChunks().entrySet().iterator();
        while (it.hasNext() && removed < maxPerTick) {
            var e = it.next();
            SimpleChunk c = e.getValue();
            if (c.loaded && !c.isActive(this.tickCount, timeout)) {
                c.loaded = false;
                c.meshed = false; // retain saved terrain and dormant entities

                removed++;
            }
        }
    }

    // === Bounds ===
    @Override
    public boolean isInBounds(int x, int y, int z) {
        return y >= 0 && y < this.depth;
    }

    @Override
    public void findSpawn() {
        if (oceanBiomes) {
            BiomeLevelGenerator biomes = BiomeLevelGenerator.forLevel(this);
            if (tryBiomeSpawn(0, 0)) return;
            // Sample height cheaply and generate chunks only where there may be dry land.
            for (int radius = 8; radius <= 3072; radius += 8) {
                for (int offset = -radius; offset < radius; offset += 8) {
                    if (tryOceanWorldSpawn(biomes, offset, -radius)
                            || tryOceanWorldSpawn(biomes, radius, offset)
                            || tryOceanWorldSpawn(biomes, -offset, radius)
                            || tryOceanWorldSpawn(biomes, -radius, -offset)) return;
                }
            }
        } else if (biomesEnabled) {
            for (int z = -32; z <= 32; z += 4) {
                for (int x = -32; x <= 32; x += 4) {
                    if (tryBiomeSpawn(x, z)) return;
                }
            }
        }
        this.xSpawn = 0;
        this.zSpawn = 0;
        int surface = getHighestTile(0, 0);
        this.ySpawn =
                Math.min(this.depth - 1, oceanBiomes ? Math.max(waterLevel + 2, surface) : surface);
        this.rotSpawn = 0f;
    }

    private boolean tryOceanWorldSpawn(BiomeLevelGenerator biomes, int x, int z) {
        return biomes.groundHeight(x, z, depth) >= waterLevel && tryBiomeSpawn(x, z);
    }

    private boolean tryBiomeSpawn(int x, int z) {
        int y = getHighestTile(x, z);
        int ground = getTile(x, y - 1, z);
        if (y <= waterLevel || y + 2 >= depth || getTile(x, y + 1, z) != 0) return false;
        if (oceanBiomes
                && ground == Block.SNOW_LAYER.id
                && getTile(x, y - 2, z) == Block.SNOWY_GRASS.id) {
            setSpawnPos(x, y + 1, z, 0);
            return true;
        }
        if (ground != Block.GRASS.id
                && ground != Block.SAND.id
                && (!oceanBiomes || ground != Block.SNOWY_GRASS.id)) {
            return false;
        }
        if (getTile(x, y, z) != 0) return false;
        setSpawnPos(x, y + 1, z, 0);
        return true;
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

    // === Core block access ===
    @Override
    public int getTile(int x, int y, int z) {
        if (y < 0 || y >= this.depth) return 0;
        SimpleChunk c = chunks.getOrCreate(x >> 4, z >> 4);
        c.markActive(this.tickCount);
        return c.blocks[SimpleChunk.idx(x & 15, y, z & 15, this.depth)] & 0xFF;
    }

    // --- NEW light accessor ---
    public int getLight(int x, int y, int z) {
        if (y < 0 || y >= this.depth) return 0;
        SimpleChunk c = chunks.getOrCreate(x >> 4, z >> 4);
        return c.getLight(x & 15, y, z & 15);
    }

    // --- NEW light setter ---
    public void setLight(int x, int y, int z, int value) {
        if (y < 0 || y >= this.depth) return;
        SimpleChunk c = chunks.getOrCreate(x >> 4, z >> 4);
        c.setLight(x & 15, y, z & 15, value);
        markSliceAndNeighbors(x, y, z); // force rebuild for renderer
    }

    // === Tile setters ===
    @Override
    public boolean setTile(int x, int y, int z, int id) {
        if (y < 0 || y >= this.depth) return false;
        int old = getTile(x, y, z);
        if (old == id) return false;

        chunks.setBlock(x, y, z, (byte) id);
        updateFlowLevelMap(x, y, z, id);

        // --- update light if block emits light ---
        if (id > 0 && Block.blocks[id] != null) {
            int lv = Block.blocks[id].getLightValue();
            if (lv > 0) {
                setLight(x, y, z, lv);
            } else {
                setLight(x, y, z, 0);
            }
        } else {
            setLight(x, y, z, 0);
        }

        if (old > 0 && Block.blocks[old] != null) {
            Block.blocks[old].onRemoved(this, x, y, z);
        }
        if (id > 0 && Block.blocks[id] != null) {
            Block.blocks[id].onAdded(this, x, y, z);

            // ✅ ensure leaves enter decay tick pipeline in infinite worlds
            if (id == Block.LEAVES.id) {
                addToTickNextTick(x, y, z, id);
            }
        }

        updateNeighborsAt(x, y, z, id);
        markSliceAndNeighbors(x, y, z);
        return true;
    }

    @Override
    public void initTransient() {
        // skip finite Level logic
        this.listeners = new ArrayList<>();
        this.random = new java.util.Random();
        if (this.flowLevels == null) this.flowLevels = new java.util.HashMap<>();

        // keep the existing infinite-aware blockMap
        if (this.blockMap == null) {
            this.blockMap = new BlockMap(1, 1, 1);
            this.blockMap.infiniteMode = true;
        }

        // reset entity state
        if (this.blockMap.all != null) {
            for (Object o : this.blockMap.all) {
                if (o instanceof net.classicremastered.minecraft.Entity e) {
                    e.xo = e.x;
                    e.yo = e.y;
                    e.zo = e.z;
                    e.xOld = e.x;
                    e.yOld = e.y;
                    e.zOld = e.z;
                    e.removed = false;
                    e.blockMap = this.blockMap;
                }
            }
        }
    }

    @Override
    public boolean setTileNoNeighborChange(int x, int y, int z, int id) {
        if (y < 0 || y >= this.depth) return false;
        int old = getTile(x, y, z);
        if (old == id) return false;

        chunks.setBlock(x, y, z, (byte) id);
        updateFlowLevelMap(x, y, z, id);

        if (old > 0 && Block.blocks[old] != null) {
            Block.blocks[old].onRemoved(this, x, y, z);
        }
        if (id > 0 && Block.blocks[id] != null) {
            Block.blocks[id].onAdded(this, x, y, z);

            // ✅ schedule decay when leaves appear, even w/o neighbor updates
            if (id == Block.LEAVES.id) {
                addToTickNextTick(x, y, z, id);
            }
        }

        markSliceAndNeighbors(x, y, z);
        return true;
    }

    @Override
    public float getBrightness(int x, int y, int z) {
        // let LightEngine handle proper day/night and emissive mixing
        return lightEngine.getBrightness(x, y, z);
    }

    // --- Liquid helpers (copied/adapted from finite Level) ---

    public boolean isWater(int x, int y, int z) {
        int id = getTile(x, y, z);
        return id > 0
                && Block.blocks[id] != null
                && Block.blocks[id].getLiquidType() == LiquidType.WATER;
    }

    public LiquidType getLiquid(int x, int y, int z) {
        int id = getTile(x, y, z);
        return (id > 0 && Block.blocks[id] != null)
                ? Block.blocks[id].getLiquidType()
                : LiquidType.NOT_LIQUID;
    }

    public boolean containsAnyLiquid(AABB box) {
        int x0 = (int) Math.floor(box.x0);
        int x1 = (int) Math.floor(box.x1 + 1.0);
        int y0 = (int) Math.floor(box.y0);
        int y1 = (int) Math.floor(box.y1 + 1.0);
        int z0 = (int) Math.floor(box.z0);
        int z1 = (int) Math.floor(box.z1 + 1.0);

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (getLiquid(x, y, z) != LiquidType.NOT_LIQUID) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean containsLiquid(AABB box, LiquidType type) {
        int x0 = (int) Math.floor(box.x0);
        int x1 = (int) Math.floor(box.x1 + 1.0);
        int y0 = (int) Math.floor(box.y0);
        int y1 = (int) Math.floor(box.y1 + 1.0);
        int z0 = (int) Math.floor(box.z0);
        int z1 = (int) Math.floor(box.z1 + 1.0);

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (getLiquid(x, y, z) == type) {
                        return true;
                    }
                }
            }
        }
        return false;
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
                if (y < 0 || y >= this.depth) continue;
                for (int z = z0; z <= z1; z++) {
                    int id = getTile(x, y, z);
                    if (id == 0) continue;
                    Block b = Block.blocks[id];
                    if (b == null) continue;
                    b.addCollisionBoxes(this, x, y, z, box, out);
                }
            }
        }
        return out;
    }

    private void markSliceAndNeighbors(int x, int y, int z) {
        if (minecraft == null || minecraft.levelRenderer == null) return;
        var renderer = minecraft.levelRenderer;
        if (renderer.level == this) {
            renderer.queueChunks(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
        }
    }
}
