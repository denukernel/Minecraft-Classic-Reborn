package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.BoatEntity;
import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.entity.FallingBlockEntity;
import net.classicremastered.minecraft.entity.PrimedTnt;
import net.classicremastered.minecraft.entity.SignEntity;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

import java.io.IOException;
import java.util.ArrayList;

/** World state shared by finite and infinite saves. */
final class WorldStateNbtCodec {
    private WorldStateNbtCodec() {}

    static void save(Level level, CompoundTag root) {
        root.putInt("GameMode", level.spectatorMode ? 3 : level.creativeMode ? 1 : 0);
        level.saveBlockState(root);
        net.classicremastered.minecraft.level.structure.GeneratedHouse.save(level, root);
        level.redstone().save(root);
        if (level.biomeSeed != null) root.putLong("BiomeSeed", level.biomeSeed);
        if (level.flatBiomes) root.putBoolean("FlatBiomes", true);
        root.putBoolean("DoMobSpawning", level.doMobSpawning);
        root.putBoolean("DoMobLoot", level.doMobLoot);
        root.putBoolean("KeepInventory", level.keepInventory);
        ListTag drops = new ListTag("DroppedEntities", (byte) 10);
        ListTag moving = new ListTag("MovingBlocks", (byte) 10);
        ListTag signs = new ListTag("Signs", (byte) 10);
        ListTag boats = new ListTag("Boats", (byte) 10);
        if (level.blockMap != null) {
            var entities = new ArrayList<>(level.blockMap.all);
            for (int index = 0; index < entities.size(); index++) {
                Entity entity = entities.get(index);
                if (entity.removed) continue;
                CompoundTag tag = new CompoundTag(null);
                if (entity instanceof BoatEntity) {
                    tag.putInt("MountKey", index);
                    tag.putFloat("Yaw", entity.yRot);
                    saveMotion(entity, tag);
                    tag.putFloat("Y", entity.bb.y0);
                    boats.add(tag);
                    continue;
                }
                if (entity instanceof SignEntity sign) {
                    signs.add(sign.save());
                    continue;
                }
                if (entity instanceof FallingBlockEntity falling) {
                    tag.putInt("Id", falling.blockId);
                    tag.putInt("Age", falling.age);
                    saveMotion(entity, tag);
                    moving.add(tag);
                    continue;
                }
                if (entity instanceof PrimedTnt tnt && tnt.life > 0) {
                    tag.putInt("Id", Block.TNT.id);
                    tag.putInt("Fuse", tnt.life);
                    tag.putBoolean("PlayerIgniter", tnt.hasPlayerIgniter());
                    saveMotion(entity, tag);
                    moving.add(tag);
                    continue;
                }
                int count;
                int life;
                if (entity instanceof DroppedBlock block) {
                    tag.putString("Kind", "Block");
                    tag.putInt("Id", block.getBlockId());
                    count = block.count;
                    life = 6000 - block.getAge();
                } else if (entity instanceof DroppedItem item) {
                    tag.putString("Kind", "Item");
                    tag.putInt("Id", item.itemId);
                    count = item.count;
                    life = item.life;
                    if (item.crate != null) tag.put(item.crate.save());
                } else {
                    continue;
                }
                if (count <= 0 || life <= 0) continue;
                tag.putInt("Count", count);
                tag.putByte("CactusHits", (byte) entity.getCactusStackHits());
                tag.putInt("Life", life);
                saveMotion(entity, tag);
                drops.add(tag);
            }
        }
        root.put(drops);
        root.put(moving);
        root.put(signs);
        root.put(boats);
    }

    private static void saveMotion(Entity entity, CompoundTag tag) {
        tag.putFloat("X", entity.x);
        tag.putFloat("Y", entity.y);
        tag.putFloat("Z", entity.z);
        tag.putFloat("MotionX", entity.xd);
        tag.putFloat("MotionY", entity.yd);
        tag.putFloat("MotionZ", entity.zd);
    }

