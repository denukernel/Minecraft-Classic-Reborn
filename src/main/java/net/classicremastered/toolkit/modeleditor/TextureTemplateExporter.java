package net.classicremastered.toolkit.modeleditor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Exports a UV template PNG:
 *  - off-model pixels stay fully transparent
 *  - optional semi-transparent checker background
 *  - faces are filled with stable random colors (no outlines)
 */
public final class TextureTemplateExporter {
    private TextureTemplateExporter() {}

    /** @param faceAlpha     0..255 alpha of face fills (e.g., 255)
     *  @param checkerAlpha  0..255 alpha of checker (e.g., 48). Use 0 for no checker.
     */
    public static void exportTemplatePNG(File out, EditableModel em, int faceAlpha, int checkerAlpha) throws Exception {
        final int W = Math.max(8, em.atlasW);
        final int H = Math.max(8, em.atlasH);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // fully transparent background
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(0,0,0,0));
        g.fillRect(0,0,W,H);

        // optional semi-transparent checker (purely for visual aid in the PNG)
        if (checkerAlpha > 0) drawChecker(g, W, H, checkerAlpha);

        // draw faces (and their children) – no outlines
        for (EditablePart p : em.parts) drawPartFaces(g, p, faceAlpha);

        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("[template] Wrote " + out.getAbsolutePath());
    }

    private static void drawChecker(Graphics2D g, int W, int H, int alpha) {
        g.setComposite(AlphaComposite.SrcOver);
        final int cell = 16;
        Color a = new Color(43, 47, 54, alpha);   // dark
        Color b = new Color(36, 41, 50, alpha);   // light
        for (int y = 0; y < H; y += cell)
            for (int x = 0; x < W; x += cell) {
                g.setColor((((x ^ y) & cell) == 0) ? a : b);
                g.fillRect(x, y, Math.min(cell, W - x), Math.min(cell, H - y));
            }
    }

    private static void drawPartFaces(Graphics2D g, EditablePart p, int faceAlpha) {
        // deterministic RNG per part (stable output); also vary per face index
        long base = (p.name == null ? 0L : p.name.hashCode())
                  ^ (((long)p.u & 0xffffffffL) << 32) ^ (p.v & 0xffffffffL);

        Rect[] faces = faceRects(p);
        for (int i = 0; i < faces.length; i++) {
            Rect r = faces[i];
            if (r.w <= 0 || r.h <= 0) continue;

            java.util.Random rnd = new java.util.Random(base ^ (i * 0x9E3779B97F4A7C15L));
            float hue = rnd.nextFloat();
            float sat = 0.55f + rnd.nextFloat() * 0.35f; // 0.55–0.90
            float val = 0.85f + rnd.nextFloat() * 0.15f; // 0.85–1.00
            int rgb = Color.HSBtoRGB(hue, sat, val);
            Color fill = new Color((rgb>>16)&0xFF, (rgb>>8)&0xFF, rgb&0xFF,
                                   Math.max(0, Math.min(255, faceAlpha)));

            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(fill);
            g.fillRect(r.x, r.y, r.w, r.h);
        }

        for (EditablePart c : p.children) drawPartFaces(g, c, faceAlpha);
    }

    // Classic cross layout, per-face rects
    private static Rect[] faceRects(EditablePart p) {
        int u = p.u, v = p.v, w = p.w, h = p.h, d = p.d;
        Rect negY = new Rect(u + d,         v,     w, d);             // bottom
        Rect posY = new Rect(u + d + w,     v,     w, d);             // top
        Rect negX = new Rect(u,             v + d, d, h);
        Rect posX = new Rect(u + d + w,     v + d, d, h);
        Rect negZ = new Rect(u + d,         v + d, w, h);             // front
        Rect posZ = new Rect(u + d + w + d, v + d, w, h);             // back
        return new Rect[]{ negY, posY, negX, posX, negZ, posZ };
    }

    private static final class Rect { final int x,y,w,h; Rect(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;} }
}
