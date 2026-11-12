package net.classicremastered.minecraft.level.structure;

import net.classicremastered.minecraft.level.GeneratedOccupant;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

import java.util.Random;
import java.util.function.Consumer;

public final class CobblestoneTower implements Structure {
    public static final String ID = "cobblestone_tower";
    private static final int REGION = 128;
    private static final int SIZE = 7;
    private static final int HEIGHT = 10;

    public record Entrance(int x, int y, int z) implements java.io.Serializable {
        public double distanceSquared(double px, double pz) {
            double dx = x - px;
            double dz = z - pz;
            return dx * dx + dz * dz;
        }

        public boolean contains(double px, double feet, double pz) {
            return px >= x - 3 && px < x + 4 && pz >= z + 1 && pz < z + 8 && feet >= y - .1
                    && feet <= y + 8.5;
        }
    }

    public static Entrance residenceAt(Level level, double x, double feet, double z) {
        for (Entrance entrance : level.cobblestoneTowers)
            if (entrance.contains(x, feet, z))
                return entrance;
        return null;
    }

    private interface ReadTile {
        int get(int x, int y, int z);
    }

    private interface WriteTile {
        void set(int x, int y, int z, int id);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean generate(Level level, int x, int y, int z, Random random) {
        if (!level.isInBounds(x, y - 1, z - 1)
                || !level.isInBounds(x + SIZE - 1, y + HEIGHT - 1, z + SIZE - 1)
                || !fits(level::getTile, x, y, z)) {
            return false;
        }
        level.cobblestoneTowers.add(new Entrance(x + 3, y + 1, z - 1));
        build(level::setTileNoUpdate, occupant -> occupant.spawn(level), x, y, z);
        return true;
    }

    public static void placeFinite(Level level, long seed) {
        CobblestoneTower tower = new CobblestoneTower();
        for (int rz = 0; rz * REGION < level.height; rz++) {
            for (int rx = 0; rx * REGION < level.width; rx++) {
                Random random = placementRandom(seed, rx, rz);
                int x = rx * REGION + offset(random, level.width - rx * REGION);
                int z = rz * REGION + offset(random, level.height - rz * REGION);
                if (level.isInBounds(x + SIZE - 1, 1, z + SIZE - 1)) {
                    tower.generate(level, x, level.getHighestTile(x + 3, z + 3) - 1, z, random);
                }
            }
        }
    }

    public static void decorate(SimpleChunk chunk, long seed) {
        int rx = Math.floorDiv(chunk.cx, 8);
        int rz = Math.floorDiv(chunk.cz, 8);
        Random random = placementRandom(seed, rx, rz);
        int x = rx * REGION + offset(random, REGION);
        int z = rz * REGION + offset(random, REGION);
        if ((x >> 4) != chunk.cx || (z >> 4) != chunk.cz) {
            return;
        }
        int lx = x & 15;
        int lz = z & 15;
        ReadTile read = (a, b, c) -> chunk.blocks[SimpleChunk.idx(a, b, c, chunk.height)] & 255;
        int y = chunk.height - 1;
        while (y > 0 && read.get(lx + 3, y, lz + 3) == 0) {
            y--;
        }
        if (y < 1 || y + HEIGHT > chunk.height || !fits(read, lx, y, lz)) {
            return;
        }
        build(
                (a, b, c, id) ->
                        chunk.blocks[SimpleChunk.idx(a & 15, b, c & 15, chunk.height)] = (byte) id,
                chunk.generatedOccupants::add,
                x,
                y,
                z);
        chunk.cobblestoneTower = new Entrance(x + 3, y + 1, z - 1);
    }

    private static Random placementRandom(long seed, int rx, int rz) {
        return new Random(seed ^ rx * 341873128712L ^ rz * 132897987541L ^ 0x544F574552L);
    }

    private static int offset(Random random, int span) {
        return random.nextInt(Math.max(1, span / 16)) * 16 + 4;
    }

    private static boolean fits(ReadTile tile, int x, int y, int z) {
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int ground = tile.get(x + dx, y, z + dz);
                if (ground != Block.GRASS.id
                        && ground != Block.DIRT.id
                        && ground != Block.STONE.id
                        && ground != Block.SAND.id) {
                    return false;
                }
                for (int dy = 1; dy < HEIGHT; dy++) {
                    if (tile.get(x + dx, y + dy, z + dz) != 0) {
                        return false;
                    }
                }
            }
        }
        return tile.get(x + 3, y + 1, z - 1) == 0 && tile.get(x + 3, y + 2, z - 1) == 0;
    }

    private static void build(
            WriteTile tile, Consumer<GeneratedOccupant> occupants, int x, int y, int z) {
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dz = 0; dz < SIZE; dz++) {
                for (int dx = 0; dx < SIZE; dx++) {
                    boolean edge = dx == 0 || dz == 0 || dx == SIZE - 1 || dz == SIZE - 1;
                    boolean door = dz == 0 && dx == 3 && (dy == 1 || dy == 2);
                    boolean hatch = dx == 1 && dz == 3;
                    if (dy == 0
                            || (dy == 4 || dy == 8) && !hatch
                            || dy > 0 && dy < 8 && edge && !door
                            || dy == 9 && edge && (dx + dz) % 2 == 0) {
                        tile.set(x + dx, y + dy, z + dz, Block.COBBLESTONE.id);
                    }
                }
            }
        }
        for (int step = 1; step <= 8; step++) {
            tile.set(x + 1, y + step, z + 3, Block.LADDER.id);
        }
        occupants.accept(
                new GeneratedOccupant(GeneratedOccupant.Kind.ZOMBIE_BUILDER, x + 3, y + 1, z + 3));
        occupants.accept(
                new GeneratedOccupant(GeneratedOccupant.Kind.ZOMBIE_BUILDER, x + 3, y + 5, z + 3));
    }

    public static void save(Level level, CompoundTag root) {
        ListTag sites = new ListTag("CobblestoneTowers", (byte) 10);
        for (Entrance entrance : level.cobblestoneTowers) {
            CompoundTag site = new CompoundTag(null);
            site.putInt("X", entrance.x());
            site.putInt("Y", entrance.y());
            site.putInt("Z", entrance.z());
            sites.add(site);
        }
        root.put(sites);
    }

    public static void load(Level level, CompoundTag root) {
        ListTag sites = root.getList("CobblestoneTowers");
        for (int i = 0; i < sites.size(); i++) {
            CompoundTag site = (CompoundTag) sites.get(i);
            level.cobblestoneTowers.add(
                    new Entrance(site.getInt("X"), site.getInt("Y"), site.getInt("Z")));
        }
    }
}
