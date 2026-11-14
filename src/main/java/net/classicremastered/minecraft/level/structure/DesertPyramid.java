package net.classicremastered.minecraft.level.structure;

import net.classicremastered.minecraft.level.GeneratedOccupant;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine;
import net.classicremastered.minecraft.level.generator.BiomeLevelGenerator;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.StairBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Chunk-order-independent sandstone temple, with a buried four-chest TNT chamber. */
public final class DesertPyramid implements Structure {
    public record Site(int x, int y, int z) {}

    @Override
    public String id() {
        return "desert_pyramid";
    }

    public static Site site(long seed, int depth, int cellX, int cellZ) {
        return site(seed, depth, cellX, cellZ, false);
    }

    public static Site site(long seed, int depth, int cellX, int cellZ, boolean flat) {
        return site(seed, depth, cellX, cellZ, flat, new BiomeLevelGenerator(seed));
    }

    public static Site site(
            long seed, int depth, int cellX, int cellZ, boolean flat, BiomeLevelGenerator biomes) {
        Random random =
                new Random(seed ^ cellX * 341873128712L ^ cellZ * 132897987541L ^ 0xDE5E47L);
        int x = cellX * 64 + 8 + random.nextInt(24), z = cellZ * 64 + 8 + random.nextInt(24);
        if (random.nextInt(3) != 0) return null;
        int low = Integer.MAX_VALUE, high = 0;
        for (int dx = 0; dx <= 20; dx += 10)
            for (int dz = 0; dz <= 20; dz += 10) {
                if (biomes.biomeAt(x + dx, z + dz, flat) != BiomeLevelGenerator.Biome.DESERT)
                    return null;
                int y = flat ? 34 : biomes.groundHeight(x + dx, z + dz, depth);
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
        if (high - low > 6 || low < Math.max(14, depth / 2) || low + 15 >= depth) return null;
        return new Site(x, low, z);
    }

    public static int blockAt(int x, int y, int z) {
        if (x < 0 || x > 20 || z < 0 || z > 20 || y < -12 || y > 14) return -1;
        if (y < -4) {
            if (x < 6 || x > 14 || z < 6 || z > 14) return -1;
            if (y == -12)
                return x >= 9 && x <= 11 && z >= 9 && z <= 11 ? Block.TNT.id : Block.SANDSTONE.id;
            if (y == -11) return Block.SANDSTONE.id;
            if (x == 6 || x == 14 || z == 6 || z == 14) return Block.CUT_SANDSTONE.id;
            if (y >= -6) return x >= 9 && x <= 11 && z >= 9 && z <= 11 ? 0 : Block.SANDSTONE.id;
            if (y == -10) {
                if (x == 10 && z == 10) return Block.STONE_PRESSURE_PLATE.id;
                if (x == 8 && z == 10
                        || x == 12 && z == 10
                        || x == 10 && z == 8
                        || x == 10 && z == 12) return Block.CHEST.id;
            }
            return 0;
        }
        if (y < 0) return x >= 9 && x <= 11 && z >= 9 && z <= 11 ? 0 : Block.SANDSTONE.id;
        if (y == 0 && z == 0 && (x == 2 || x >= 9 && x <= 11 || x == 18))
            return StairBlock.id(0, 3, false);
        if (y == 0) {
            if (x == 10 && z == 10) return Block.BLUE_TERRACOTTA.id;
            int dx = Math.abs(x - 10), dz = Math.abs(z - 10);
            if (dx == 1 && dz == 1 || dx + dz >= 2 && dx + dz <= 3 && (dx == 0 || dz == 0))
                return Block.ORANGE_TERRACOTTA.id;
            return Block.SANDSTONE.id;
        }
        int interior = interiorAt(x, y, z);
        if (interior >= 0)
            return interior;
        if ((x <= 4 || x >= 16) && z <= 4 && y <= 10) {
            if (y == 10) return Block.CHISELED_SANDSTONE.id;
            if (z == 0
                    && (x >= 1 && x <= 3 || x >= 17 && x <= 19)
                    && y >= 4
                    && y <= 7
                    && (y == 5 || x == 2 || x == 18)) return Block.ORANGE_TERRACOTTA.id;
            if (y <= 3 && z == 0 && (x == 2 || x == 18)) return 0;
            return x == 0 || x == 4 || x == 16 || x == 20 || z == 0 || z == 4
                    ? Block.CUT_SANDSTONE.id
                    : 0;
        }
        if (z <= 3 && x >= 8 && x <= 12 && y == 4) return Block.CHISELED_SANDSTONE.id;
        if (z <= 3 && (x == 8 || x == 12) && y <= 3) return Block.CUT_SANDSTONE.id;
        if (x >= 9 && x <= 11 && z <= 7 && y <= 3) return 0;
        if ((x == 8 || x == 12) && (z == 8 || z == 12) && y <= 3) return Block.CUT_SANDSTONE.id;
        if (z >= 5 && z <= 17 && (x == 4 || x == 16)) {
            if (y == 3) return Block.SANDSTONE.id;
            if (y <= 2)
                return isGalleryOpening(z)
                        ? 0
                        : y == 1 ? Block.CUT_SANDSTONE.id : Block.CHISELED_SANDSTONE.id;
        }
        if (y <= 2 && z >= 5 && z <= 16 && (x == 3 || x == 17)) return 0;
        if (y == 4 && x >= 5 && x <= 15 && z >= 5 && z <= 15)
            return x >= 9 && x <= 11 && z >= 9 && z <= 11 ? 0 : Block.SANDSTONE.id;
        if (y <= 9 && x >= y && x <= 20 - y && z >= y && z <= 20 - y)
            return x == y || x == 20 - y || z == y || z == 20 - y || y == 9
                    ? Block.SANDSTONE.id
                    : 0;
        return y <= 10 ? 0 : -1;
    }

    private static int interiorAt(int x, int y, int z) {
        if ((x == 2 || x == 18) && z >= 3 && z <= 7) {
            int stairY = z - 2;
            if (y == stairY)
                return StairBlock.id(0, 3, false);
            if (y > stairY && y <= stairY + 3)
                return 0;
        }
        if ((x == 2 || x == 18) && z >= 8 && z <= 12) {
            if (y == 5)
                return Block.CUT_SANDSTONE.id;
            if (y >= 6 && y <= 8)
                return 0;
        }
        if (z >= 11 && z <= 12 && (x >= 3 && x <= 7 || x >= 13 && x <= 17)) {
            if (y == 5)
                return Block.CUT_SANDSTONE.id;
            if (y >= 6 && y <= 8)
                return 0;
        }
        if (x >= 7 && x <= 13 && z >= 5 && z <= 15 && y >= 1 && y <= 5) {
            if ((x == 8 || x == 12) && (z == 8 || z == 12) && y <= 3)
                return Block.CHISELED_SANDSTONE.id;
            if (y == 5 && (x == 7 || x == 13) && z >= 11 && z <= 12)
                return Block.CUT_SANDSTONE.id;
            return 0;
        }
        return -1;
    }

    private static boolean isGalleryOpening(int z) {
        return z >= 6 && z <= 16 && z % 2 == 0;
    }

    private static void offerIronmocks(
            List<GeneratedOccupant> pending, long seed, Site site, SimpleChunk owner) {
        for (int side = 4; side <= 16; side += 12) {
            int x = site.x + side;
            for (int offset = 6; offset <= 16; offset += 2) {
                int z = site.z + offset;
                if (owner == null || owner.cx == x >> 4 && owner.cz == z >> 4)
                    GeneratedOccupant.offerIronmock(pending, seed, x, site.y + 1, z);
            }
        }
    }

    public static void decorateChunk(SimpleChunk chunk, long seed, boolean flat) {
        decorateChunk(chunk, seed, flat, new BiomeLevelGenerator(seed));
    }

    public static void decorateChunk(
            SimpleChunk chunk, long seed, boolean flat, BiomeLevelGenerator biomes) {
        Site site =
                site(
                        seed,
                        chunk.height,
                        Math.floorDiv(chunk.cx, 4),
                        Math.floorDiv(chunk.cz, 4),
                        flat,
                        biomes);
        if (site == null) return;
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++)
                for (int y = Math.max(0, site.y - 12);
                        y < Math.min(chunk.height, site.y + 15);
                        y++) {
                    int id =
                            blockAt(
                                    chunk.cx * 16 + x - site.x,
                                    y - site.y,
                                    chunk.cz * 16 + z - site.z);
                    if (id >= 0) chunk.blocks[SimpleChunk.idx(x, y, z, chunk.height)] = (byte) id;
                }
        offerIronmocks(chunk.generatedOccupants, seed, site, chunk);
    }

