package net.classicremastered.minecraft.level.infinite;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.generator.noise.CombinedNoise;
import net.classicremastered.minecraft.level.generator.noise.OctaveNoise;
import net.classicremastered.minecraft.level.structure.HouseStructure;
import net.classicremastered.minecraft.level.tile.Block;

public final class InfiniteTerrainGenerator {
    public static final int CHUNK = SimpleChunk.SIZE;

    private final long seed;
    private final int height;
    public final int waterLevel;

    private final CombinedNoise nH1, nH2;
    private final CombinedNoise nE1, nE2;
    private final OctaveNoise nVar;
    private final OctaveNoise nBeachA, nBeachB;

    public InfiniteTerrainGenerator(long seed, int height) {
        this.seed = seed;
        this.height = height;
        this.waterLevel = Math.max(1, height / 2);

        Random r = new Random(seed);
        nH1 = new CombinedNoise(new OctaveNoise(r, 8), new OctaveNoise(r, 8));
        nH2 = new CombinedNoise(new OctaveNoise(r, 8), new OctaveNoise(r, 8));
        nE1 = new CombinedNoise(new OctaveNoise(r, 8), new OctaveNoise(r, 8));
        nE2 = new CombinedNoise(new OctaveNoise(r, 8), new OctaveNoise(r, 8));
        nVar = new OctaveNoise(r, 8);
        nBeachA = new OctaveNoise(r, 8);
        nBeachB = new OctaveNoise(r, 8);
    }

    /**
     * Main entrypoint. corruptedFlat = true when Mode.FLAT.
     */
    public void generateChunk(SimpleChunk c, int worldX, int worldZ, boolean corruptedFlat) {
        if (corruptedFlat) {
            generateCorruptedFlat(c, worldX, worldZ);
        } else {
            generateNormal(c, worldX, worldZ);
        }
    }

    /** Normal infinite terrain (classic terrain + ores + houses). */
    private void generateNormal(SimpleChunk c, int worldX, int worldZ) {
        final float SCALE = 1.3f;
        Random rand = new Random(seed ^ (worldX * 341873128712L + worldZ * 132897987541L));

        for (int lx = 0; lx < CHUNK; lx++) {
            int gx = worldX + lx;
            for (int lz = 0; lz < CHUNK; lz++) {
                int gz = worldZ + lz;

                double a = nH1.compute(gx * SCALE, gz * SCALE) / 6.0 - 4.0;
                double b = nH2.compute(gx * SCALE, gz * SCALE) / 5.0 + 10.0 - 4.0;
                if (nVar.compute(gx, gz) / 8.0 > 0.0)
                    b = a;
                double h = Math.max(a, b) / 2.0;
                if (h < 0)
                    h *= 0.8;
                int base = (int) h;

                double e = nE1.compute(gx * 2, gz * 2) / 8.0;
                int eFlag = (nE2.compute(gx * 2, gz * 2) > 0.0) ? 1 : 0;
                if (e > 2.0)
                    base = (((base - eFlag) / 2) << 1) + eFlag;

                int dirtTop = base + waterLevel;
                int stoneTop = dirtTop + ((int) (nVar.compute(gx, gz) / 24.0) - 4);

                for (int y = 0; y < height; y++) {
                    byte id = 0;
                    if (y == 0)
                        id = (byte) Block.BEDROCK.id;
                    else if (y <= stoneTop)
                        id = (byte) Block.STONE.id;
                    else if (y <= dirtTop)
                        id = (byte) Block.DIRT.id;
                    c.blocks[SimpleChunk.idx(lx, y, lz, c.height)] = id;
                }

                if (dirtTop >= waterLevel && dirtTop < height) {
                    int idx = SimpleChunk.idx(lx, dirtTop, lz, c.height);
                    if (c.blocks[idx] == (byte) Block.DIRT.id) {
                        c.blocks[idx] = (byte) Block.GRASS.id;
                    }
                }

                for (int y = 1; y <= waterLevel && y < height; y++) {
                    int idx = SimpleChunk.idx(lx, y, lz, c.height);
                    if (c.blocks[idx] == 0) {
                        c.blocks[idx] = (byte) Block.WATER.id;
                    }
                }

                if (dirtTop < waterLevel + 2) {
                    int idx = SimpleChunk.idx(lx, dirtTop, lz, c.height);
                    if (c.blocks[idx] == (byte) Block.GRASS.id) {
                        boolean gravel = nBeachB.compute(gx, gz) > 12.0;
                        c.blocks[idx] = (byte) (gravel ? Block.GRAVEL.id : Block.SAND.id);
                    }
                }
            }
        }

        populateOreVeins(c, worldX, worldZ, Block.COAL_ORE.id, 8, 18, height - 6);
        populateOreVeins(c, worldX, worldZ, Block.IRON_ORE.id, 6, 12, height - 12);
        populateOreVeins(c, worldX, worldZ, Block.GOLD_ORE.id, 3, 8, height / 2);

        decorateChunk(c, worldX, worldZ, rand);
        c.meshed = false;
    }

