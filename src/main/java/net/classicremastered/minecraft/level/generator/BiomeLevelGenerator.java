package net.classicremastered.minecraft.level.generator;

import java.util.ArrayList;
import java.util.List;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.GeneratedOccupant;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.structure.DesertPyramid;

import java.util.Random;

/** Seeded terrain with coherent climate regions and biome vegetation. */
public final class BiomeLevelGenerator {
    public enum Biome {
        PLAINS,
        DESERT,
        FOREST,
        HILLS,
        WETLAND,
        SNOWY_PLAINS,
        SNOWY_FOREST;

        boolean snowy() {
            return this == SNOWY_PLAINS || this == SNOWY_FOREST;
        }

        int treeChance() {
            return switch (this) {
                case FOREST, SNOWY_FOREST -> 24;
                case WETLAND -> 75;
                default -> 240;
            };
        }

        int leafId() {
            return snowy() ? Block.SNOWY_LEAVES.id : Block.LEAVES.id;
        }
    }

    private final long seed;
    private volatile net.classicremastered.minecraft.level.infinite.InfiniteTerrainGenerator caves;

    public BiomeLevelGenerator(long seed) {
        this.seed = seed;
    }

    private net.classicremastered.minecraft.level.infinite.InfiniteTerrainGenerator caveGenerator(
            int height) {
        if (height != 64)
            return new net.classicremastered.minecraft.level.infinite.InfiniteTerrainGenerator(
                    seed, height);
        if (caves == null)
            synchronized (this) {
                if (caves == null)
                    caves =
                            new net.classicremastered.minecraft.level.infinite
                                    .InfiniteTerrainGenerator(seed, 64);
            }
        return caves;
    }

    public Biome biomeAt(int x, int z) {
        double temperature = noise(x / 44.0, z / 44.0, 17);
        double moisture = noise(x / 38.0, z / 38.0, 41);
        if (temperature > 0.24 && moisture < 0.12) return Biome.DESERT;
        if (temperature < -.27) return moisture > .05 ? Biome.SNOWY_FOREST : Biome.SNOWY_PLAINS;
        if (noise(x / 52.0, z / 52.0, 93) > 0.35) return Biome.HILLS;
        if (moisture > 0.3 && temperature < -0.15) return Biome.WETLAND;
        if (moisture > 0.2) return Biome.FOREST;
        return Biome.PLAINS;
    }

    private int surfaceHeight(int x, int z) {
        double hills = Math.max(0, noise(x / 52.0, z / 52.0, 93) - 0.15) * 18;
        double lowlands =
                Math.max(0, noise(x / 38.0, z / 38.0, 41) - 0.2)
                        * Math.max(0, -noise(x / 44.0, z / 44.0, 17))
                        * 18;
        return (int)
                Math.round(
                        34
                                + noise(x / 32.0, z / 32.0, 7) * 6
                                + noise(x / 12.0, z / 12.0, 11) * 2
                                + hills
                                - lowlands);
    }

    private int oreBlock(int y, Random random) {
        int ore = random.nextInt(1000);
        if (y < 20 && ore >= 55 && ore < 70) return Block.REDSTONE_ORE.id;
        if (y < 12 && ore < 3) return Block.DIAMOND_ORE_NEW.id;
        if (y < 20 && ore < 10) return Block.GOLD_ORE.id;
        if (ore < 30) return Block.IRON_ORE.id;
        if (ore < 55) return Block.COAL_ORE.id;
        return Block.STONE.id;
    }

    public int groundHeight(int x, int z, int depth) {
        return Math.max(1, Math.min(depth - 2, surfaceHeight(x, z) + depth / 2 - 32));
    }