    public static void placeInFinite(Level level, long seed) {
        BiomeLevelGenerator biomes = BiomeLevelGenerator.forLevel(level);
        for (int cz = 0; cz * 64 < level.height; cz++)
            for (int cx = 0; cx * 64 < level.width; cx++) {
                Site site = site(seed, level.depth, cx, cz, level.flatBiomes, biomes);
                if (site != null && site.x + 20 < level.width && site.z + 20 < level.height)
                    new DesertPyramid()
                            .generate(level, site.x, site.y + 1, site.z, new Random(seed));
            }
    }

    @Override
    public boolean canPlace(Level level, int x, int y, int z) {
        if (level.biomeSeed == null || y < 14 || y + 14 >= level.depth) return false;
        BiomeLevelGenerator biomes = BiomeLevelGenerator.forLevel(level);
        for (int dx = 0; dx <= 20; dx += 10)
            for (int dz = 0; dz <= 20; dz += 10)
                if (!level.isInBounds(x + dx, y, z + dz)
                        || biomes.biomeAt(x + dx, z + dz, level.flatBiomes)
                                != BiomeLevelGenerator.Biome.DESERT) return false;
        return true;
    }

    @Override
    public boolean generate(Level level, int x, int y, int z, Random random) {
        if (!canPlace(level, x, y, z)) return false;
        for (int dz = 0; dz <= 20; dz++)
            for (int dx = 0; dx <= 20; dx++)
                for (int dy = -12; dy <= 14; dy++) {
                    int id = blockAt(dx, dy, dz);
                    if (id >= 0) level.setTileNoUpdate(x + dx, y - 1 + dy, z + dz, id);
                }
        List<GeneratedOccupant> occupants = new ArrayList<>();
        offerIronmocks(occupants, level.biomeSeed, new Site(x, y - 1, z), null);
        for (GeneratedOccupant occupant : occupants) occupant.spawn(level);
        return true;
    }

    public static void fillLoot(Level level, RedstoneEngine.Pos pos, RedstoneEngine.State state) {
        if (level.biomeSeed == null) return;
        Site site =
                site(
                        level.biomeSeed,
                        level.depth,
                        Math.floorDiv(pos.x(), 64),
                        Math.floorDiv(pos.z(), 64),
                        level.flatBiomes,
                        BiomeLevelGenerator.forLevel(level));
        if (site == null
                || blockAt(pos.x() - site.x, pos.y() - site.y, pos.z() - site.z) != Block.CHEST.id)
            return;
        ItemRegistry.bootstrap();
        int[] loot = {
            Block.IRON_ORE.id,
            Block.GOLD_ORE.id,
            Equipment.STRING.id + 256,
            Equipment.ARROW.id + 256,
            Item.APPLE.id + 256,
            Block.DIAMOND_ORE_NEW.id
        };
        Random random = new Random(level.biomeSeed ^ pos.x() * 7919L ^ pos.z() * 104729L);
        for (int n = 0; n < 6; n++) {
            int slot = random.nextInt(27);
            state.items[slot] = loot[random.nextInt(loot.length)];
            state.counts[slot] = 1 + random.nextInt(4);
        }
    }
}
