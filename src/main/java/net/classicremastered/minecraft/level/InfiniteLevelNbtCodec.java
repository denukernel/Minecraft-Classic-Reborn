package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.nbt.*;

import java.io.*;
import java.util.Map;
import java.util.function.IntConsumer;

/** Reads and writes infinite terrain and flat world NBT data. */
final class InfiniteLevelNbtCodec {
    private InfiniteLevelNbtCodec() {}

    static void save(Level level, DataOutputStream out) throws IOException {
        CompoundTag root = new CompoundTag("InfiniteLevel");
        root.putByte("FormatVersion", (byte) 1);
        root.putString("TerrainType", level instanceof LevelInfiniteFlat ? "Flat" : "Terrain");

        long seed = 0;
        int depthY = 64;
        Map<Long, SimpleChunk> map = null;

        if (level instanceof LevelInfiniteFlat flat) {
            seed = flat.randomSeed;
            depthY = flat.depth;
            map = flat.chunks().getAllChunks();
        } else if (level instanceof LevelInfiniteTerrain terrain) {
            if (terrain.biomesEnabled) root.putString("TerrainType", "Biomes");
            if (terrain.flatBiomes) root.putBoolean("FlatBiomes", true);
            seed = terrain.randomSeed;
            depthY = terrain.depth;
            map = terrain.chunks().getAllChunks();
        }

        root.putLong("Seed", seed);
        root.putInt("Depth", depthY);
        root.putString("Name", level.name);
        root.putString("Creator", level.creator);
        root.putLong("CreateTime", level.createTime);
        root.putBoolean("Creative", level.creativeMode);
        root.putInt("SpawnX", level.xSpawn);
        root.putInt("SpawnY", level.ySpawn);
        root.putInt("SpawnZ", level.zSpawn);
        root.putFloat("SpawnRot", level.rotSpawn);
        root.putInt("WaterLevel", level.waterLevel);
        root.putInt("TimeOfDay", level.timeOfDay);
        root.putByte("DoDayNightCycle", (byte) (level.doDayNightCycle ? 1 : 0));

        root.put(PlayerNbtCodec.save(level));
        var all = level.blockMap != null ? level.blockMap.all : java.util.Collections.emptyList();

        ListTag chunks = new ListTag("Chunks", (byte) 10);
        if (map != null) {
            for (Map.Entry<Long, SimpleChunk> e : map.entrySet()) {
                CompoundTag chunkTag = new CompoundTag(null);
                chunkTag.putLong("Key", e.getKey());
                chunkTag.putBoolean("Loaded", e.getValue().loaded);
                chunkTag.putByteArray("Blocks", e.getValue().blocks);
                GeneratedOccupant.save(e.getValue().generatedOccupants, chunkTag);
                chunks.add(chunkTag);
            }
        }
        root.put(chunks);

        ListTag entities = new ListTag("Entities", (byte) 10);
        for (Object e : all) {
            if (!(e instanceof net.classicremastered.minecraft.mob.Mob m)) continue;
            if (m instanceof net.classicremastered.minecraft.player.Player) continue;
            if (m.removed || m.health <= 0) continue;

            short id = net.classicremastered.minecraft.mob.MobRegistry.idOf(m);
            if (id < 0) continue;

            CompoundTag mobTag = new CompoundTag(null);
            mobTag.putShort("Id", id);
            mobTag.putString("id", m.getClass().getSimpleName());

            ListTag mPos = new ListTag("Pos", (byte) 5);
            mPos.add(new FloatTag(null, m.x));
            mPos.add(new FloatTag(null, m.y));
            mPos.add(new FloatTag(null, m.z));
            mobTag.put(mPos);

            ListTag mRot = new ListTag("Rotation", (byte) 5);
            mRot.add(new FloatTag(null, m.yRot));
            mRot.add(new FloatTag(null, m.xRot));
            mobTag.put(mRot);

            mobTag.putInt("Health", m.health);
            m.savePersistence(mobTag);
            RidingLinks.save(all, m, mobTag);
            net.classicremastered.minecraft.level.itemstack.Equipment.save(m, mobTag);
            VillagerDoorManager.saveHome(m, mobTag);
            entities.add(mobTag);
        }
        root.put(entities);
        level.villagerDoors().save(root);
        WorldStateNbtCodec.save(level, root);

        net.classicremastered.minecraft.level.structure.CobblestoneTower.save(level, root);
        net.classicremastered.nbt.NBTIO.write(root, out);
    }

    static Level load(CompoundTag root, IntConsumer progress) throws IOException {
        progress.accept(0);
        String type = root.getString("TerrainType");
        long seed = root.getLong("Seed");
        int depth = root.getInt("Depth");
        if (depth <= 0 || depth > Integer.MAX_VALUE / 256) {
            throw new IOException("Invalid infinite world depth: " + depth);
        }
        ListTag chunks = root.getList("Chunks");
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunkTag = (CompoundTag) chunks.get(i);
            if (chunkTag.getByteArray("Blocks").length != depth * 256) {
                throw new IOException("Invalid block count for chunk " + chunkTag.getLong("Key"));
            }
        }

