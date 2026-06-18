package net.classicremastered.minecraft.level;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.ProgressBarDisplay;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.infinite.SimpleChunkManager;
import net.classicremastered.nbt.*;

/**
 * Unified level serializer:
 *  v2 = Classic finite worlds (ObjectOutputStream(Level))
 *  v3 = Infinite flat worlds (seed + depth + modified chunks)
 *  v4 = Infinite terrain worlds (seed + depth + modified chunks)
 */
public final class LevelIO {

    private static final int MAGIC = 656127880;
    private static final int VERSION_CLASSIC  = 2;
    private static final int VERSION_INFINITE = 3; // flat
    private static final int VERSION_TERRAIN  = 4; // terrain
    private static final int VERSION_CLASSIC_NBT  = 5;
    private static final int VERSION_INFINITE_NBT = 6;

    private final ProgressBarDisplay progressBar;

    public LevelIO(ProgressBarDisplay pb) {
        this.progressBar = pb;
    }

    // ----------------------------------------------------------------------
    // SAVE
    // ----------------------------------------------------------------------
    public final boolean save(Level level, File file) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            File tmp = new File(file.getPath() + ".tmp");

            try (FileOutputStream fos = new FileOutputStream(tmp);
                 GZIPOutputStream gos = new GZIPOutputStream(fos);
                 DataOutputStream out = new DataOutputStream(gos)) {

                if (level instanceof LevelInfiniteFlat || level instanceof LevelInfiniteTerrain) {
                    saveInfiniteNBT(level, out);
                } else {
                    saveClassicNBT(level, out);
                }
            }

