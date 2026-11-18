package net.classicremastered.minecraft.level;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/** Local world folders and compatibility with the original level list. */
public final class WorldStorage {
    private static final String DATA_FILE = "level.dat";
    private static final String LEGACY_EXTENSION = ".lvl.gz";

    private WorldStorage() {}

    public static List<File> list(File gameDir) {
        List<File> worlds = new ArrayList<>();
        File[] folders = new File(gameDir, "saves").listFiles();
        if (folders != null) {
            for (File folder : folders) {
                File world = new File(folder, DATA_FILE);
                if (!Files.isSymbolicLink(folder.toPath()) && world.isFile()) worlds.add(world);
                else if (folder.isFile() && isLegacy(folder)) worlds.add(folder);
            }
        }
        File[] legacy =
                new File(gameDir, "levels").listFiles(file -> file.isFile() && isLegacy(file));
        if (legacy != null) worlds.addAll(List.of(legacy));
        worlds.sort(
                Comparator.comparingLong(File::lastModified)
                        .reversed()
                        .thenComparing(File::getPath));
        return worlds;
    }

    public static File create(File gameDir, String name) throws IOException {
        Path saves = Files.createDirectories(gameDir.toPath().resolve("saves"));
        String slug = name.replaceAll("[^\\p{L}\\p{N} _-]", "_").strip();
        if (slug.isBlank()) slug = "world";
        if (slug.length() > 48) slug = slug.substring(0, 48);
        // The prefix also avoids Windows device names such as CON.
        String base = "world-" + slug;
        for (int i = 1; ; i++) {
            Path folder = saves.resolve(i == 1 ? base : base + "-" + i);
            try {
                Files.createDirectory(folder);
            } catch (FileAlreadyExistsException occupied) {
                continue;
            }
            File world = folder.resolve(DATA_FILE).toFile();
            try {
                rename(world, name);
                return world;
            } catch (IOException failure) {
                Files.deleteIfExists(folder);
                throw failure;
            }
        }
    }

    public static boolean hasWorldFolder(File world) {
        File folder = world.getAbsoluteFile().getParentFile();
        return DATA_FILE.equals(world.getName())
                && folder != null
                && folder.getParentFile() != null
                && "saves".equals(folder.getParentFile().getName());
    }

    public static Path icon(File world) {
        if (hasWorldFolder(world)) return world.toPath().resolveSibling("icon.png");
        return isLegacy(world) ? world.toPath().resolveSibling(world.getName() + ".png") : null;
    }

    private static boolean isLegacy(File world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(LEGACY_EXTENSION) || name.endsWith(".lvl");
    }

    private static Path nameFile(File world) {
        return world.toPath()
                .toAbsolutePath()
                .resolveSibling(
                        hasWorldFolder(world) ? "world-name.txt" : world.getName() + ".name");
    }

    public static String storedName(File world) {
        try {
            Path path = nameFile(world);
            if (!Files.isRegularFile(path) || Files.size(path) > 1024) return null;
            String name = Files.readString(path, StandardCharsets.UTF_8).strip();
            return name.isEmpty() ? null : name;
        } catch (IOException ignored) {
            return null;
        }
    }

    public static String displayName(File world) {
        String name = storedName(world);
        if (name != null) return name;
        if (hasWorldFolder(world)) return world.getAbsoluteFile().getParentFile().getName();
        name = world.getName();
        if (!isLegacy(world)) return name;
        int extensionLength =
                name.toLowerCase(Locale.ROOT).endsWith(LEGACY_EXTENSION)
                        ? LEGACY_EXTENSION.length()
                        : 4;
        return name.substring(0, name.length() - extensionLength);
    }

    public static void rename(File world, String name) throws IOException {
        String cleanName = name.strip();
        if (cleanName.isEmpty()
                || cleanName.length() > 64
                || cleanName.chars().anyMatch(Character::isISOControl))
            throw new IOException("Invalid world name.");
        replace(nameFile(world), tmp -> Files.writeString(tmp, cleanName, StandardCharsets.UTF_8));
    }

    public static void saveIcon(File world, BufferedImage image) throws IOException {
        Path destination = icon(world);
        if (destination != null)
            replace(
                    destination,
                    tmp -> {
                        if (!ImageIO.write(image, "png", tmp.toFile()))
                            throw new IOException("PNG writer unavailable.");
                    });
    }

    public static void delete(File world) throws IOException {
        Files.delete(world.toPath());
        Files.deleteIfExists(nameFile(world));
        Path icon = icon(world);
        if (icon != null) Files.deleteIfExists(icon);
        if (hasWorldFolder(world)) {
            // Leave user files in the folder intact.
            try {
                Files.delete(world.toPath().toAbsolutePath().getParent());
            } catch (java.nio.file.DirectoryNotEmptyException ignored) {
            }
        }
    }

    @FunctionalInterface
    private interface FileWriter {
        void write(Path temporary) throws IOException;
    }

    private static void replace(Path destination, FileWriter writer) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Path tmp = Files.createTempFile(absolute.getParent(), ".world-", ".tmp");
        try {
            writer.write(tmp);
            try {
                Files.move(
                        tmp,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
