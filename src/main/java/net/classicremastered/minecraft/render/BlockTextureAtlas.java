package net.classicremastered.minecraft.render;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/** Builds the GPU atlas from individually editable block textures. */
public final class BlockTextureAtlas {
    public static final String RESOURCE = "/textures/block/atlas";

    private BlockTextureAtlas() {}

    public static BufferedImage create(ZipFile pack) throws IOException {
        return create(pack, false);
    }

    public static BufferedImage create(ZipFile pack, boolean opaqueLeaves) throws IOException {
        Properties tiles = new Properties();
        try (InputStream input = open("/textures/block/tiles.properties", null)) {
            tiles.load(input);
        }
        BufferedImage atlas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        try {
            for (String key : tiles.stringPropertyNames()) {
                int index = Integer.parseInt(key);
                BufferedImage tile = read("/textures/block/" + tiles.getProperty(key), pack);
                graphics.drawImage(
                        tile,
                        index % 16 * 16,
                        index / 16 * 16,
                        index % 16 * 16 + 16,
                        index / 16 * 16 + 16,
                        0,
                        0,
                        tile.getWidth(),
                        Math.min(tile.getWidth(), tile.getHeight()),
                        null);
            }
            graphics.drawImage(read("/textures/entity/chest.png", pack), 192, 192, 64, 64, null);
            if (opaqueLeaves) {
                fillLeafHoles(atlas, 22);
                fillLeafHoles(atlas, 24);
            }
        } finally {
            graphics.dispose();
        }
        return atlas;
    }

    private static void fillLeafHoles(BufferedImage atlas, int tile) {
        int left = tile % 16 * 16, top = tile / 16 * 16;
        for (int y = top; y < top + 16; y++) {
            for (int x = left; x < left + 16; x++) {
                int pixel = atlas.getRGB(x, y);
                atlas.setRGB(x, y, (pixel >>> 24) < 128 ? 0xFF284719 : pixel | 0xFF000000);
            }
        }
    }

    private static BufferedImage read(String path, ZipFile pack) throws IOException {
        try (InputStream input = open(path, pack)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("Invalid block texture: " + path);
            return image;
        }
    }

    private static InputStream open(String path, ZipFile pack) throws IOException {
        if (pack != null && pack.getEntry(path.substring(1)) != null)
            return pack.getInputStream(pack.getEntry(path.substring(1)));
        InputStream input = BlockTextureAtlas.class.getResourceAsStream(path);
        if (input == null) throw new IOException("Missing block texture: " + path);
        return input;
    }
}
