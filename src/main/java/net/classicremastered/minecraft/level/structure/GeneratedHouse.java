package net.classicremastered.minecraft.level.structure;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

/** The center and floor of a generated seven-by-seven house. */
public record GeneratedHouse(int x, int floor, int z) implements java.io.Serializable {
    private boolean contains(int bx, int by, int bz) {
        return Math.abs(bx - x) <= 3 && Math.abs(bz - z) <= 3 && by > floor && by < floor + 4;
    }

    public static boolean protects(Level level, int x, int y, int z) {
        for (var tower : level.cobblestoneTowers) {
            if (Math.abs(x - tower.x()) <= 3 && z > tower.z() && z <= tower.z() + 7 && y >= tower.y()
                    && y < tower.y() + 8)
                return true;
        }
        for (GeneratedHouse house : level.generatedHouses) {
            if (house.contains(x, y, z))
                return true;
        }
        for (int floor = y - 3; floor < y; floor++) {
            for (int cx = x - 3; cx <= x + 3; cx++) {
                for (int cz = z - 3; cz <= z + 3; cz++) {
                    if (legacyHouse(level, cx, floor, cz))
                        return true;
                }
            }
        }
        return false;
    }

    private static boolean legacyHouse(Level level, int x, int y, int z) {
        if (!loaded(level, x - 3, y, z - 3) || !loaded(level, x + 3, y + 4, z + 3)
                || !loaded(level, x - 3, y, z + 3) || !loaded(level, x + 3, y, z - 3)
                || level.getTile(x, y, z) != Block.COBBLESTONE.id
                || level.getTile(x, y + 4, z) != Block.WOOD.id || level.getTile(x, y + 1, z - 3) != 0
                || level.getTile(x, y + 2, z - 3) != 0)
            return false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (level.getTile(x + dx, y, z + dz) != Block.COBBLESTONE.id
                        || level.getTile(x + dx, y + 4, z + dz) != Block.WOOD.id)
                    return false;
                if ((Math.abs(dx) == 3 || Math.abs(dz) == 3) && (dx != 0 || dz != -3)
                        && level.getTile(x + dx, y + 1, z + dz) != Block.WOOD.id)
                    return false;
            }
        }
        return true;
    }

    private static boolean loaded(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y, z))
            return false;
        if (level instanceof LevelInfiniteTerrain t)
            return t.chunks().isChunkLoaded(x >> 4, z >> 4);
        if (level instanceof LevelInfiniteFlat t)
            return t.chunks().isChunkLoaded(x >> 4, z >> 4);
        return true;
    }

    public static void save(Level level, CompoundTag root) {
        ListTag houses = new ListTag("GeneratedHouses", (byte) 10);
        for (GeneratedHouse house : level.generatedHouses) {
            CompoundTag tag = new CompoundTag(null);
            tag.putInt("X", house.x);
            tag.putInt("Y", house.floor);
            tag.putInt("Z", house.z);
            houses.add(tag);
        }
        root.put(houses);
    }

    public static void load(Level level, CompoundTag root) {
        ListTag houses = root.getList("GeneratedHouses");
        for (int i = 0; i < houses.size(); i++) {
            CompoundTag tag = (CompoundTag) houses.get(i);
            int x = tag.getInt("X"), y = tag.getInt("Y"), z = tag.getInt("Z");
            if (level.isInBounds(x, y, z))
                level.generatedHouses.add(new GeneratedHouse(x, y, z));
        }
    }
}
