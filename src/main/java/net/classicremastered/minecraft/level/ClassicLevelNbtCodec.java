package net.classicremastered.minecraft.level;

import net.classicremastered.nbt.*;

import java.io.*;

/** Reads and writes finite world NBT data. */
final class ClassicLevelNbtCodec {
    private ClassicLevelNbtCodec() {}

    static void save(Level level, DataOutputStream out) throws IOException {
        validateMap(level);
        CompoundTag root = new CompoundTag("ClassicLevel");
        root.putByte("FormatVersion", (byte) 1);
        root.putString("Name", level.name);
        root.putString("Creator", level.creator);
        root.putLong("CreateTime", level.createTime);

        CompoundTag map = new CompoundTag("Map");
        map.putShort("Width", (short) level.width);
        map.putShort("Height", (short) level.depth);
        map.putShort("Length", (short) level.height);
        map.putByteArray("Blocks", level.blocks);
        map.putShort("WaterLevel", (short) level.waterLevel);
        map.putByte("Creative", (byte) (level.creativeMode ? 1 : 0));
        map.putInt("SkyColor", level.skyColor);
        map.putInt("FogColor", level.fogColor);
        map.putInt("CloudColor", level.cloudColor);
        map.putInt("TimeOfDay", level.timeOfDay);
        map.putByte("DoDayNightCycle", (byte) (level.doDayNightCycle ? 1 : 0));

        ListTag spawn = new ListTag("Spawn", (byte) 3); // TAG_Int
        spawn.add(new IntTag(null, level.xSpawn));
        spawn.add(new IntTag(null, level.ySpawn));
        spawn.add(new IntTag(null, level.zSpawn));
        map.put(spawn);
        map.putFloat("SpawnRot", level.rotSpawn);
        root.put(map);

        root.put(PlayerNbtCodec.save(level));
        var all = level.blockMap != null ? level.blockMap.all : java.util.Collections.emptyList();

        ListTag entities = new ListTag("Entities", (byte) 10); // TAG_Compound
        for (Object e : all) {
            if (!(e instanceof net.classicremastered.minecraft.mob.Mob m)) continue;
            if (m instanceof net.classicremastered.minecraft.player.Player) continue;
            if (m.removed || m.health <= 0) continue;

            if (!m.isPersistenceRequired()) continue;

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

    static Level load(CompoundTag root) throws IOException {
        Level level = new Level();

        level.name = root.getString("Name");
        level.creator = root.getString("Creator");
        level.createTime = root.getLong("CreateTime");

        CompoundTag map = root.getCompound("Map");
        level.width = Short.toUnsignedInt(map.getShort("Width"));
        level.depth = Short.toUnsignedInt(map.getShort("Height"));
        level.height = Short.toUnsignedInt(map.getShort("Length"));
        level.blocks = map.getByteArray("Blocks");
        validateMap(level);
        level.waterLevel = map.getShort("WaterLevel");
        level.creativeMode = map.getBoolean("Creative");
        level.skyColor = map.getInt("SkyColor");
        level.fogColor = map.getInt("FogColor");
        level.cloudColor = map.getInt("CloudColor");
        level.setTime(map.getInt("TimeOfDay"));
        level.doDayNightCycle = !map.hasKey("DoDayNightCycle") || map.getBoolean("DoDayNightCycle");

        ListTag spawn = map.getList("Spawn");
        if (spawn.size() >= 3) {
            level.xSpawn = ((IntTag) spawn.get(0)).value;
            level.ySpawn = ((IntTag) spawn.get(1)).value;
            level.zSpawn = ((IntTag) spawn.get(2)).value;
        }
        level.rotSpawn = map.getFloat("SpawnRot");

        ListTag entities = root.getList("Entities");
        level.pendingEntities = new java.util.ArrayList<>();
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

            Level.SavedMob saved = new Level.SavedMob(id, mx, my, mz, myRot, mxRot, health);
            saved.extraData = mobTag;
            level.pendingEntities.add(saved);
        }

        level.villagerDoors().load(root);
        RidingLinks ridingLinks = new RidingLinks();
        level.initTransient(ridingLinks);

        CompoundTag player = root.getCompound("Player");
        WorldStateNbtCodec.load(level, root, ridingLinks);
        PlayerNbtCodec.load(level, player);
        ridingLinks.readPassenger(level.player, player);
        ridingLinks.restore();
        level.updateDayNightColorsSmooth();

        net.classicremastered.minecraft.level.structure.CobblestoneTower.load(level, root);
        return level;
    }

    private static void validateMap(Level level) throws IOException {
        if (level.width <= 0
                || level.width > 65535
                || level.depth <= 0
                || level.depth > 65535
                || level.height <= 0
                || level.height > 65535
                || level.blocks == null
                || (long) level.width * level.depth * level.height != level.blocks.length) {
            throw new IOException("Invalid finite world dimensions or block count");
        }
    }
}
