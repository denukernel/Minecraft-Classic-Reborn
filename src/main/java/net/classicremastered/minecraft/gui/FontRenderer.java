package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.GameSettings;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Classic 8×8 ASCII atlas + Unicode fallback (Noto Sans) for any non-ASCII.
 * Fallback glyphs now render at ~8–9 px so they match the classic height.
 */
public final class FontRenderer {

    private final int[] widthmap = new int[256];
    private int fontTexture = 0;
    private final GameSettings settings;

    // Unicode fallback (lazy)
    private UnicodeFont unicode;

    // use the upright Noto Sans (variable TTF) you bundled
    private static final String FALLBACK_FONT_RESOURCE = "/assets/font/NotoSans.ttf";
    private static final float FALLBACK_SIZE_PX = 9.0f; // ~classic height

    public FontRenderer(GameSettings settings, String atlasPath, TextureManager textures) {
        this.settings = settings;

        // load classic 8x8 font atlas and compute widths for ASCII 0..127
        BufferedImage img;
        try {
            img = ImageIO.read(TextureManager.class.getResourceAsStream(atlasPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int w = img.getWidth();
        int h = img.getHeight();
        int[] rgba = new int[w * h];
        img.getRGB(0, 0, w, h, rgba, 0, w);

        for (int c = 0; c < 128; ++c) {
            int col = c % 16;
            int row = c / 16;
            int adv = 0;
            boolean empty = false;
            for (; adv < 8 && !empty; ++adv) {
                int px = (col << 3) + adv;
                empty = true;
                for (int yy = 0; yy < 8 && empty; ++yy) {
                    int idx = ((row << 3) + yy) * w + px;
                    if ((rgba[idx] & 0xFF) > 128)
                        empty = false;
                }
            }
            if (c == 32)
                adv = 4; // space
            widthmap[c] = adv;
        }

        this.fontTexture = textures.load(atlasPath);
    }

    /* ------------ public API ------------ */

    public void render(String s, int x, int y, int rgb) {
        renderInternal(s, x + 1, y + 1, rgb, true);
        renderInternal(s, x, y, rgb, false);
    }

    public void renderNoShadow(String s, int x, int y, int rgb) {
        renderInternal(s, x, y, rgb, false);
    }

    public int getWidth(String s) {
        if (s == null)
            return 0;
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // Skip classic color codes (&x or §x)
            if ((ch == '&' || ch == '§') && i + 1 < s.length()) {
                i++;
                continue;
            }

            int gi = ch & 0xFF;
            if ((ch == gi) && gi >= 32 && widthmap[gi] > 0) {
                w += widthmap[gi];
            } else {
                ensureUnicode();
                UnicodeFont.Glyph g = unicode.get(ch);
                w += (g != null ? g.adv : 8);
            }
        }
        return w;
    }

    public static String stripColor(String s) {
        if (s == null)
            return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /* ------------ internals ------------ */

    private void renderInternal(String text, int x, int y, int rgb, boolean shadow) {
        if (text == null)
            return;

        int curColor = shadow ? ((rgb & 0xFCFCFC) >> 2) : rgb;
        final String codes = "0123456789abcdef";

        int cursor = x;
        int boundTex = -1;

        ShapeRenderer sr = ShapeRenderer.instance;

        // start with ASCII atlas
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.fontTexture);
        boundTex = this.fontTexture;
        sr.begin();
        sr.color(curColor);

        for (int i = 0; i < text.length();) {
            char ch = text.charAt(i);

            // classic color codes (&x or §x)
            if ((ch == '&' || ch == '§') && i + 1 < text.length()) {
                char c2 = Character.toLowerCase(text.charAt(i + 1));
                int idx = codes.indexOf(c2);
                if (idx >= 0) {
                    int base = (idx & 8) << 3;
                    int r = ((idx & 1) != 0 ? 191 : 0) + base;
                    int g = ((idx & 2) != 0 ? 191 : 0) + base;
                    int b = ((idx & 4) != 0 ? 191 : 0) + base;

                    if (settings.anaglyph) {
                        int rGray = (r * 30 + g * 59 + b * 11) / 100;
                        int gGray = (r * 30 + g * 70) / 100;
                        int bGray = (r * 30 + b * 70) / 100;
                        r = rGray;
                        g = gGray;
                        b = bGray;
                    }
                    curColor = (r << 16) | (g << 8) | b;
                    if (shadow)
                        curColor = (curColor & 0xFCFCFC) >> 2;

                    // flush color change
                    sr.end();
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, boundTex);
                    sr.begin();
                    sr.color(curColor);

                    i += 2;
                    continue;
                }
            }

            // ASCII path
            int gi = ch & 0xFF;
            boolean asciiCandidate = (ch == gi) && gi >= 32 && widthmap[gi] > 0;

            if (asciiCandidate) {
                if (boundTex != this.fontTexture) {
                    sr.end();
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.fontTexture);
                    boundTex = this.fontTexture;
                    sr.begin();
                    sr.color(curColor);
                }

                int u = (gi % 16) << 3;
                int v = (gi / 16) << 3;
                float quad = 7.99F;
                float u0 = (float) u / 128.0F, v0 = (float) v / 128.0F;
                float u1 = ((float) u + quad) / 128.0F, v1 = ((float) v + quad) / 128.0F;

                sr.vertexUV(cursor, y + quad, 0, u0, v1);
                sr.vertexUV(cursor + quad, y + quad, 0, u1, v1);
                sr.vertexUV(cursor + quad, y, 0, u1, v0);
                sr.vertexUV(cursor, y, 0, u0, v0);

                cursor += widthmap[gi];
                i++;
                continue;
            }

            // Unicode fallback
            ensureUnicode();
            UnicodeFont.Glyph g = unicode.get(ch);
            if (g != null) {
                if (boundTex != g.texId) {
                    sr.end();
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, g.texId);
                    boundTex = g.texId;
                    sr.begin();
                    sr.color(curColor);
                }

                float x0 = cursor, y0 = y;
                float x1 = cursor + g.w, y1 = y + g.h;

                sr.vertexUV(x0, y1, 0, g.u0, g.v1);
                sr.vertexUV(x1, y1, 0, g.u1, g.v1);
                sr.vertexUV(x1, y0, 0, g.u1, g.v0);
                sr.vertexUV(x0, y0, 0, g.u0, g.v0);

                cursor += g.adv;
            } else {
                cursor += 8;
            }
            i++;
        }

        sr.end();
    }

    private void ensureUnicode() {
        if (unicode == null) {
            unicode = new UnicodeFont(FALLBACK_FONT_RESOURCE, FALLBACK_SIZE_PX);
        }
    }
}
