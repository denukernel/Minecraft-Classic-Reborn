package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal Unicode glyph cache sized to ~9px glyphs so they match the classic 8×8 look.
 * - Packs glyphs into 384×384 pages with 24×24 cells (16×16 grid).
 * - Sets texture wrap to CLAMP to avoid bleeding.
 */
final class UnicodeFont {
    private final Font awtFont;
    private final FontRenderContext frc = new FontRenderContext(null, true, true);
    private final Map<Integer, Page> pages = new HashMap<>();

    // tuned for ~9px glyphs
    private static final int TEX_W = 384, TEX_H = 384; // smaller page
    private static final int CELL  = 24;               // each cell 24×24
    private static final int COLS  = TEX_W / CELL;     // 16
    private static final int ROWS  = TEX_H / CELL;     // 16
    private static final int MAX_CELLS = COLS * ROWS;  // 256

    static final class Glyph {
        int texId;
        float u0, v0, u1, v1;
        int w, h, adv;
    }

    static final class Page {
        int texId = 0;
        int nextCell = 0;
        BufferedImage sheet = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g;
        Map<Character, Glyph> glyphs = new HashMap<>();
        Page() {
            g = sheet.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0,0,0,0));
            g.fillRect(0,0,TEX_W,TEX_H);
            g.setColor(Color.WHITE);
        }
    }

    UnicodeFont(String resourcePath, float sizePx) {
        try (InputStream in = UnicodeFont.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalArgumentException("Font resource not found: " + resourcePath);
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            this.awtFont = base.deriveFont(sizePx);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fallback font: " + resourcePath, e);
        }
    }

    UnicodeFont.Glyph get(char ch) {
        int pageKey = ch >>> 8;
        Page page = pages.computeIfAbsent(pageKey, k -> new Page());

        Glyph cached = page.glyphs.get(ch);
        if (cached != null) return cached;

        try {
            GlyphVector gv = awtFont.createGlyphVector(frc, new char[]{ch});
            Shape outline = gv.getGlyphOutline(0);
            Rectangle b = outline.getBounds();

            // pad a little to avoid cutting edge pixels
            int w = Math.max(1, Math.min(CELL-2, b.width + 2));
            int h = Math.max(1, Math.min(CELL-2, b.height + 2));

            // render glyph into small ARGB
            BufferedImage bmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = bmp.createGraphics();
            gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gg.translate(-b.x, -b.y);
            gg.setColor(Color.WHITE);
            gg.fill(outline);
            gg.dispose();

            // allocate cell
            int cell = page.nextCell++;
            if (cell >= MAX_CELLS) {
                page = new Page();
                pages.put(pageKey, page);
                cell = page.nextCell++;
            }
            int cx = (cell % COLS) * CELL;
            int cy = (cell / COLS) * CELL;

            // blit into page
            page.g.drawImage(bmp, cx, cy, null);

            // upload / refresh page texture
            page.texId = upload(page.sheet, page.texId);

            Glyph g = new Glyph();
            g.texId = page.texId;
            g.w = w; g.h = h;

            float adv = gv.getGlyphMetrics(0).getAdvanceX();
            g.adv = (int)Math.ceil(adv > 0 ? adv : w);
            if (g.adv < 6) g.adv = 6; // keep cursor moving

            g.u0 = (float) cx / TEX_W;
            g.v0 = (float) cy / TEX_H;
            g.u1 = (float) (cx + w) / TEX_W;
            g.v1 = (float) (cy + h) / TEX_H;

            page.glyphs.put(ch, g);
            return g;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int upload(BufferedImage img, int existingTex) {
        int id = existingTex == 0 ? GL11.glGenTextures() : existingTex;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        // prevent edge bleed
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        int w = img.getWidth(), h = img.getHeight();
        int[] pix = img.getRGB(0,0,w,h,null,0,w);
        ByteBuffer buf = ByteBuffer.allocateDirect(w*h*4);
        for (int p : pix) {
            buf.put((byte)((p>>16)&0xFF));
            buf.put((byte)((p>> 8)&0xFF));
            buf.put((byte)( p      &0xFF));
            buf.put((byte)((p>>24)&0xFF));
        }
        buf.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        return id;
    }
}