            // atomic replace
            if (!tmp.renameTo(file)) {
                if (file.exists() && !file.delete())
                    throw new IOException("Failed to delete " + file);
                if (!tmp.renameTo(file))
                    throw new IOException("Failed to rename " + tmp + " -> " + file);
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            if (progressBar != null) progressBar.setText("Failed!");
            return false;
        }
    }

    private void saveClassic(Level level, OutputStream out) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(level);
        oos.flush();
    }

    private void saveInfinite(LevelInfiniteFlat inf, DataOutputStream out) throws IOException {
        out.writeLong(inf.randomSeed);
        out.writeInt(inf.depth);

        // --- chunks ---
        Map<Long, SimpleChunk> map = inf.chunks().getAllChunks();
        out.writeInt(map.size());
        for (Map.Entry<Long, SimpleChunk> e : map.entrySet()) {
            out.writeLong(e.getKey());
            out.write(e.getValue().blocks);
        }

        // --- world metadata ---
        out.writeInt(inf.timeOfDay);
        out.writeBoolean(inf.doDayNightCycle);

        // --- player ---
        Entity savePlayer = inf.player;
        if (savePlayer == null && inf.minecraft != null) {
            savePlayer = inf.minecraft.player;
        }
        if (savePlayer != null) {
            out.writeFloat(savePlayer.x);
            out.writeFloat(savePlayer.y);
            out.writeFloat(savePlayer.z);
            out.writeFloat(savePlayer.yRot);
            out.writeFloat(savePlayer.xRot);
        } else {
            out.writeFloat(0f); out.writeFloat(0f); out.writeFloat(0f);
            out.writeFloat(0f); out.writeFloat(0f);
        }

        // --- entities ---
        var all = inf.blockMap != null ? inf.blockMap.all : java.util.Collections.emptyList();
        int nEnt = 0;
        for (Object e : all) {
            if (e instanceof net.classicremastered.minecraft.mob.Mob m) {
                if (m.removed || m.health <= 0) continue;
                nEnt++;
            }
        }
        out.writeInt(nEnt);
        for (Object e : all) {
            if (!(e instanceof net.classicremastered.minecraft.mob.Mob m)) continue;
            if (m.removed || m.health <= 0) continue;
            short id = net.classicremastered.minecraft.mob.MobRegistry.idOf(m);
            out.writeShort(id);
            out.writeFloat(m.x);
            out.writeFloat(m.y);
            out.writeFloat(m.z);
            out.writeFloat(m.yRot);
            out.writeFloat(m.xRot);
            out.writeInt(m.health);
        }
    }

    private void saveInfinite(LevelInfiniteTerrain inf, DataOutputStream out) throws IOException {
        out.writeLong(inf.randomSeed);
        out.writeInt(inf.depth);

        // --- chunks ---
        Map<Long, SimpleChunk> map = inf.chunks().getAllChunks();
        out.writeInt(map.size());
        for (Map.Entry<Long, SimpleChunk> e : map.entrySet()) {
            out.writeLong(e.getKey());
            out.write(e.getValue().blocks);
        }

        // --- world metadata ---
        out.writeInt(inf.timeOfDay);
        out.writeBoolean(inf.doDayNightCycle);

        // --- player ---
        Entity savePlayer = inf.player;
        if (savePlayer == null && inf.minecraft != null) {
            savePlayer = inf.minecraft.player;
        }
        if (savePlayer != null) {
            out.writeFloat(savePlayer.x);
            out.writeFloat(savePlayer.y);
            out.writeFloat(savePlayer.z);
            out.writeFloat(savePlayer.yRot);
            out.writeFloat(savePlayer.xRot);
        } else {
            out.writeFloat(0f); out.writeFloat(0f); out.writeFloat(0f);
            out.writeFloat(0f); out.writeFloat(0f);
        }

        // --- entities ---
        var all = inf.blockMap != null ? inf.blockMap.all : java.util.Collections.emptyList();
        int nEnt = 0;
        for (Object e : all) {
            if (e instanceof net.classicremastered.minecraft.mob.Mob m) {
                if (m.removed || m.health <= 0) continue;
                nEnt++;
            }
        }
        out.writeInt(nEnt);
        for (Object e : all) {
            if (!(e instanceof net.classicremastered.minecraft.mob.Mob m)) continue;
            if (m.removed || m.health <= 0) continue;
            short id = net.classicremastered.minecraft.mob.MobRegistry.idOf(m);
            out.writeShort(id);
            out.writeFloat(m.x);
            out.writeFloat(m.y);
            out.writeFloat(m.z);
            out.writeFloat(m.yRot);
            out.writeFloat(m.xRot);
            out.writeInt(m.health);
        }
    }

    // ----------------------------------------------------------------------
    // LOAD
    // ----------------------------------------------------------------------
    public final Level load(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return load(fis);
        } catch (Exception ex) {
            ex.printStackTrace();
            if (progressBar != null) progressBar.setText("Failed!");
            return null;
        }
    }

    public final Level load(InputStream inRaw) {
        if (progressBar != null) {
            progressBar.setTitle("Loading level");
            progressBar.setText("Reading..");
        }

        try (GZIPInputStream gis = new GZIPInputStream(inRaw);
             PushbackInputStream pbis = new PushbackInputStream(gis, 1);
             DataInputStream in = new DataInputStream(pbis)) {

            int firstByte = pbis.read();
            if (firstByte == -1) {
                throw new EOFException("Empty stream");
            }
            if (firstByte == 0x0A) {
                pbis.unread(firstByte);
                CompoundTag root = net.classicremastered.nbt.NBTIO.read(in);
                String name = root.getName();
                if ("ClassicLevel".equals(name)) {
                    return loadClassicNBT(root);
                } else if ("InfiniteLevel".equals(name)) {
                    return loadInfiniteNBT(root);
                } else {
                    throw new IOException("Unknown root tag name: " + name);
                }
            } else {
                pbis.unread(firstByte);
                int magic = in.readInt();
                if (magic != MAGIC) throw new IOException("Bad magic " + magic);

                int ver = in.readByte() & 0xFF;
                switch (ver) {
                    case VERSION_CLASSIC:      return loadClassic(gis);
                    case VERSION_INFINITE:     return loadInfiniteFlat(in);
                    case VERSION_TERRAIN:      return loadInfiniteTerrain(in);
                    case VERSION_CLASSIC_NBT:  {
                        CompoundTag root = net.classicremastered.nbt.NBTIO.read(in);
                        return loadClassicNBT(root);
                    }
                    case VERSION_INFINITE_NBT: {
                        CompoundTag root = net.classicremastered.nbt.NBTIO.read(in);
                        return loadInfiniteNBT(root);
                    }
                    default: throw new IOException("Unsupported version: " + ver);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private Level loadClassic(InputStream in) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(in);
        Level L = (Level) ois.readObject();
        L.initTransient();
        return L;
    }

    private Level loadInfiniteFlat(DataInputStream in) throws IOException {
        long seed = in.readLong();
        int depth = in.readInt();
        LevelInfiniteFlat inf = new LevelInfiniteFlat(seed, depth);

        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            long key = in.readLong();
            byte[] blocks = new byte[depth * 16 * 16];
            in.readFully(blocks);
            inf.chunks().restoreChunk(key, blocks);
        }
        return inf;
    }

    private Level loadInfiniteTerrain(DataInputStream in) throws IOException {
        long seed = in.readLong();
        int depth = in.readInt();
        LevelInfiniteTerrain inf = new LevelInfiniteTerrain(seed, depth);

        // --- chunks ---
     // --- chunks ---
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            long key = in.readLong();
            byte[] blocks = new byte[depth * 16 * 16];
            in.readFully(blocks);
            inf.chunks().restoreChunk(key, blocks);
        }

        // ✅ make sure BlockMap exists before adding entities
        inf.blockMap = new net.classicremastered.minecraft.level.BlockMap(inf.width, inf.depth, inf.height);
        inf.blockMap.infiniteMode = true;
        for (Entity e : inf.blockMap.all) {
            e.blockMap = inf.blockMap;
        }

        // --- metadata ---
        try {
            inf.timeOfDay = in.readInt();
            inf.doDayNightCycle = in.readBoolean();

            float px = in.readFloat();
            float py = in.readFloat();
            float pz = in.readFloat();
            float yaw = in.readFloat();
            float pitch = in.readFloat();

            if (inf.player == null) {
                net.classicremastered.minecraft.player.Player loadedPlayer = new net.classicremastered.minecraft.player.Player(null);
                loadedPlayer.level = inf;
                inf.player = loadedPlayer;
                loadedPlayer.moveTo(px, py, pz, yaw, pitch);
                inf.addEntity(loadedPlayer);
            } else {
                inf.player.moveTo(px, py, pz, yaw, pitch);
            }

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                short id = in.readShort();
                float x = in.readFloat();
                float y = in.readFloat();
                float z = in.readFloat();
                float yRot = in.readFloat();
                float xRot = in.readFloat();
                int health = in.readInt();
                var mob = net.classicremastered.minecraft.mob.MobRegistry.create(id, inf, x, y, z);
                if (mob == null) continue;
                mob.yRot = yRot;
                mob.xRot = xRot;
                mob.health = health;
                inf.addEntity(mob);
            }
        } catch (EOFException ignored) {
            // older saves
        }

        inf.updateDayNightColorsSmooth();

        return inf;
    }

    // ----------------------------------------------------------------------
    // Online stubs (compatibility with old Classic client code)
    // ----------------------------------------------------------------------
    public Level loadOnline(String host, String user, int id) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL("http://" + host + "/level/load.html?id=" + id + "&user=" + user).openConnection();
            try (InputStream in = conn.getInputStream()) {
                return load(in);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public void saveOnline(Level level, String host, String username, String sessionId, String name, int id) {
        // stub: not implemented
        System.out.println("[LevelIO] saveOnline() called (stub).");
    }

    // ----------------------------------------------------------------------
    // Helper: decompress gzip stream with length prefix (used by netcode)
    // ----------------------------------------------------------------------
    public static byte[] decompress(InputStream in0) {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(in0))) {
            int len = in.readInt();
            byte[] arr = new byte[len];
            in.readFully(arr);
            return arr;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void saveClassicNBT(Level level, DataOutputStream out) throws IOException {
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

        CompoundTag player = new CompoundTag("Player");
        player.putString("id", "Player");
        Entity savePlayer = level.player;
        if (savePlayer == null && level.minecraft != null) {
            savePlayer = level.minecraft.player;
        }
        if (savePlayer != null) {
            ListTag pos = new ListTag("Pos", (byte) 5); // TAG_Float
            pos.add(new FloatTag(null, savePlayer.x));
            pos.add(new FloatTag(null, savePlayer.y));
            pos.add(new FloatTag(null, savePlayer.z));
            player.put(pos);

            ListTag rot = new ListTag("Rotation", (byte) 5); // TAG_Float
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
        root.put(player);

        ListTag entities = new ListTag("Entities", (byte) 10); // TAG_Compound
        var all = level.blockMap != null ? level.blockMap.all : java.util.Collections.emptyList();
        for (Object e : all) {
            if (!(e instanceof net.classicremastered.minecraft.mob.Mob m)) continue;
            if (m instanceof net.classicremastered.minecraft.player.Player) continue;
            if (m.removed || m.health <= 0) continue;

            try {
                java.lang.reflect.Field f = m.getClass().getField("persistent");
                if (f.getType() == boolean.class && !f.getBoolean(m))
                    continue;
            } catch (Throwable ignored) {}

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

    private void saveInfiniteNBT(Level level, DataOutputStream out) throws IOException {
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

    private Level loadClassicNBT(CompoundTag root) throws IOException {
        Level level = new Level();

        level.name = root.getString("Name");
        level.creator = root.getString("Creator");
        level.createTime = root.getLong("CreateTime");

        CompoundTag map = root.getCompound("Map");
        level.width = map.getShort("Width");
        level.depth = map.getShort("Height");
        level.height = map.getShort("Length");
        level.blocks = map.getByteArray("Blocks");
        level.waterLevel = map.getShort("WaterLevel");
        level.creativeMode = map.getBoolean("Creative");
        level.skyColor = map.getInt("SkyColor");
        level.fogColor = map.getInt("FogColor");
        level.cloudColor = map.getInt("CloudColor");
        level.timeOfDay = map.getInt("TimeOfDay");
        level.doDayNightCycle = map.getBoolean("DoDayNightCycle");

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

            level.pendingEntities.add(new Level.SavedMob(id, mx, my, mz, myRot, mxRot, health));
        }

        level.initTransient();

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
        level.updateDayNightColorsSmooth();

        return level;
    }

    private Level loadInfiniteNBT(CompoundTag root) throws IOException {
        String type = root.getString("TerrainType");
        long seed = root.getLong("Seed");
        int depth = root.getInt("Depth");

        Level level;
        if ("Flat".equalsIgnoreCase(type)) {
            level = new LevelInfiniteFlat(seed, depth);
        } else {
            level = new LevelInfiniteTerrain(seed, depth);
        }

        level.timeOfDay = root.getInt("TimeOfDay");
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
