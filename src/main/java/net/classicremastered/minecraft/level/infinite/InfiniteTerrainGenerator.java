package net.classicremastered.minecraft.level.infinite;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.generator.noise.CombinedNoise;
import net.classicremastered.minecraft.level.generator.noise.OctaveNoise;
import net.classicremastered.minecraft.level.structure.HouseStructure;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.VillagerAI;

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
    public void generateChunk(SimpleChunk c, int worldX, int worldZ, boolean corruptedFlat, Level level) {
        if (corruptedFlat) {
            generateCorruptedFlat(c, worldX, worldZ);
        } else {
            generateNormal(c, worldX, worldZ, level);
        }
    }

    /** Normal infinite terrain (classic terrain + ores + houses). */
    private void generateNormal(SimpleChunk c, int worldX, int worldZ, Level level) {
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
        if (level != null) {
            generateHouseIfSpawned(c, worldX, worldZ, rand, level);
        }
        c.meshed = false;
    }

    /** Clean flat: flat plains. */
    private void generateCorruptedFlat(SimpleChunk c, int worldX, int worldZ) {
        final int baseY = height / 2;

        for (int lx = 0; lx < CHUNK; lx++) {
            for (int lz = 0; lz < CHUNK; lz++) {
                for (int y = 0; y < height; y++) {
                    byte id = 0;
                    if (y == 0) {
                        id = (byte) Block.BEDROCK.id;
                    } else if (y < baseY - 2) {
                        id = (byte) Block.STONE.id;
                    } else if (y < baseY) {
                        id = (byte) Block.DIRT.id;
                    } else if (y == baseY) {
                        id = (byte) Block.GRASS.id;
                    }
                    c.blocks[SimpleChunk.idx(lx, y, lz, c.height)] = id;
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


    private void populateOreVeins(SimpleChunk c, int worldX, int worldZ, int blockId, int attempts, int veinSize, int maxY) {
        Random rand = new Random(seed ^ (worldX * 341873128712L + worldZ * 132897987541L + blockId * 31L));

        for (int i = 0; i < attempts; i++) {
            int lx = rand.nextInt(CHUNK);
            int lz = rand.nextInt(CHUNK);
            int ly = rand.nextInt(Math.max(1, maxY));

            // Try to form a small cluster / vein
            for (int j = 0; j < veinSize; j++) {
                int xx = lx + rand.nextInt(3) - 1;
                int yy = ly + rand.nextInt(3) - 1;
                int zz = lz + rand.nextInt(3) - 1;

                if (xx < 0 || xx >= CHUNK || zz < 0 || zz >= CHUNK || yy < 1 || yy >= height)
                    continue;

                int idx = SimpleChunk.idx(xx, yy, zz, height);
                byte id = c.blocks[idx];
                if (id == (byte) Block.STONE.id) {
                    c.blocks[idx] = (byte) blockId; // replace with ore
                }
            }
        }
    }
    public int getDeterministicGroundHeight(int gx, int gz) {
        final float SCALE = 1.3f;
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

        return base + waterLevel;
    }

    private int computeDeterministicTargetY(int x, int z) {
        int[] ys = new int[7 * 7];
        int n = 0;
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                ys[n++] = getDeterministicGroundHeight(x + dx, z + dz);
            }
        }
        java.util.Arrays.sort(ys);
        return ys[ys.length / 2] + 1;
    }

    private void generateHouseIfSpawned(SimpleChunk c, int worldX, int worldZ, Random rand, Level level) {
        int cx = c.cx;
        int cz = c.cz;
        Random chunkRand = new Random(seed ^ (cx * 7919L + cz * 104729L));

        // Rare/uncommon: 1 in 50 chunks (approx. 2%)
        if (chunkRand.nextInt(50) == 0) {
            int hx = worldX + 2 + chunkRand.nextInt(8);
            int hz = worldZ + 2 + chunkRand.nextInt(8);
            int targetY = computeDeterministicTargetY(hx, hz);

            if (targetY > waterLevel && targetY + 5 <= height) {
                int padY = targetY - 1;
                final int W = 7, D = 7, H = 5;

                // 1. Terraform: fill upward to pad with cobble, clear house volume
                for (int dx = 0; dx < W; dx++) {
                    for (int dz = 0; dz < D; dz++) {
                        int wx = hx + dx;
                        int wz = hz + dz;
                        int lx = wx & 15;
                        int lz = wz & 15;

                        int solid = getDeterministicGroundHeight(wx, wz);
                        if (solid >= 0) {
                            for (int y = solid + 1; y <= padY; y++) {
                                c.blocks[SimpleChunk.idx(lx, y, lz, height)] = (byte) Block.COBBLESTONE.id;
                            }
                            for (int y = targetY; y < targetY + H; y++) {
                                c.blocks[SimpleChunk.idx(lx, y, lz, height)] = 0; // air
                            }
                        }
                    }
                }

                // 2. Floor: cobblestone
                for (int dx = 0; dx < W; dx++) {
                    for (int dz = 0; dz < D; dz++) {
                        int lx = (hx + dx) & 15;
                        int lz = (hz + dz) & 15;
                        c.blocks[SimpleChunk.idx(lx, padY, lz, height)] = (byte) Block.COBBLESTONE.id;
                    }
                }

                // 3. Walls & Windows
                for (int dy = 1; dy < H - 1; dy++) {
                    for (int dx = 0; dx < W; dx++) {
                        for (int dz = 0; dz < D; dz++) {
                            boolean wall = (dx == 0 || dx == W - 1 || dz == 0 || dz == D - 1);
                            if (!wall) continue;

                            int lx = (hx + dx) & 15;
                            int lz = (hz + dz) & 15;
                            int y = padY + dy;

                            if (dy == 2 && !(dz == D / 2 && dx == 0)) {
                                c.blocks[SimpleChunk.idx(lx, y, lz, height)] = (byte) Block.GLASS.id;
                            } else {
                                c.blocks[SimpleChunk.idx(lx, y, lz, height)] = (byte) Block.WOOD.id;
                            }
                        }
                    }
                }

                // 4. Roof: wood
                for (int dx = 0; dx < W; dx++) {
                    for (int dz = 0; dz < D; dz++) {
                        int lx = (hx + dx) & 15;
                        int lz = (hz + dz) & 15;
                        c.blocks[SimpleChunk.idx(lx, padY + H - 1, lz, height)] = (byte) Block.WOOD.id;
                    }
                }

                // 5. Door gap
                int doorX = hx + W / 2;
                int doorZ = hz;
                c.blocks[SimpleChunk.idx(doorX & 15, padY + 1, doorZ & 15, height)] = 0;
                c.blocks[SimpleChunk.idx(doorX & 15, padY + 2, doorZ & 15, height)] = 0;

                // 6. Spawn Villagers
                int baseY = padY + 1;
                int minX = hx + 2, maxX = hx + W - 3;
                int minZ = hz + 2, maxZ = hz + D - 3;
                int toSpawn = 1 + (chunkRand.nextBoolean() ? (1 + chunkRand.nextInt(2)) : 0);

                for (int sx = minX; sx <= maxX && toSpawn > 0; sx++) {
                    for (int sz = minZ; sz <= maxZ && toSpawn > 0; sz++) {
                        int lx = sx & 15;
                        int lz = sz & 15;

                        if (c.blocks[SimpleChunk.idx(lx, baseY - 1, lz, height)] == (byte) Block.COBBLESTONE.id &&
                            c.blocks[SimpleChunk.idx(lx, baseY, lz, height)] == 0 &&
                            c.blocks[SimpleChunk.idx(lx, baseY + 1, lz, height)] == 0) {

                            Villager v = new Villager(level, sx + 0.5F, baseY + 1, sz + 0.5F);

                            float homeX = hx + W * 0.5f;
                            float homeZ = hz + D * 0.5f;
                            float homeY = baseY + 1;
                            float radius = 2.5f;

                            v.setHome(homeX, homeY, homeZ, radius);
                            VillagerAI vai = new VillagerAI(homeX, homeY, homeZ, radius);
                            vai.suppressRandomJump = true;
                            vai.runSpeed = 0.8f;
                            v.ai = vai;

                            level.addEntity(v);
                            toSpawn--;
                        }
                    }
                }
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (Math.min(v, hi));
    }
}
