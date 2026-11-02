package net.classicremastered.util;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Shared HTTPS skin loader for the classic 64 by 32 humanoid texture atlas. */
public final class SkinDownloader {
    private static final String SKIN_URL = "https://minotar.net/skin/";
    private static final int MAX_BYTES = 1024 * 1024;

    private SkinDownloader() {}

    public static BufferedImage download(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,16}|[a-fA-F0-9]{32}")) return null;
        try {
            return downloadImage(new URL(SKIN_URL + username));
        } catch (IOException ex) {
            System.err.println("[Skins] Skin unavailable for " + username + ": " + ex.getMessage());
            return null; // keep the bundled default skin while offline
        }
    }

    private static BufferedImage downloadImage(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "ClassicReborn/0.45");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            byte[] bytes;
            try (InputStream in = connection.getInputStream()) {
                bytes = in.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw new IOException("Skin image is too large");
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return null;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width != 64 || (height != 32 && height != 64)) return null;
                    BufferedImage source = reader.read(0);
                    // The current player model mirrors the original arms and legs.
                    // Modern skins retain this original atlas in their upper half.
                    BufferedImage classic = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
                    classic.setRGB(0, 0, 64, 32, source.getRGB(0, 0, 64, 32, null, 0, 64), 0, 64);
                    return classic;
                } finally { reader.dispose(); }
            }
        } finally { connection.disconnect(); }
    }
}
