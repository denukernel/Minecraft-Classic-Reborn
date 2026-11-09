package net.classicremastered.toolkit.mapedit3d;

import net.classicremastered.minecraft.level.*;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.nbt.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Single-owner editor document with serialized, bounded undo states. */
public final class WorldEditorDocument implements AutoCloseable {
    private final LevelIO io = new LevelIO(null);
    private final Deque<byte[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Level level, simulation;
    private File file;
    private byte[] saved, before;

    public Level level() {
        return level;
    }

    public Level view() {
        return simulation == null ? level : simulation;
    }

    public File file() {
        return file;
    }

    public boolean simulating() {
        return simulation != null;
    }

    public void open(File source) throws IOException {
        validateMobs(source);
        Level loaded = io.load(source);
        if (loaded == null) throw new IOException("Unsupported or damaged world: " + source);
        replace(loaded, source);
    }

    public void replace(Level replacement, File source) throws IOException {
        byte[] baseline = snapshot(replacement);
        close();
        level = replacement;
        file = source;
        saved = source == null ? null : baseline;
        before = null;
        undo.clear();
        redo.clear();
    }

    public boolean dirty() throws IOException {
        return level != null && (saved == null || !Arrays.equals(saved, snapshot(level)));
    }

    public void begin() throws IOException {
        if (simulation != null) throw new IOException("Stop the simulation before editing.");
        if (before == null) before = snapshot(level);
    }

    public void commit() throws IOException {
        if (before == null) return;
        byte[] previous = before;
        before = null;
        if (!Arrays.equals(previous, snapshot(level))) {
            undo.addLast(previous);
            redo.clear();
            trim(undo);
        }
    }

    public void undo() throws IOException {
        restore(undo, redo);
    }

    public void redo() throws IOException {
        restore(redo, undo);
    }

    private void restore(Deque<byte[]> from, Deque<byte[]> to) throws IOException {
        if (simulating() || from.isEmpty()) return;
        commit();
        Level replacement = decode(from.peekLast());
        to.addLast(snapshot(level));
        shutdown(level);
        level = replacement;
        from.removeLast();
        trim(to);
    }

    public void save(File destination) throws IOException {
        commit();
        if (!io.save(level, destination)) throw new IOException("Could not save " + destination);
        file = destination;
        saved = snapshot(level);
    }

    public void startSimulation() throws IOException {
        commit();
        if (simulation == null) simulation = decode(snapshot(level));
    }

    public void stopSimulation() {
        shutdown(simulation);
        simulation = null;
    }

    public static byte[] snapshot(Level world) throws IOException {
        MobRegistry.bootstrapDefaults();
        for (Object entity : world.blockMap.all) {
            if (entity instanceof net.classicremastered.minecraft.mob.Mob mob
                    && !(mob instanceof net.classicremastered.minecraft.player.Player)
                    && !mob.removed
                    && mob.health > 0
                    && MobRegistry.idOf(mob) < 0) {
                throw new IOException(
                        "Cannot preserve unregistered mob " + mob.getClass().getName());
            }
        }
        Path directory = Files.createTempDirectory("classic-editor-state-");
        Path state = directory.resolve("world.dat");
        File oldFile = world.saveFile;
        try {
            if (!new LevelIO(null).save(world, state.toFile()))
                throw new IOException("Cannot serialize world");
            return Files.readAllBytes(state);
        } finally {
            world.saveFile = oldFile;
            Files.deleteIfExists(state);
            Files.deleteIfExists(directory);
        }
    }

    private Level decode(byte[] bytes) throws IOException {
        Level result = io.load(new ByteArrayInputStream(bytes));
        if (result == null) throw new IOException("Cannot restore editor state");
        return result;
    }

    private static void trim(Deque<byte[]> states) {
        long bytes = states.stream().mapToLong(s -> s.length).sum();
        while (states.size() > 1 && (states.size() > 20 || bytes > 128L * 1024 * 1024))
            bytes -= states.removeFirst().length;
    }

    private static void validateMobs(File file) throws IOException {
        MobRegistry.bootstrapDefaults();
        Set<Short> known = new HashSet<>();
        for (String name : MobRegistry.allNames()) known.add(MobRegistry.idOfName(name));
        try (DataInputStream in =
                new DataInputStream(
                        new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            in.mark(8);
            int first = in.readUnsignedByte();
            in.reset();
            CompoundTag root;
            if (first == 10) root = NBTIO.read(in);
            else {
                in.readInt();
                int version = in.readUnsignedByte();
                if (version != 5 && version != 6) return;
                root = NBTIO.read(in);
            }
            ListTag mobs = root.getList("Entities");
            for (int i = 0; i < mobs.size(); i++) {
                CompoundTag mob = (CompoundTag) mobs.get(i);
                if (!known.contains(mob.getShort("Id"))
                        && MobRegistry.idOfName(mob.getString("id")) < 0)
                    throw new IOException(
                            "Unresolved mob type: "
                                    + mob.getString("id")
                                    + " ("
                                    + mob.getShort("Id")
                                    + ")");
            }
        }
    }

    private static void shutdown(Level world) {
        if (world instanceof LevelInfiniteFlat flat) flat.chunks().shutdown();
        if (world instanceof LevelInfiniteTerrain terrain) terrain.chunks().shutdown();
    }

    public void close() {
        stopSimulation();
        shutdown(level);
    }
}
