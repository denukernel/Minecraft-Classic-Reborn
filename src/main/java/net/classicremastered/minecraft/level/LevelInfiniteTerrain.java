package net.classicremastered.minecraft.level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.infinite.InfiniteTerrainGenerator;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.phys.AABB;

public final class LevelInfiniteTerrain extends Level {
    private final SimpleChunkManager chunks;
    public final long randomSeed;

    // liquid/physics tick list
    private final java.util.ArrayList<NextTickListEntry> tickList = new java.util.ArrayList<>();

    public LevelInfiniteTerrain(long seed, int depthY) {
        super();
        this.randomSeed = seed;

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
        this.chunks = new SimpleChunkManager(seed, depthY, SimpleChunkManager.Mode.TERRAIN);
        this.waterLevel = Math.max(1, depthY / 2);

        this.findSpawn();
        this.ySpawn = Math.min(34, depthY - 1);
    }

    // === Core block access ===
    // === Core block access ===

    @Override
    public void explode(Entity src, float fx, float fy, float fz, float power) { // fixed
        int minX = (int) (fx - power - 1.0F);
        int maxX = (int) (fx + power + 1.0F);
        int minY = (int) (fy - power - 1.0F);
        int maxY = (int) (fy + power + 1.0F);
        int minZ = (int) (fz - power - 1.0F);
        int maxZ = (int) (fz + power + 1.0F);

        // --- Block destruction --- // fixed
        for (int x = minX; x < maxX; ++x) {
            for (int y = maxY - 1; y >= minY; --y) {
                if (y < 0 || y >= this.depth)
                    continue; // fixed: Y-only bounds
                for (int z = minZ; z < maxZ; ++z) {
                    float dx = (x + 0.5F) - fx;
                    float dy = (y + 0.5F) - fy;
                    float dz = (z + 0.5F) - fz;
                    float dist2 = dx * dx + dy * dy + dz * dz;

                    if (dist2 < power * power) {
                        int id = getTile(x, y, z); // fixed: chunk-aware
                        if (id > 0) {
                            Block b = Block.blocks[id];
                            if (b != null && b.canExplode()) {
                                if (b == Block.TNT) {
                                    // --- TNT chain reaction fix --- // added
                                    setTile(x, y, z, 0); // clear TNT block
                                    b.explode(this, x, y, z); // spawn PrimedTnt entity
                                } else {
                                    // normal explodable blocks
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

        // --- Entity damage (unchanged) ---
        List entities = this.blockMap.getEntities(src, (float) minX, (float) minY, (float) minZ, (float) maxX,
                (float) maxY, (float) maxZ);

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
        if (y < 0 || y >= this.depth)
            return false;
        int old = getTile(x, y, z);
        if (old == id)
            return false;

        chunks.setBlock(x, y, z, (byte) id);

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
            if (id == 0)
                continue;
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
        if (y < 0)
            return false;
        if (y >= this.depth)
            return true;
        final int MAX_SCAN = 64;
        int yMax = Math.min(this.depth - 1, y + MAX_SCAN);
        for (int yy = y + 1; yy <= yMax; yy++) {
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
        if (!isInBounds(x, y, z))
            return;
        int nid = getTile(x, y, z);
        if (nid <= 0)
            return;
        Block b = Block.blocks[nid];
        if (b != null)
            b.onNeighborChange(this, x, y, z, changedId);
    }

    // put these at the top of LevelInfiniteTerrain
    private int bossBattleCounter = 0; // counts soundtrack ticks
    private boolean bossBattleWarned = false;

    // === Tick system ===
    @Override
    public void tick() {
        tickCount++;

        // --- Scheduled block ticks ---
        int processed = 0;
        int maxPerTick = 2050; // high cap for infinite terrain
        for (int i = 0; i < tickList.size() && processed < maxPerTick; i++) {
            NextTickListEntry e = tickList.remove(0);

            if (e.ticks > 0) {
                e.ticks--;
                tickList.add(e);
                continue;
            }

            int id = getTile(e.x, e.y, e.z);
            if (id == e.block && id > 0 && Block.blocks[id] != null) {
                Block.blocks[id].update(this, e.x, e.y, e.z, random);

                // liquids must always keep flowing → requeue next tick
                if (Block.blocks[id].getLiquidType() != LiquidType.NOT_LIQUID) {
                    addToTickNextTick(e.x, e.y, e.z, id);
                }
            }
            processed++;
        }

        // --- Random block updates near player ---
        if (player != null) {
            int updates = 20;
            int range = 32;
            for (int i = 0; i < updates; i++) {
                int x = (int) player.x + random.nextInt(range) - range / 2;
                int z = (int) player.z + random.nextInt(range) - range / 2;
                int y = random.nextInt(this.depth);

                int id = getTile(x, y, z);
                if (id > 0 && Block.physics[id]) {
                    if (Block.blocks[id].getLiquidType() == LiquidType.NOT_LIQUID) {
                        Block.blocks[id].update(this, x, y, z, random);
                    }
                }
            }
        }

        // --- Mob despawn ---
        if (this.blockMap != null && this.blockMap.all != null) {
            final float DESPAWN_RANGE = 64f;
            final float DESPAWN_D2 = DESPAWN_RANGE * DESPAWN_RANGE;
            for (int i = blockMap.all.size() - 1; i >= 0; --i) {
                Object o = blockMap.all.get(i);
                if (!(o instanceof Mob))
                    continue;
                Mob m = (Mob) o;
                if (m.removed || m.health <= 0)
                    continue;
                if (player == null)
                    continue;
                float dx = m.x - player.x, dy = m.y - player.y, dz = m.z - player.z;
                if (dx * dx + dy * dy + dz * dz > DESPAWN_D2) {
                    m.remove();
                }
            }
        }

        // --- Mob spawn pass ---
        // Spawn mobs when this level is authoritative (not network mode). Don't rely on
        // `minecraft` being present.
        if (!this.isNetworkMode() && this.tickCount % 20 == 0) {
            MobSpawner spawner = new MobSpawner(this);
            spawner.spawn(4, this.player, null, true);
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
                    if (!c.loaded)
                        continue;
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
                                    c.blocks[idx] = (byte) (random.nextBoolean() ? Block.GRAVEL.id
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
                InfiniteTerrainGenerator gen = new InfiniteTerrainGenerator(randomSeed, depth);
                for (SimpleChunk c : loaded) {
                    if (c.corrupted26M) {
                        gen.generateChunk(c, c.cx * SimpleChunk.SIZE, c.cz * SimpleChunk.SIZE, false);
                        c.meshed = false;
                        c.corrupted26M = false;
                    }
                }
            }

            // --- 30M+ rainbow corruption ---
            if (dist >= 30_000_000L) {
                for (SimpleChunk c : loaded) {
                    if (!c.loaded)
                        continue;
                    if (!c.corrupted30M) { // corrupt once
                        for (int i = 0; i < 16; i++) {
                            int lx = random.nextInt(SimpleChunk.SIZE);
                            int lz = random.nextInt(SimpleChunk.SIZE);
                            int y = random.nextInt(depth - 1);
                            int idx = SimpleChunk.idx(lx, y, lz, depth);
                            c.blocks[idx] = switch (random.nextInt(6)) {
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
                InfiniteTerrainGenerator gen = new InfiniteTerrainGenerator(randomSeed, depth);
                for (SimpleChunk c : loaded) {
                    if (c.corrupted30M) {
                        gen.generateChunk(c, c.cx * SimpleChunk.SIZE, c.cz * SimpleChunk.SIZE, false);
                        c.meshed = false;
                        c.corrupted30M = false;
                    }
                }
            }

            // --- 26M+ day/night glitch ---
            if (dist >= 26_000_000L) {
                this.timeOfDay = (this.timeOfDay + random.nextInt(200) - 100) & 23999;
                if (random.nextInt(20) == 0) {
                    this.doDayNightCycle = !this.doDayNightCycle;
                }
            } else {
                // 🔹 restore normal cycle if back under 26M
                this.doDayNightCycle = true;
            }

            // --- 30M+ Boss soundtrack ---
            if (dist >= 30_000_000L && this.minecraft != null && this.minecraft.soundPC != null) {
                bossBattleCounter++;

                // First-time warning
                if (!bossBattleWarned && this.minecraft.hud != null) {
                    this.minecraft.hud.addChat("&4⚠ You are now fighting the world. A boss battle begins!");
                    bossBattleWarned = true;
                }

                // After ~1 cycle, second message
                if (bossBattleCounter == 8 * 6 && this.minecraft != null && this.minecraft.hud != null
                        && !bossBattleMessage2) {
                    this.minecraft.hud.addChat(
                            "&cYou will suffer the trip until you fall into the void, no one told you to get there.");
                    bossBattleMessage2 = true;
                }

                // Every 6 ticks (~0.3s at 20 TPS)
                if (bossBattleCounter % 6 == 0) {
                    int step = (bossBattleCounter / 6) % 16; // 0..15 pattern length

                    // === Base pitches pattern ===
                    float basePitch;
                    if (step < 8) {
                        basePitch = (step % 2 == 0) ? 0.6f : 0.6f * (float) Math.pow(2.0, 3.0 / 12.0);
                    } else {
                        basePitch = (step % 2 == 0) ? 0.6f * (float) Math.pow(2.0, 3.0 / 12.0)
                                : 0.6f * (float) Math.pow(2.0, 6.0 / 12.0);
                    }

                    this.minecraft.soundPC.playSoundClean("note/pling", 0.9f, basePitch);

                    // Harmony layer (+3)
                    float harmonyPitch = basePitch * (float) Math.pow(2.0, 3.0 / 12.0);
                    this.minecraft.soundPC.playSoundClean("note/pling", 0.7f, harmonyPitch);
                }
            }

            // --- 34M+ rainbow sky ---
            if (dist >= 34_000_000L) {
                this.forceRainbowSky = true;
                if (this.tickCount % 600 == 0 && this.minecraft != null && this.minecraft.hud != null) {
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
            if (!c.isActive(this.tickCount, timeout)) {
                c.dispose(); // clear arrays before removal
                it.remove();
                removed++;
            }
        }
    }


    @Override
    public void addToTickNextTick(int x, int y, int z, int blockId) {
        if (blockId <= 0)
            return;
        NextTickListEntry e = new NextTickListEntry(x, y, z, blockId);

        // Liquids tick every frame (fast like Classic finite worlds)
        if (Block.blocks[blockId].getLiquidType() != LiquidType.NOT_LIQUID) {
            e.ticks = 1;
        } else {
            e.ticks = Block.blocks[blockId].getTickDelay();
        }
        tickList.add(e);
    }

    // === Bounds ===
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

    // Add these fields to LevelInfiniteTerrain
    private final java.util.HashMap<Long, Float> brightnessCache = new java.util.HashMap<>();
    private int brightnessCacheTick = -1;

    // Extracted heavy brightness logic into a private helper
    private float computeBrightnessInternal(int x, int y, int z) {
        if (y < 0)
            return 0.05f;
        if (y >= this.depth)
            return 1.0f;

        int id = getTile(x, y, z);
        if (id > 0) {
            Block b = Block.blocks[id];
            if (b != null) {
                int lv = b.getLightValue();
                if (lv > 0)
                    return 1.0f; // 🔥 fire/lava = full bright
            }
        }

        // --- Nearby glow (reduced radius for performance) ---
        final int R = 3; // 🔥 much smaller radius
        final float R2 = R * R;
        float maxGlow = 0f;

        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    int idn = getTile(x + dx, y + dy, z + dz);
                    if (idn <= 0)
                        continue;
                    Block nb = Block.blocks[idn];
                    if (nb == null)
                        continue;

                    int lv = nb.getLightValue();
                    if (lv <= 0)
                        continue;

                    float dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 > R2)
                        continue;

                    float strength = (lv / 15.0f) * (1.0f - (float) Math.sqrt(dist2) / R);
                    if (strength > maxGlow)
                        maxGlow = strength;
                }
            }
        }

        if (maxGlow > 0f) {
            return Math.min(1.0f, 0.25f + maxGlow * 0.75f);
        }

        // Default: sky/cave brightness
        final float outside = getDayFactorSmooth();
        final float caveBase = 0.35f;
        return isLit(x, y, z) ? outside : caveBase;
    }

    // === Core block access ===
    @Override
    public int getTile(int x, int y, int z) {
        if (y < 0 || y >= this.depth)
            return 0;
        SimpleChunk c = chunks.getOrCreate(x >> 4, z >> 4);
        c.markActive(this.tickCount);
        return c.blocks[SimpleChunk.idx(x & 15, y, z & 15, this.depth)] & 0xFF;
    }

    // --- NEW light accessor ---
    public int getLight(int x, int y, int z) {
        if (y < 0 || y >= this.depth)
            return 0;
        SimpleChunk c = chunks.getOrCreate(x >> 4, z >> 4);
        return c.getLight(x & 15, y, z & 15);
    }

    // --- NEW light setter ---
    public void setLight(int x, int y, int z, int value) {
        if (y < 0 || y >= this.depth)
            return;
        SimpleChunk c = chunks.getOrCreate(x >> 4, z >> 4);
        c.setLight(x & 15, y, z & 15, value);
        markSliceAndNeighbors(x, y, z); // force rebuild for renderer
    }

    // === Tile setters ===
    @Override
    public boolean setTile(int x, int y, int z, int id) {
        if (y < 0 || y >= this.depth)
            return false;
        int old = getTile(x, y, z);
        if (old == id)
            return false;

        chunks.setBlock(x, y, z, (byte) id);

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
        this.tickCount = 0;

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
        if (y < 0 || y >= this.depth)
            return false;
        int old = getTile(x, y, z);
        if (old == id)
            return false;

        chunks.setBlock(x, y, z, (byte) id);

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
        return id > 0 && Block.blocks[id] != null && Block.blocks[id].getLiquidType() == LiquidType.WATER;
    }

    public LiquidType getLiquid(int x, int y, int z) {
        int id = getTile(x, y, z);
        return (id > 0 && Block.blocks[id] != null) ? Block.blocks[id].getLiquidType() : LiquidType.NOT_LIQUID;
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

    // helper
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
}
