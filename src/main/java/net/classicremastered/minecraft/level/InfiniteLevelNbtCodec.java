package net.classicremastered.minecraft.level;

import java.io.*;
import java.util.Map;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.*;

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
            seed = terrain.randomSeed;
            depthY = terrain.depth;
            map = terrain.chunks().getAllChunks();
        }

        root.putLong("Seed", seed);
        root.putInt("Depth", depthY);
        root.putInt("TimeOfDay", level.timeOfDay);
        root.putByte("DoDayNightCycle", (byte) (level.doDayNightCycle ? 1 : 0));

        CompoundTag player = new CompoundTag("Player");
        player.putString("id", "Player");
        Entity savePlayer = level.player;
        if (savePlayer == null && level.minecraft != null) {
            savePlayer = level.minecraft.player;
        }
        if (savePlayer != null) {
            ListTag pos = new ListTag("Pos", (byte) 5);
            pos.add(new FloatTag(null, savePlayer.x));
            pos.add(new FloatTag(null, savePlayer.y));
            pos.add(new FloatTag(null, savePlayer.z));
            player.put(pos);

            ListTag rot = new ListTag("Rotation", (byte) 5);
            rot.add(new FloatTag(null, savePlayer.yRot));
            rot.add(new FloatTag(null, savePlayer.xRot));
            player.put(rot);
        } else {
            ListTag pos = new ListTag("Pos", (byte) 5);
            pos.add(new FloatTag(null, 0f));
            pos.add(new FloatTag(null, 0f));
            pos.add(new FloatTag(null, 0f));
            player.put(pos);

            ListTag rot = new ListTag("Rotation", (byte) 5);
            rot.add(new FloatTag(null, 0f));
            rot.add(new FloatTag(null, 0f));
            rot.add(new FloatTag(null, 0f));
            player.put(rot);
        }
        PlayerNbtCodec.save(player, savePlayer);
        root.put(player);

        ListTag chunks = new ListTag("Chunks", (byte) 10);
        if (map != null) {
            for (Map.Entry<Long, SimpleChunk> e : map.entrySet()) {
                CompoundTag chunkTag = new CompoundTag(null);
                chunkTag.putLong("Key", e.getKey());
                chunkTag.putByteArray("Blocks", e.getValue().blocks);
                chunks.add(chunkTag);
            }
        }
        root.put(chunks);

        ListTag entities = new ListTag("Entities", (byte) 10);
        var all = level.blockMap != null ? level.blockMap.all : java.util.Collections.emptyList();
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
            entities.add(mobTag);
        }
        root.put(entities);

        net.classicremastered.nbt.NBTIO.write(root, out);
    }

    static Level load(CompoundTag root) throws IOException {
        String type = root.getString("TerrainType");
        long seed = root.getLong("Seed");
        int depth = root.getInt("Depth");

        Level level;
        if ("Flat".equalsIgnoreCase(type)) {
            level = new LevelInfiniteFlat(seed, depth);
        } else {
            level = new LevelInfiniteTerrain(seed, depth);
        }

        level.setTime(root.getInt("TimeOfDay"));
        level.doDayNightCycle = root.getBoolean("DoDayNightCycle");

        ListTag chunks = root.getList("Chunks");
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
            }
        }

        level.blockMap = new net.classicremastered.minecraft.level.BlockMap(level.width, level.depth, level.height);
        level.blockMap.infiniteMode = true;
        for (Entity e : level.blockMap.all) {
            e.blockMap = level.blockMap;
        }

        CompoundTag player = root.getCompound("Player");
        if (player.hasKey("Pos")) {
            ListTag pos = player.getList("Pos");
            ListTag rot = player.getList("Rotation");
            float px = 0f, py = 0f, pz = 0f;
            if (pos.size() >= 3) {
                px = ((FloatTag) pos.get(0)).value;
                py = ((FloatTag) pos.get(1)).value;
                pz = ((FloatTag) pos.get(2)).value;
            }
            float yaw = 0f, pitch = 0f;
            if (rot.size() >= 2) {
                yaw = ((FloatTag) rot.get(0)).value;
                pitch = ((FloatTag) rot.get(1)).value;
            }

            if (level.player == null) {
                net.classicremastered.minecraft.player.Player loadedPlayer = new net.classicremastered.minecraft.player.Player(null);
                loadedPlayer.level = level;
                level.player = loadedPlayer;
                loadedPlayer.moveTo(px, py, pz, yaw, pitch);
                level.addEntity(loadedPlayer);
            } else {
                level.player.moveTo(px, py, pz, yaw, pitch);
            }
        }

        PlayerNbtCodec.load(player, level.player);
        ListTag entities = root.getList("Entities");
        net.classicremastered.minecraft.mob.MobRegistry.bootstrapDefaults();
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
            if (mob != null) {
                mob.yRot = myRot;
                mob.xRot = mxRot;
                mob.health = health;
                level.addEntity(mob);
            }
        }
        level.updateDayNightColorsSmooth();

        return level;
    }
}
