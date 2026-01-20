package net.classicremastered.minecraft.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public final class Screenshot {
    private Screenshot() {}

    public static File take(File gameDir) {
        int w = Display.getWidth();
        int h = Display.getHeight();
        if (w <= 0 || h <= 0) return null;

        // detect if screenshot is crash-triggered
        boolean fromCrash = false;
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (e.getMethodName().equals("handleCrash")) {
                fromCrash = true;
                break;
            }
        }

        String folderName = fromCrash ? "crash_screenshots" : "screenshots";
        String prefix = fromCrash ? "crash-report-" : "screenshot-";
        File outDir = new File(gameDir, folderName);
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("[Screenshot] Cannot create: " + outDir.getAbsolutePath());
            return null;
        }

        String ts = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
        File out = new File(outDir, prefix + ts + ".png");

        int bpp = 4;
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * bpp);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int stride = w * bpp;

        for (int y = 0; y < h; y++) {
            int flipY = h - 1 - y;
            for (int x = 0; x < w; x++) {
                int i = y * stride + x * bpp;
                int r = buf.get(i) & 0xFF;
                int g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
                int a = buf.get(i + 3) & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, flipY, argb);
            }
        }

        // draw crash overlay
        if (fromCrash) {
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // large red header
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.setColor(Color.RED);
            String msg = "THIS CRASHED RIGHT HERE";
            int textWidth = g.getFontMetrics().stringWidth(msg);
            g.drawString(msg, (w - textWidth) / 2, h - 150);
            g.setColor(Color.BLACK);
            g.drawRect(10, 10, w - 20, h - 20);

            // --- stack trace region (top-left corner) ---
            List<String> logLines = readLastCrashLines(new File(gameDir, "crash_log.txt"), 12);
            if (!logLines.isEmpty()) {
                g.setFont(new Font("Consolas", Font.PLAIN, 14));
                g.setColor(new Color(255, 0, 0)); // pure red
                int x = 25;
                int y = 70; // start near top-left inside border
                int maxWidth = w / 2 - 50; // half width region

                for (String line : logLines) {
                    if (line == null || line.isBlank()) continue;

                    // word wrap long lines
                    String[] words = line.split(" ");
                    StringBuilder sb = new StringBuilder();
                    for (String word : words) {
                        String test = sb + word + " ";
                        if (g.getFontMetrics().stringWidth(test) > maxWidth) {
                            g.drawString(sb.toString(), x, y);
                            y += 17;
                            sb.setLength(0);
                        }
                        sb.append(word).append(" ");
                    }
                    if (sb.length() > 0) {
                        g.drawString(sb.toString(), x, y);
                        y += 17;
                    }

                    if (y > h / 2) break; // stop halfway down
                }
            }

            g.dispose();
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

    // read last N lines of crash_log.txt for overlay text
    private static List<String> readLastCrashLines(File crashLog, int maxLines) {
        List<String> lines = new LinkedList<>();
        if (!crashLog.exists()) return lines;
        try (RandomAccessFile raf = new RandomAccessFile(crashLog, "r")) {
            long fileLength = raf.length() - 1;
            int linesRead = 0;
            StringBuilder sb = new StringBuilder();
            for (long pos = fileLength; pos >= 0; pos--) {
                raf.seek(pos);
                int ch = raf.read();
                if (ch == '\n') {
                    if (sb.length() > 0) {
                        lines.add(0, sb.reverse().toString());
                        sb.setLength(0);
                        linesRead++;
                        if (linesRead >= maxLines) break;
                    }
                } else {
                    sb.append((char) ch);
                }
            }
            if (sb.length() > 0 && linesRead < maxLines)
                lines.add(0, sb.reverse().toString());
        } catch (Throwable ignored) {}
        return lines;
    }
}
