package net.classicremastered.minecraft.level;

import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.BabyVillager;
import net.classicremastered.minecraft.mob.Bee;
import net.classicremastered.minecraft.mob.Ironmock;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

/** One generation-time spawn attempt, owned by the chunk containing its anchor. */
public record GeneratedOccupant(Kind kind, int x, int y, int z) {
    public enum Kind {
        BEE,
        BABY_VILLAGER,
        IRONMOCK,
        ZOMBIE_BUILDER, ADULT_VILLAGER
    }

    private static final int[][] TRUNK_SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    public static void offer(List<GeneratedOccupant> pending, long seed, boolean bee, int x, int y, int z) {
        Random random = new Random(
                seed ^ x * 341873128712L ^ z * 132897987541L ^ (bee ? 0xBEE71L : 0xBAB715L));
        if (random.nextInt(bee ? 100 : 50) == 0)
            pending.add(new GeneratedOccupant(bee ? Kind.BEE : Kind.BABY_VILLAGER, x, y, z));
    }

    public static void offerHouse(List<GeneratedOccupant> pending, long seed, int x, int y, int z) {
        pending.add(new GeneratedOccupant(Kind.ADULT_VILLAGER, x, y, z));
        offer(pending, seed, false, x, y, z);
        int last = pending.size() - 1;
        if (pending.get(last).kind == Kind.BABY_VILLAGER) {
            int babyX = (x & 15) == 15 ? x - 1 : x + 1;
            pending.set(last, new GeneratedOccupant(Kind.BABY_VILLAGER, babyX, y, z));
        }
    }

    public static void offerIronmock(List<GeneratedOccupant> pending, long seed, int x, int y,
            int z) {
        SplittableRandom random = new SplittableRandom(
                seed ^ x * 341873128712L ^ z * 132897987541L ^ 0x170A0C4L);
        if (random.nextInt(4) == 0)
            pending.add(new GeneratedOccupant(Kind.IRONMOCK, x, y, z));
    }

    public void spawn(Level level) {
        if (level.isNetworkMode() || !level.isInBounds(x, y, z))
            return;
        if (kind == Kind.BEE) {
            spawnBee(level);
            return;
        }
        if (kind == Kind.ZOMBIE_BUILDER) {
            spawnZombieBuilder(level);
            return;
        }
        if (kind == Kind.IRONMOCK) {
            spawnIronmock(level);
            return;
        }
        if (kind == Kind.ADULT_VILLAGER) {
            spawnAdult(level);
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

    private void spawnAdult(Level level) {
        level.generatedHouses
                .add(new net.classicremastered.minecraft.level.structure.GeneratedHouse(x, y - 1, z));
        for (int dz : new int[] { 0, -1, 1 }) {
            int sz = z + dz;
            if ((sz >> 4) != (z >> 4) || !clear(level, x, y, sz)
                    || level.getTile(x, y - 1, sz) != Block.COBBLESTONE.id)
                continue;
            float sx = x + .5f;
            var villager = new net.classicremastered.minecraft.mob.Villager(level, sx, y + 1, sz + .5f);
            villager.setPos(sx, y + villager.bbHeight * .5f + .01f, sz + .5f);
            if (!level.isFree(villager.bb) || !level.getCubes(villager.bb).isEmpty())
                continue;
            villager.persistent = true;
            villager.setHome(sx, y + villager.heightOffset, z + .5f, 2.5f);
            villager.setHomeDoor(x, y, z - 3);
            level.addEntity(villager);
            return;
        }
    }

    private void spawnZombieBuilder(Level level) {
        if (level.getTile(x, y - 1, z) != Block.COBBLESTONE.id || !clear(level, x, y, z)) {
            return;
        }
        var builder =
                new net.classicremastered.minecraft.mob.ZombieBuilder(level, x + .5f, y, z + .5f);
        builder.setPos(x + .5f, y + builder.bbHeight * .5f + .01f, z + .5f);
        builder.equipment[0] =
                net.classicremastered.minecraft.level.itemstack.Equipment.ARMOR[1][0] + 256;
        builder.equipmentRolled = true;
        builder.persistent = true;
        builder.assignTower(net.classicremastered.minecraft.level.structure.CobblestoneTower
                .residenceAt(level, builder.x, builder.bb.y0, builder.z));
        if (level.isFree(builder.bb) && level.getCubes(builder.bb).isEmpty()) {
            level.addEntity(builder);
        }
    }

    private void spawnIronmock(Level level) {
        if (level.getTile(x, y - 1, z) != Block.SANDSTONE.id || !clear(level, x, y, z))
            return;
        float sx = x + .5f, sz = z + .5f;
        Ironmock mob = new Ironmock(level, sx, y, sz);
        mob.setPos(sx, y + mob.bbHeight * .5f + .01f, sz);
        mob.persistent = true;
        if (level.isFree(mob.bb) && level.getCubes(mob.bb).isEmpty())
            level.addEntity(mob);
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
            if (occupant.kind == Kind.ZOMBIE_BUILDER) tag.putBoolean("ZombieBuilder", true);
            else if (occupant.kind == Kind.ADULT_VILLAGER)
                tag.putBoolean("AdultVillager", true);
            else if (occupant.kind == Kind.IRONMOCK) tag.putBoolean("Ironmock", true);
            else tag.putBoolean("Bee", occupant.kind == Kind.BEE);
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
                pending.add(
                        new GeneratedOccupant(
                                tag.getBoolean("AdultVillager") ? Kind.ADULT_VILLAGER
                                        : tag.getBoolean("ZombieBuilder") ? Kind.ZOMBIE_BUILDER
                                                : tag.getBoolean("Ironmock")
                                                        ? Kind.IRONMOCK
                                                : tag.getBoolean("Bee")
                                                        ? Kind.BEE
                                                        : Kind.BABY_VILLAGER,
                                x,
                                y,
                                z));
        }
    }
}
