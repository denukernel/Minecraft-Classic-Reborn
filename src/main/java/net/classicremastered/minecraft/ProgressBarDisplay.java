package net.classicremastered.minecraft;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.render.ShapeRenderer;

public class ProgressBarDisplay {

    protected String text = "";
    private final Minecraft minecraft;
    protected String title = "";
    private long start = System.currentTimeMillis();

    public ProgressBarDisplay(Minecraft mc) {
        this.minecraft = mc;
    }

    public void setTitle(String s) {
        if (!this.minecraft.running) throw new StopGameException();
        this.title = (s == null ? "" : s);

        // If there’s no GL context yet, just cache the title and bail.
        if (!glReady()) return;

        // Setup the ortho once we know GL exists.
        setupOrtho();
        // We don’t force a draw here; next setProgress() (or your render loop) will.
    }

    public void setText(String s) {
        if (!this.minecraft.running) throw new StopGameException();
        this.text = (s == null ? "" : s);

        // No GL? cache only.
        if (!glReady()) return;

        // Nudge a redraw (indeterminate).
        setProgress(-1);
    }

public void setProgress(int pct) {
    if (!this.minecraft.running) throw new StopGameException();
    if (!glReady()) return;

    long now = System.currentTimeMillis();
    if (now - this.start >= 0L && now - this.start < 20L) return;
    this.start = now;

    final int w = Math.max(1, this.minecraft.width  * 240 / Math.max(1, this.minecraft.height));
    final int h = Math.max(1, this.minecraft.height * 240 / Math.max(1, this.minecraft.height));

 // ---------- Clear & prep 2D ----------
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    setupOrtho();

    // ---------- Background dirt (NO tint, NO blend) ----------
    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glColor4f(1f, 1f, 1f, 1f);              // force no tint
    int oldEnv = GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE) == GL11.GL_REPLACE ? GL11.GL_REPLACE : GL11.GL_MODULATE;
    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE); // texture color only

    int dirtTex = 0;
    try { dirtTex = this.minecraft.textureManager.load("/dirt.png"); } catch (Throwable ignored) {}
    if (dirtTex != 0) GL11.glBindTexture(GL11.GL_TEXTURE_2D, dirtTex);

    ShapeRenderer sr = ShapeRenderer.instance;
    float tile = 32.0F;
    sr.begin();
    // don't call sr.color() for textured quad; rely on GL color = white
    sr.vertexUV(0.0F,    (float) h, 0.0F,              0.0F,            (float) h / tile);
    sr.vertexUV((float) w,(float) h, 0.0F, (float) w / tile,            (float) h / tile);
    sr.vertexUV((float) w,0.0F,      0.0F, (float) w / tile,            0.0F);
    sr.vertexUV(0.0F,     0.0F,      0.0F,              0.0F,            0.0F);
    sr.end();

    // restore texture env
    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, oldEnv);

    // ---------- Progress bar ----------
    if (pct >= 0) {
        int bx = w / 2 - 50;
        int by = h / 2 + 16;
        int fill = Math.max(0, Math.min(100, pct));

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        sr.begin();
        // track background (mid gray)
        GL11.glColor4f(0.62f, 0.62f, 0.62f, 1f); // ~#9E9E9E
        sr.vertex((float) bx,           (float) by,        0f);
        sr.vertex((float) bx,           (float) (by + 2),  0f);
        sr.vertex((float) (bx + 100),   (float) (by + 2),  0f);
        sr.vertex((float) (bx + 100),   (float) by,        0f);
        sr.end();

        // fill (browner orange, not red)
        sr.begin();
        GL11.glColor4f(0.74f, 0.37f, 0.18f, 1f); // ~#BD5F2F (classic-looking)
        sr.vertex((float) bx,           (float) by,        0f);
        sr.vertex((float) bx,           (float) (by + 2),  0f);
        sr.vertex((float) (bx + fill),  (float) (by + 2),  0f);
        sr.vertex((float) (bx + fill),  (float) by,        0f);
        sr.end();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    // ---------- Text ----------
    if (this.minecraft.fontRenderer != null) {
        int tx = (w - this.minecraft.fontRenderer.getWidth(this.title)) / 2;
        int ty = h / 2 - 4 - 16;
        int sx = (w - this.minecraft.fontRenderer.getWidth(this.text)) / 2;
        int sy = h / 2 - 4 + 8;
        this.minecraft.fontRenderer.render(this.title, tx, ty, 0xFFFFFF);
        this.minecraft.fontRenderer.render(this.text,  sx, sy, 0xFFFFFF);
    }

    // ---------- Restore clean state ----------
    GL11.glColor4f(1f, 1f, 1f, 1f);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_LIGHTING);
    if (Display.isCreated()) {
        Display.update();
        try { Thread.yield(); } catch (Throwable ignored) {}
    }
}

    // ---------- helpers ----------

    private boolean glReady() {
        // We consider GL ready only when a context is created and not destroyed
        return Display.isCreated();
    }

    private void setupOrtho() {
        // guard again, since some callers might call during teardown
        if (!glReady()) return;

        final int w = Math.max(1, this.minecraft.width  * 240 / Math.max(1, this.minecraft.height));
        final int h = Math.max(1, this.minecraft.height * 240 / Math.max(1, this.minecraft.height));

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, (double) w, (double) h, 0.0, 100.0, 300.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -200.0F);
    }
}