    static void load(Level level, CompoundTag root, RidingLinks ridingLinks) throws IOException {
        if (root.hasKey("GameMode")) {
            int mode = root.getInt("GameMode");
            level.spectatorMode = mode == 3;
            level.creativeMode = mode == 1;
        }
        level.loadBlockState(root);
        net.classicremastered.minecraft.level.structure.GeneratedHouse.load(level, root);
        if (root.hasKey("BiomeSeed")) level.biomeSeed = root.getLong("BiomeSeed");
        level.flatBiomes = root.getBoolean("FlatBiomes");
        level.redstone().load(root);
        level.doMobSpawning = !root.hasKey("DoMobSpawning") || root.getBoolean("DoMobSpawning");
        level.doMobLoot = !root.hasKey("DoMobLoot") || root.getBoolean("DoMobLoot");
        level.keepInventory = root.getBoolean("KeepInventory");
        ListTag drops = root.getList("DroppedEntities");
        for (int i = 0; i < drops.size(); i++) {
            CompoundTag tag = (CompoundTag) drops.get(i);
            int id = tag.getInt("Id");
            int count = tag.getInt("Count");
            int life = tag.getInt("Life");
            if (id < 0 || id >= 256 || count <= 0 || life <= 0 || life > 6000) {
                throw new IOException("Invalid saved dropped stack");
            }
            float x = tag.getFloat("X"), y = tag.getFloat("Y"), z = tag.getFloat("Z");
            Entity entity;
            switch (tag.getString("Kind")) {
                case "Block":
                    DroppedBlock block = new DroppedBlock(level, x, y, z, id, count);
                    block.setAge(6000 - life);
                    entity = block;
                    break;
                case "Item":
                    DroppedItem item = new DroppedItem(level, x, y, z, id, count);
                    item.life = life;
                    if (id == net.classicremastered.minecraft.level.itemstack.TurretItems.PACKED) {
                        item.crate =
                                net.classicremastered.minecraft.level.itemstack.PackedTurret.load(
                                        tag);
                    }
                    entity = item;
                    break;
                default:
                    throw new IOException("Unknown saved dropped stack type");
            }
            entity.setCactusStackHits(tag.getByte("CactusHits"));
            restoreMotion(level, entity, tag);
        }
        ListTag signs = root.getList("Signs");
        for (int i = 0; i < signs.size(); i++) {
            SignEntity sign = SignEntity.load(level, (CompoundTag) signs.get(i));
            if (sign != null) level.addEntity(sign);
        }
        ListTag boats = root.getList("Boats");
        for (int i = 0; i < boats.size(); i++) {
            CompoundTag tag = (CompoundTag) boats.get(i);
            float x = tag.getFloat("X"), feet = tag.getFloat("Y"), z = tag.getFloat("Z");
            float yaw = tag.getFloat("Yaw");
            if (!Float.isFinite(x)
                    || !Float.isFinite(feet)
                    || !Float.isFinite(z)
                    || !Float.isFinite(yaw)
                    || !Float.isFinite(tag.getFloat("MotionX"))
                    || !Float.isFinite(tag.getFloat("MotionY"))
                    || !Float.isFinite(tag.getFloat("MotionZ"))) continue;
            BoatEntity boat = new BoatEntity(level, x, feet, z, yaw);
            restoreMotion(level, boat, tag);
            ridingLinks.readMount(boat, tag);
        }
        ListTag moving = root.getList("MovingBlocks");
        for (int i = 0; i < moving.size(); i++) {
            CompoundTag tag = (CompoundTag) moving.get(i);
            int id = tag.getInt("Id");
            float x = tag.getFloat("X"), y = tag.getFloat("Y"), z = tag.getFloat("Z");
            if (id == Block.SAND.id || id == Block.GRAVEL.id) {
                int age = tag.getInt("Age");
                if (age < 0 || age >= FallingBlockEntity.MAX_AGE)
                    throw new IOException("Invalid falling block age");
                FallingBlockEntity falling = new FallingBlockEntity(level, x, y, z, id);
                falling.age = age;
                restoreMotion(level, falling, tag);
            } else if (id == Block.TNT.id) {
                int fuse = tag.getInt("Fuse");
                if (fuse <= 0) throw new IOException("Invalid TNT fuse");
                PrimedTnt tnt = new PrimedTnt(level, x, y, z, null, fuse);
                tnt.restorePlayerIgniter(tag.getBoolean("PlayerIgniter"));
                restoreMotion(level, tnt, tag);
            } else {
                throw new IOException("Invalid moving block type");
            }
        }
    }

    private static void restoreMotion(Level level, Entity entity, CompoundTag tag) {
        entity.xd = tag.getFloat("MotionX");
        entity.yd = tag.getFloat("MotionY");
        entity.zd = tag.getFloat("MotionZ");
        level.addEntity(entity);
    }
}