        Level level;
        if ("Flat".equalsIgnoreCase(type)) {
            level = new LevelInfiniteFlat(seed, depth);
        } else {
            level =
                    new LevelInfiniteTerrain(
                            seed,
                            depth,
                            "Biomes".equalsIgnoreCase(type),
                            root.getBoolean("FlatBiomes"),
                            root.getBoolean("OceanBiomes"));
        }

        level.setTime(root.getInt("TimeOfDay"));
        level.doDayNightCycle =
                !root.hasKey("DoDayNightCycle") || root.getBoolean("DoDayNightCycle");
        if (root.hasKey("Name")) level.name = root.getString("Name");
        if (root.hasKey("Creator")) level.creator = root.getString("Creator");
        if (root.hasKey("CreateTime")) level.createTime = root.getLong("CreateTime");
        if (root.hasKey("Creative")) level.creativeMode = root.getBoolean("Creative");
        if (root.hasKey("SpawnX")) level.xSpawn = root.getInt("SpawnX");
        if (root.hasKey("SpawnY")) level.ySpawn = root.getInt("SpawnY");
        if (root.hasKey("SpawnZ")) level.zSpawn = root.getInt("SpawnZ");
        if (root.hasKey("SpawnRot")) level.rotSpawn = root.getFloat("SpawnRot");
        if (root.hasKey("WaterLevel")) level.waterLevel = root.getInt("WaterLevel");

        SimpleChunkManager chunkMgr = null;
        if (level instanceof LevelInfiniteFlat) {
            chunkMgr = ((LevelInfiniteFlat) level).chunks();
        } else if (level instanceof LevelInfiniteTerrain) {
            chunkMgr = ((LevelInfiniteTerrain) level).chunks();
        }

        if (chunkMgr != null) {
            for (int i = 0; i < chunks.size(); i++) {
                CompoundTag chunkTag = (CompoundTag) chunks.get(i);
                long key = chunkTag.getLong("Key");
                byte[] blocks = chunkTag.getByteArray("Blocks");
                chunkMgr.restoreChunk(key, blocks);
                chunkMgr.restoreOccupants(key, chunkTag);
                if (chunkTag.hasKey("Loaded"))
                    chunkMgr.tryGet((int) (key >> 32), (int) key).loaded =
                            chunkTag.getBoolean("Loaded");
                progress.accept((int) ((i + 1L) * 95 / chunks.size()));
            }
        }

        level.blockMap =
                new net.classicremastered.minecraft.level.BlockMap(
                        level.width, level.depth, level.height);
        level.blockMap.infiniteMode = true;
        for (Entity e : level.blockMap.all) {
            e.blockMap = level.blockMap;
        }

        CompoundTag player = root.getCompound("Player");
        ListTag entities = root.getList("Entities");
        level.villagerDoors().load(root);
        net.classicremastered.minecraft.mob.MobRegistry.bootstrapDefaults();
        var ridingLinks = new RidingLinks();
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag mobTag = (CompoundTag) entities.get(i);
            short id = mobTag.getShort("Id");
            if (mobTag.hasKey("id")) {
                String name = mobTag.getString("id");
                short resolved = net.classicremastered.minecraft.mob.MobRegistry.idOfName(name);
                if (resolved >= 0) {
                    id = resolved;
                }
            }
            ListTag mPos = mobTag.getList("Pos");
            ListTag mRot = mobTag.getList("Rotation");
            int health = mobTag.getInt("Health");

            float mx = 0f, my = 0f, mz = 0f;
            if (mPos.size() >= 3) {
                mx = ((FloatTag) mPos.get(0)).value;
                my = ((FloatTag) mPos.get(1)).value;
                mz = ((FloatTag) mPos.get(2)).value;
            }
            float myRot = 0f, mxRot = 0f;
            if (mRot.size() >= 2) {
                myRot = ((FloatTag) mRot.get(0)).value;
                mxRot = ((FloatTag) mRot.get(1)).value;
            }

            var mob = net.classicremastered.minecraft.mob.MobRegistry.create(id, level, mx, my, mz);
            if (mob != null && mob.getRegistryId() == id) {
                net.classicremastered.minecraft.level.itemstack.Equipment.load(mob, mobTag);
                VillagerDoorManager.loadHome(mob, mobTag);
                mob.moveTo(mx, my, mz, myRot, mxRot);
                mob.health = mob.lastHealth = health;
                mob.loadPersistence(mobTag);
                level.addEntity(mob);
                ridingLinks.read(mob, mobTag);
            }
        }
        WorldStateNbtCodec.load(level, root, ridingLinks);
        PlayerNbtCodec.load(level, player);
        ridingLinks.readPassenger(level.player, player);
        ridingLinks.restore();
        level.updateDayNightColorsSmooth();

        net.classicremastered.minecraft.level.structure.CobblestoneTower.load(level, root);
        progress.accept(100);
        return level;
    }
}
