package net.classicremastered.minecraft.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Screenshot {
    private Screenshot() {}

    /**
     * Capture the current OpenGL back buffer into a PNG.
     * Call this AFTER the frame is rendered but BEFORE Display.update().
     * @param gameDir base dir (use Minecraft.mcDir)
     * @return the saved file or null if failed
     */
    public static File take(File gameDir) {
        int w = Display.getWidth();
        int h = Display.getHeight();
        if (w <= 0 || h <= 0) return null;

        File outDir = new File(gameDir, "screenshots");
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("[Screenshot] Cannot create: " + outDir.getAbsolutePath());
            return null;
        }

        String ts = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
        File out = new File(outDir, ts + ".png");

        int bpp = 4; // RGBA
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * bpp);

        GL11.glReadBuffer(GL11.GL_BACK);                 // we’re still on the back buffer pre-swap
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int stride = w * bpp;

        // Flip vertically and convert RGBA -> ARGB
        for (int y = 0; y < h; y++) {
            int flipY = h - 1 - y;
            for (int x = 0; x < w; x++) {
                int i = y * stride + x * bpp;
                int r = buf.get(i)   & 0xFF;
                int g = buf.get(i+1) & 0xFF;
                int b = buf.get(i+2) & 0xFF;
                int a = buf.get(i+3) & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, flipY, argb);
            }
        }

        try {
            ImageIO.write(img, "png", out);
            System.out.println("[Screenshot] Saved: " + out.getAbsolutePath());
            return out;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