    /** Each chunk samples world coordinates only, including neighboring tree roots. */
    public void generateChunk(SimpleChunk chunk, boolean flat) {
        int worldX = chunk.cx * SimpleChunk.SIZE;
        int worldZ = chunk.cz * SimpleChunk.SIZE;
        int sea = Math.max(1, chunk.height / 2 - 2);
        int[] tops = new int[SimpleChunk.SIZE * SimpleChunk.SIZE];
        for (int z = 0; z < SimpleChunk.SIZE; z++) {
            for (int x = 0; x < SimpleChunk.SIZE; x++) {
                int gx = worldX + x, gz = worldZ + z;
                int top = flat ? 34 : groundHeight(gx, gz, chunk.height);
                tops[x + z * SimpleChunk.SIZE] = top;
                Biome biome = biomeAt(gx, gz);
                Random random = new Random(seed ^ gx * 341873128712L ^ gz * 132897987541L);
                for (int y = 0; y < chunk.height; y++) {
                    int id = 0;
                    if (y == 0) id = Block.BEDROCK.id;
                    else if (y > top && y <= sea) id = Block.STATIONARY_WATER.id;
                    else if (y <= top) {
                        if (y < top - 3) id = oreBlock(y, random);
                        else if (biome == Biome.DESERT || top <= sea + 1) id = Block.SAND.id;
                        else id = y == top ? grassId(biome) : Block.DIRT.id;
                    }
                    chunk.blocks[SimpleChunk.idx(x, y, z, chunk.height)] = (byte) id;
                }
            }
        }
        if (!flat) caveGenerator(chunk.height).carveCaves(chunk, worldX, worldZ, tops, sea);
        // Include roots outside this chunk so canopies do not stop at its edges.
        for (int z = worldZ - 2; z < worldZ + SimpleChunk.SIZE + 2; z++) {
            for (int x = worldX - 2; x < worldX + SimpleChunk.SIZE + 2; x++) {
                if (Math.floorMod(x, 4) != 1 || Math.floorMod(z, 4) != 1) continue;
                Biome biome = biomeAt(x, z);
                int top = flat ? 34 : groundHeight(x, z, chunk.height);
                int chance = biome.treeChance();
                Random random = new Random(seed ^ x * 341873128712L ^ z * 132897987541L ^ 71);
                if (biome == Biome.DESERT || top <= sea + 1 || random.nextInt(chance) >= 16)
                    continue;
                int trunkHeight = 4 + random.nextInt(2);
                if (top + trunkHeight + 2 >= chunk.height) continue;
                for (int dy = trunkHeight - 2; dy <= trunkHeight + 1; dy++) {
                    int radius = dy == trunkHeight + 1 ? 1 : 2;
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            if (Math.abs(dx) != radius || Math.abs(dz) != radius)
                                placeTreeBlock(chunk, x + dx, top + 1 + dy, z + dz, biome.leafId());
                        }
                    }
                }
                for (int dy = 1; dy <= trunkHeight; dy++)
                    placeTreeBlock(chunk, x, top + dy, z, Block.LOG.id);
                if (x >= worldX && x < worldX + 16 && z >= worldZ && z < worldZ + 16)
                    GeneratedOccupant.offer(chunk.generatedOccupants, seed, true, x, top + 1, z);
            }
        }
        DesertPyramid.decorateChunk(chunk, seed, flat);
        ReadBlock read =
                (x, y, z) ->
                        chunk.blocks[SimpleChunk.idx(x - worldX, y, z - worldZ, chunk.height)]
                                & 255;
        WriteBlock write =
                (x, y, z, id) ->
                        chunk.blocks[SimpleChunk.idx(x - worldX, y, z - worldZ, chunk.height)] =
                                (byte) id;
        decorateSurface(
                worldX,
                worldZ,
                worldX + 16,
                worldZ + 16,
                chunk.height,
                flat,
                chunk.generatedOccupants,
                read,
                write);
        coverVegetationAndSnow(worldX, worldZ, 16, chunk.height, read, write);
        chunk.meshed = false;
    }

    @FunctionalInterface
    private interface ReadBlock {
        int get(int x, int y, int z);
    }

    @FunctionalInterface
    private interface WriteBlock {
        void set(int x, int y, int z, int id);
    }

    private static int grassId(Biome biome) {
        return biome.snowy() ? Block.SNOWY_GRASS.id : Block.GRASS.id;
    }

    private void coverVegetationAndSnow(
            int minX, int minZ, int size, int depth, ReadBlock read, WriteBlock write) {
        for (int z = minZ; z < minZ + size; z++) {
            for (int x = minX; x < minX + size; x++) {
                Biome biome = biomeAt(x, z);
                if (!biome.snowy() && biome != Biome.PLAINS && biome != Biome.FOREST)
                    continue;
                for (int y = depth - 1; y >= 1; y--) {
                    int id = read.get(x, y, z);
                    if (id == 0) continue;
                    if (y + 1 < depth) {
                        if (biome.snowy()
                                && (id == Block.SNOWY_GRASS.id || id == Block.SNOWY_LEAVES.id)) {
                            write.set(x, y + 1, z, Block.SNOW_LAYER.id);
                        } else if (id == Block.GRASS.id && sample(x, z, 131) > .96) {
                            int flower = sample(x, z, 137) < .5 ? Block.DANDELION.id
                                    : Block.ROSE.id;
                            write.set(x, y + 1, z, flower);
                        } else if (biome == Biome.PLAINS
                                && id == Block.GRASS.id
                                && sample(x, z, 123) > .64 - .28 * noise(x / 7.0, z / 7.0, 127)) {
                            write.set(x, y + 1, z, Block.SHORT_GRASS.id);
                        }
                    }
                    break;
                }
            }
        }
    }

    private void decorateSurface(
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int depth,
            boolean flat,
            List<GeneratedOccupant> occupants,
            ReadBlock read,
            WriteBlock write) {
        int sea = depth / 2 - 2;
        for (int cz = Math.floorDiv(minZ, 64); cz <= Math.floorDiv(maxZ - 1, 64); cz++) {
            for (int cx = Math.floorDiv(minX, 64); cx <= Math.floorDiv(maxX - 1, 64); cx++) {
                var pyramid = DesertPyramid.site(seed, depth, cx, cz, flat);
                Random random =
                        new Random(seed ^ cx * 341873128712L ^ cz * 132897987541L ^ 0x484F555345L);
                int x = cx * 64 + 4 + random.nextInt(48), z = cz * 64 + 4 + random.nextInt(48);
                int low = depth, high = 0;
                boolean dry = true;
                for (int dz = 0; dz < 7; dz++)
                    for (int dx = 0; dx < 7; dx++) {
                        int y = flat ? 34 : groundHeight(x + dx, z + dz, depth);
                        low = Math.min(low, y);
                        high = Math.max(high, y);
                        Biome biome = biomeAt(x + dx, z + dz);
                        dry &= biome == Biome.PLAINS || biome == Biome.FOREST;
                    }
                if (!dry
                        || low <= sea + 1
                        || high - low > 4
                        || high + 5 >= depth
                        || nearPyramid(pyramid, x, z, 6)
                        || maxX - minX > 16 && (x + 7 > maxX || z + 7 > maxZ)) continue;
                int floor = (low + high) / 2;
                if (x + 3 >= minX && x + 3 < maxX && z + 3 >= minZ && z + 3 < maxZ)
                    GeneratedOccupant.offerHouse(occupants, seed, x + 3, floor + 1, z + 3);
                for (int wz = Math.max(z, minZ); wz < Math.min(z + 7, maxZ); wz++) {
                    for (int wx = Math.max(x, minX); wx < Math.min(x + 7, maxX); wx++) {
                        int ground = flat ? 34 : groundHeight(wx, wz, depth);
                        for (int y = Math.min(ground, floor);
                                y <= Math.max(high + 1, floor + 4);
                                y++) {
                            int dx = wx - x, dz = wz - z, dy = y - floor;
                            boolean wall = dx == 0 || dx == 6 || dz == 0 || dz == 6;
                            boolean door = dx == 3 && dz == 0 && dy <= 2;
                            int id =
                                    dy <= 0
                                            ? Block.COBBLESTONE.id
                                            : dy == 4
                                                    ? Block.WOOD.id
                                                    : wall && !door && dy < 4
                                                            ? (dy == 2
                                                                    ? Block.GLASS.id
                                                                    : Block.WOOD.id)
                                                            : 0;
                            write.set(wx, y, wz, id);
                        }
                    }
                }
            }
        }
        for (int cz = Math.floorDiv(minZ, 8); cz <= Math.floorDiv(maxZ - 1, 8); cz++) {
            for (int cx = Math.floorDiv(minX, 8); cx <= Math.floorDiv(maxX - 1, 8); cx++) {
                Random random =
                        new Random(seed ^ cx * 341873128712L ^ cz * 132897987541L ^ 0xCA67A5L);
                int x = cx * 8 + 1 + random.nextInt(6), z = cz * 8 + 1 + random.nextInt(6);
                int y = flat ? 34 : groundHeight(x, z, depth);
                int height = 1 + random.nextInt(3);
                if (x <= minX
                        || x >= maxX - 1
                        || z <= minZ
                        || z >= maxZ - 1
                        || y <= sea
                        || y + height >= depth
                        || biomeAt(x, z) != Biome.DESERT
                        || nearPyramid(
                                DesertPyramid.site(
                                        seed,
                                        depth,
                                        Math.floorDiv(x, 64),
                                        Math.floorDiv(z, 64),
                                        flat),
                                x,
                                z,
                                0)
                        || read.get(x, y, z) != Block.SAND.id) continue;
                boolean clear = true;
                for (int dy = 1; dy <= height; dy++) {
                    clear &=
                            read.get(x, y + dy, z) == 0
                                    && read.get(x - 1, y + dy, z) == 0
                                    && read.get(x + 1, y + dy, z) == 0
                                    && read.get(x, y + dy, z - 1) == 0
                                    && read.get(x, y + dy, z + 1) == 0;
                }
                if (clear)
                    for (int dy = 1; dy <= height; dy++) write.set(x, y + dy, z, Block.CACTUS.id);
            }
        }
    }

    private static boolean nearPyramid(DesertPyramid.Site site, int x, int z, int span) {
        return site != null
                && x + span + 1 >= site.x()
                && x <= site.x() + 21
                && z + span + 1 >= site.z()
                && z <= site.z() + 21;
    }

    private void placeTreeBlock(SimpleChunk chunk, int x, int y, int z, int id) {
        int lx = x - chunk.cx * SimpleChunk.SIZE, lz = z - chunk.cz * SimpleChunk.SIZE;
        if (lx < 0 || lx >= SimpleChunk.SIZE || lz < 0 || lz >= SimpleChunk.SIZE) return;
        int index = SimpleChunk.idx(lx, y, lz, chunk.height);
        int existing = chunk.blocks[index] & 255;
        if (existing == 0 || existing == Block.LEAVES.id || existing == Block.SNOWY_LEAVES.id)
            chunk.blocks[index] = (byte) id;
    }

    public Level generate(String creator, int size, boolean flat) {
        int depth = 64;
        int sea = 30;
        List<GeneratedOccupant> occupants = new ArrayList<>();
        byte[] blocks = new byte[size * size * depth];
        int[] surfaces = new int[size * size];
        Random random = new Random(seed ^ 0x5DEECE66DL);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                Biome biome = biomeAt(x, z);
                int top = flat ? 34 : surfaceHeight(x, z);
                surfaces[z * size + x] = top;
                for (int y = 0; y <= Math.max(top, sea); y++) {
                    int id = Block.STONE.id;
                    if (y == 0) id = Block.BEDROCK.id;
                    else if (y > top) id = Block.STATIONARY_WATER.id;
                    else if (y >= top - 3)
                        id =
                                biome == Biome.DESERT || top <= sea + 1
                                        ? Block.SAND.id
                                        : y == top ? grassId(biome) : Block.DIRT.id;
                    else {
                        id = oreBlock(y, random);
                    }
                    blocks[(y * size + z) * size + x] = (byte) id;
                }
            }
        }
        if (!flat) {
            for (int cz = 0; cz < size / 16; cz++) {
                for (int cx = 0; cx < size / 16; cx++) {
                    SimpleChunk chunk = new SimpleChunk(cx, cz, depth);
                    int[] tops = new int[256];
                    for (int z = 0; z < 16; z++) {
                        int column = (cz * 16 + z) * size + cx * 16;
                        System.arraycopy(surfaces, column, tops, z * 16, 16);
                        for (int y = 0; y < depth; y++)
                            System.arraycopy(
                                    blocks,
                                    y * size * size + column,
                                    chunk.blocks,
                                    SimpleChunk.idx(0, y, z, depth),
                                    16);
                    }
                    caveGenerator(depth).carveCaves(chunk, cx * 16, cz * 16, tops, sea);
                    net.classicremastered.minecraft.level.structure.Mineshaft.decorate(chunk, seed);
                    for (int z = 0; z < 16; z++) {
                        int column = (cz * 16 + z) * size + cx * 16;
                        for (int y = 0; y < depth; y++)
                            System.arraycopy(
                                    chunk.blocks,
                                    SimpleChunk.idx(0, y, z, depth),
                                    blocks,
                                    y * size * size + column,
                                    16);
                    }
                }
            }
        }
        for (int z = 3; z < size - 3; z++) {
            for (int x = 3; x < size - 3; x++) {
                Biome biome = biomeAt(x, z);
                int top = surfaces[z * size + x];
                int chance = biome.treeChance();
                if (top > sea + 1 && biome != Biome.DESERT && random.nextInt(chance) == 0) {
                    plantTree(blocks, size, depth, x, top + 1, z, random, biome.leafId());
                    GeneratedOccupant.offer(occupants, seed, true, x, top + 1, z);
                }
            }
        }
        Level level = new Level();
        level.waterLevel = sea;
        level.setData(size, depth, size, blocks);
        level.name = "Biomes";
        level.biomeSeed = seed;
        level.flatBiomes = flat;
        DesertPyramid.placeInFinite(level, seed);
        decorateSurface(
                0, 0, size, size, depth, flat, occupants, level::getTile, level::setTileNoUpdate);
        if (!flat) {
            net.classicremastered.minecraft.level.structure.CobblestoneTower.placeFinite(
                    level, seed);
        }
        coverVegetationAndSnow(0, 0, size, depth, level::getTile, level::setTileNoUpdate);
        for (var occupant : occupants) occupant.spawn(level);
        level.creator = creator;
        level.createTime = System.currentTimeMillis();
        // Choose a reproducible dry spawn with enough headroom, away from tree canopies.
        for (int z = size / 4; z < size * 3 / 4; z++) {
            for (int x = size / 4; x < size * 3 / 4; x++) {
                int top = surfaces[z * size + x];
                if (top > sea
                        && (level.getTile(x, top + 1, z) == 0
                                || level.getTile(x, top + 1, z) == Block.SNOW_LAYER.id
                                || level.getTile(x, top + 1, z) == Block.SHORT_GRASS.id
                                || level.getTile(x, top + 1, z) == Block.DANDELION.id
                                || level.getTile(x, top + 1, z) == Block.ROSE.id)
                        && level.getTile(x, top + 2, z) == 0) {
                    level.xSpawn = x;
                    level.ySpawn = top + 2;
                    level.zSpawn = z;
                    return level;
                }
            }
        }
        return level;
    }

    private void plantTree(
            byte[] blocks, int size, int depth, int x, int y, int z, Random random, int leafId) {
        int height = 4 + random.nextInt(2);
        if (y + height + 2 >= depth) return;
        for (int dy = 0; dy <= height + 1; dy++) {
            int radius = dy >= height - 2 ? 2 : 0;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (blocks[((y + dy) * size + z + dz) * size + x + dx] != 0) return;
                }
            }
        }
        for (int dy = height - 2; dy <= height + 1; dy++) {
            int radius = dy == height + 1 ? 1 : 2;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.abs(dx) != radius || Math.abs(dz) != radius)
                        blocks[((y + dy) * size + z + dz) * size + x + dx] = (byte) leafId;
                }
            }
        }
        for (int dy = 0; dy < height; dy++)
            blocks[((y + dy) * size + z) * size + x] = (byte) Block.LOG.id;
    }

    private double noise(double x, double z, long salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double north = sample(ix, iz, salt) * (1 - fx) + sample(ix + 1, iz, salt) * fx;
        double south = sample(ix, iz + 1, salt) * (1 - fx) + sample(ix + 1, iz + 1, salt) * fx;
        return north * (1 - fz) + south * fz;
    }

    private double sample(int x, int z, long salt) {
        long value =
                seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (salt * 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