    /** Corrupted flat: jittered plains with random holes and spikes. */
    private void generateCorruptedFlat(SimpleChunk c, int worldX, int worldZ) {
        Random rand = new Random(seed ^ (worldX * 4987142L + worldZ * 5947611L));
        final int baseY = height / 2;

        for (int lx = 0; lx < CHUNK; lx++) {
            int gx = worldX + lx;
            for (int lz = 0; lz < CHUNK; lz++) {
                int gz = worldZ + lz;

                int h = baseY + (int) (Math.sin(gx * 0.01) * 2) + (int) (Math.cos(gz * 0.01) * 2) + rand.nextInt(3) - 1;

                if ((gx ^ gz ^ seed) % 97 == 0) {
                    h += rand.nextInt(20) - 10;
                }

                h = clamp(h, 4, height - 4);

                for (int y = 0; y < height; y++) {
                    byte id = 0;
                    if (y == 0)
                        id = (byte) Block.BEDROCK.id;
                    else if (y < h - 3)
                        id = (byte) Block.STONE.id;
                    else if (y < h)
                        id = (byte) Block.DIRT.id;
                    else if (y == h)
                        id = (byte) Block.GRASS.id;
                    if (rand.nextInt(500) == 0)
                        id = 0; // corruption hole
                    c.blocks[SimpleChunk.idx(lx, y, lz, c.height)] = id;
                }

                for (int y = 1; y <= waterLevel && y < height; y++) {
                    int idx = SimpleChunk.idx(lx, y, lz, c.height);
                    if (c.blocks[idx] == 0)
                        c.blocks[idx] = (byte) Block.WATER.id;
                }
            }
        }
        c.meshed = false;
    }

 // InfiniteTerrainGenerator.java

    private void decorateChunk(SimpleChunk c, int worldX, int worldZ, Random rand) {
        // Tree rarity (lower = fewer trees)
        final int treeChance = 200; // about 1 in 18 blocks of grass → tree

        for (int lx = 0; lx < CHUNK; lx++) {
            for (int lz = 0; lz < CHUNK; lz++) {
                int gx = worldX + lx;
                int gz = worldZ + lz;

                // Find ground height
                int gy = -1;
                for (int y = height - 2; y > 0; y--) {
                    int id = c.blocks[SimpleChunk.idx(lx, y, lz, height)] & 0xFF;
                    if (id == Block.GRASS.id) {
                        gy = y + 1;
                        break;
                    }
                }
                if (gy <= 0 || gy >= height - 6) continue;

                // Chance to place a tree
                if (rand.nextInt(treeChance) != 0) continue;

                // ==== Classic tree generator ====
                int trunkH = 4 + rand.nextInt(2); // 4–5 tall

                // Trunk
                for (int t = 0; t < trunkH; t++) {
                    int yy = gy + t;
                    if (yy >= height) break;
                    c.blocks[SimpleChunk.idx(lx, yy, lz, height)] = (byte) Block.LOG.id;
                }

                // Leaves
                int topY = gy + trunkH;
                int leafR = 2;
                for (int dx = -leafR; dx <= leafR; dx++) {
                    for (int dz = -leafR; dz <= leafR; dz++) {
                        for (int dy = -2; dy <= 2; dy++) {
                            int xx = lx + dx;
                            int yy = topY + dy;
                            int zz = lz + dz;

                            if (xx < 0 || xx >= CHUNK || zz < 0 || zz >= CHUNK) continue;
                            if (yy < 0 || yy >= height) continue;

                            int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            if (dist > 4) continue; // round off edges

                            int idx = SimpleChunk.idx(xx, yy, zz, height);
                            if (c.blocks[idx] == 0) {
                                c.blocks[idx] = (byte) Block.LEAVES.id;
                            }
                        }
                    }
                }
            }
        }
    }


    // ore + deco helpers unchanged
    private void populateOreVeins(SimpleChunk c, int worldX, int worldZ, int blockId, int attempts, int veinSize,
            int maxY) {
        /* ... keep your existing code ... */ }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (Math.min(v, hi));
    }
}
