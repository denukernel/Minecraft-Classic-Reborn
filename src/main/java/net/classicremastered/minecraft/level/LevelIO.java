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
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
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
                    InfiniteLevelNbtCodec.save(level, out);
                } else {
                    ClassicLevelNbtCodec.save(level, out);
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
                    return ClassicLevelNbtCodec.load(root);
                } else if ("InfiniteLevel".equals(name)) {
                    return InfiniteLevelNbtCodec.load(root);
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
                        return ClassicLevelNbtCodec.load(root);
                    }
                    case VERSION_INFINITE_NBT: {
                        CompoundTag root = net.classicremastered.nbt.NBTIO.read(in);
                        return InfiniteLevelNbtCodec.load(root);
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
        L.setTime(L.timeOfDay);
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
            inf.setTime(in.readInt());
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

}
