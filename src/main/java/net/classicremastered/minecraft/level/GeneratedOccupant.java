package net.classicremastered.minecraft.level;

import java.util.List;
import java.util.Random;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.BabyVillager;
import net.classicremastered.minecraft.mob.Bee;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

/** One generation-time spawn attempt, owned by the chunk containing its anchor. */
public record GeneratedOccupant(boolean bee, int x, int y, int z) {
    private static final int[][] TRUNK_SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    public static void offer(List<GeneratedOccupant> pending, long seed, boolean bee, int x, int y, int z) {
        Random random = new Random(
                seed ^ x * 341873128712L ^ z * 132897987541L ^ (bee ? 0xBEE71L : 0xBAB715L));
        if (random.nextInt(bee ? 100 : 50) == 0)
            pending.add(new GeneratedOccupant(bee, x, y, z));
    }

    public void spawn(Level level) {
        if (level.isNetworkMode() || !level.isInBounds(x, y, z))
            return;
        if (bee) {
            spawnBee(level);
            return;
        }
        int feet = y;
        if (level.getTile(x, y - 1, z) != Block.COBBLESTONE.id || !clear(level, x, feet, z))
            return;
        float sx = x + .5f, sz = z + .5f;
        BabyVillager baby = new BabyVillager(level, sx, feet + 1, sz);
        baby.persistent = true;
        baby.setPos(sx, feet + baby.bbHeight * .5f + .01f, sz);
        if (level.isFree(baby.bb) && level.getCubes(baby.bb).isEmpty()) {
            baby.setHome(sx, feet + baby.heightOffset, sz, 2.5f);
            level.addEntity(baby);
        }
    }

    private void spawnBee(Level level) {
        if (level.getTile(x, y, z) != Block.LOG.id)
            return;
        boolean infinite = level instanceof LevelInfiniteTerrain || level instanceof LevelInfiniteFlat;
        for (int[] side : TRUNK_SIDES) {
            int sideX = x + side[0];
            int sideZ = z + side[1];
            // Publishing a resident must not generate a neighboring chunk.
            if (infinite && (sideX >> 4 != x >> 4 || sideZ >> 4 != z >> 4))
                continue;
            if (!clear(level, sideX, y, sideZ))
                continue;
            float sx = sideX + .5f, sz = sideZ + .5f;
            Bee resident = new Bee(level, sx, y, sz);
            resident.persistent = true;
            resident.setPos(sx, y + resident.bbHeight * .5f + .01f, sz);
            if (level.isFree(resident.bb) && level.getCubes(resident.bb).isEmpty()) {
                level.addEntity(resident);
                return;
            }
        }
    }

    private static boolean clear(Level level, int x, int feet, int z) {
        return level.isInBounds(x, feet + 1, z) && level.getTile(x, feet, z) == 0
                && level.getTile(x, feet + 1, z) == 0;
    }

    public static void save(List<GeneratedOccupant> pending, CompoundTag chunk) {
        if (pending.isEmpty())
            return;
        ListTag list = new ListTag("GeneratedOccupants", (byte) 10);
        for (GeneratedOccupant occupant : pending) {
            CompoundTag tag = new CompoundTag(null);
            tag.putBoolean("Bee", occupant.bee);
            tag.putInt("X", occupant.x);
            tag.putInt("Y", occupant.y);
            tag.putInt("Z", occupant.z);
            list.add(tag);
        }
        chunk.put(list);
    }

    public static void load(CompoundTag chunk, List<GeneratedOccupant> pending, int cx, int cz, int depth) {
        ListTag list = chunk.getList("GeneratedOccupants");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = (CompoundTag) list.get(i);
            int x = tag.getInt("X"), y = tag.getInt("Y"), z = tag.getInt("Z");
            if (x >> 4 == cx && z >> 4 == cz && y > 0 && y < depth - 1)
                pending.add(new GeneratedOccupant(tag.getBoolean("Bee"), x, y, z));
        }
    }
}
