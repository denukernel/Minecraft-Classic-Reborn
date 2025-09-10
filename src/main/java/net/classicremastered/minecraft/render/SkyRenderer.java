package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

import java.util.Random;

public final class SkyRenderer {

    private final Level level;
    private final TextureManager texMgr;
    private int starList = -1;
    private int ticks = 0; // advance once per frame

    public SkyRenderer(Level level, TextureManager texMgr) {
        this.level = level;
        this.texMgr = texMgr;
        buildStarField();
    }

    /** call once per frame from Minecraft.run() */
    public void tick() {
        ticks++;
    }

    // === Nightmare fuel switch (25M+ distance) ===
    private boolean isNightmareZone(Player cam) {
        if (cam == null)
            return false;
        long dist = (long) Math.max(Math.abs(cam.x), Math.abs(cam.z));
        return dist >= 25_000_000L;
    }

    // ---- stars (infinite dome)
    private void buildStarField() {
        if (starList != -1)
            GL11.glDeleteLists(starList, 1);
        starList = GL11.glGenLists(1);

        float worldMax = Math.max(Math.max(level.width, level.height), level.depth);
        float R = Math.max(64f, worldMax * 0.8f);

        Random r = new Random(10842L);
        GL11.glNewList(starList, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_POINTS);

        int count = 2000;
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * 2f - 1f;
            float y = r.nextFloat() * 2f - 1f;
            float z = r.nextFloat() * 2f - 1f;
            float d2 = x * x + y * y + z * z;
            if (d2 < 0.01f || d2 > 1f) {
                i--;
                continue;
            }
            float inv = 1f / MathHelper.sqrt(d2);
            GL11.glVertex3f(x * inv * R, y * inv * R, z * inv * R);
        }

        GL11.glEnd();
        GL11.glEndList();
    }

    // ---- clouds (infinite sheet)
    public void renderClouds(float partial, float r, float g, float b) {
        if (level == null || texMgr == null)
            return;

        int cloudsTex = texMgr.load("/clouds.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cloudsTex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cullWasEnabled)
            GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glColor4f(r, g, b, 1.0f);

        float y = (float) (level.depth + 2);
        final float texStep = 1.0f / 2048.0f;
        float scroll = (ticks + partial) * texStep * 0.03f;

        // --- nightmare mode: clouds tear into stripes ---
        if (isNightmareZone((Player) level.player)) {
            scroll *= 50.0f; // insane fast scroll
        }

        ShapeRenderer sr = ShapeRenderer.instance;
        sr.begin();

        final int step = 512;
        final int pad = 2048;

        int baseX, baseZ, maxX, maxZ;
        if (level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat) {
            int cx = (int) (level.player.x);
            int cz = (int) (level.player.z);
            baseX = cx - pad;
            baseZ = cz - pad;
            maxX = cx + pad;
            maxZ = cz + pad;
        } else {
            baseX = -pad;
            baseZ = -pad;
            maxX = level.width + pad;
            maxZ = level.height + pad;
        }

        for (int x = baseX; x < maxX; x += step) {
            for (int z = baseZ; z < maxZ; z += step) {
                float u0 = (x) * texStep + scroll;
                float v0 = (z) * texStep;
                float u1 = (x + step) * texStep + scroll;
                float v1 = (z + step) * texStep;

                // top face
                sr.vertexUV(x, y, z + step, u0, v1);
                sr.vertexUV(x + step, y, z + step, u1, v1);
                sr.vertexUV(x + step, y, z, u1, v0);
                sr.vertexUV(x, y, z, u0, v0);

                // bottom face
                sr.vertexUV(x, y, z, u0, v0);
                sr.vertexUV(x + step, y, z, u1, v0);
                sr.vertexUV(x + step, y, z + step, u1, v1);
                sr.vertexUV(x, y, z + step, u0, v1);
            }
        }
        sr.end();

        if (cullWasEnabled)
            GL11.glEnable(GL11.GL_CULL_FACE);
    }

    public void renderSunMoon(Player cam, float partial) {
        int sunTex = texMgr.load("/terrain/sun.png");
        int moonTex = texMgr.load("/terrain/moon.png");

        float px = cam.xo + (cam.x - cam.xo) * partial;
        float py = cam.yo + (cam.y - cam.yo) * partial;
        float pz = cam.zo + (cam.z - cam.zo) * partial;

        float R = level != null ? level.depth * 2.0f : 512.0f;
        float SUN = R * 0.3f;
        float MOON = R * 0.2f;

        GL11.glPushMatrix();
        GL11.glTranslatef(px, py, pz);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

        GL11.glRotatef(level.getCelestialAngleSmooth() * 360.0f, 1f, 0f, 0f);

        ShapeRenderer sr = ShapeRenderer.instance;

        if (isNightmareZone(cam)) {
            // warp: tint red, squish vertically
            GL11.glScalef(1.0f, 0.4f + 0.2f * (float)Math.sin(ticks * 0.1f), 1.0f);
            GL11.glColor4f(1.0f, 0.3f, 0.3f, 1.0f);
        } else {
            GL11.glColor4f(1, 1, 1, 1);
        }

        // sun
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sunTex);
        sr.begin();
        sr.vertexUV(-SUN, R, -SUN, 0, 0);
        sr.vertexUV(SUN, R, -SUN, 1, 0);
        sr.vertexUV(SUN, R, SUN, 1, 1);
        sr.vertexUV(-SUN, R, SUN, 0, 1);
        sr.end();

        // moon (opposite)
        GL11.glPushMatrix();
        GL11.glRotatef(180f, 1f, 0f, 0f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, moonTex);
        sr.begin();
        sr.vertexUV(-MOON, R, -MOON, 0, 0);
        sr.vertexUV(MOON, R, -MOON, 1, 0);
        sr.vertexUV(MOON, R, MOON, 1, 1);
        sr.vertexUV(-MOON, R, MOON, 0, 1);
        sr.end();
        GL11.glPopMatrix();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_FOG);
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }




    public void renderStars(Player cam, float partial) {
        if (level == null)
            return;
        if (starList == -1)
            buildStarField();

        float alpha = Math.max(0.02f, level.getStarBrightness());
        if (alpha <= 0.01f)
            return;

        float px = cam.xo + (cam.x - cam.xo) * partial;
        float py = cam.yo + (cam.y - cam.yo) * partial;
        float pz = cam.zo + (cam.z - cam.zo) * partial;

        GL11.glPushMatrix();
        GL11.glTranslatef(px, py, pz);

        final float zFarSafe = Math.max(level.depth * 1.2f, 128f);
        final float scale = (zFarSafe * 0.85f) / 4000f;
        GL11.glScalef(scale, scale, scale);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDepthRange(0.999f, 1.0f);

        GL11.glEnable(GL11.GL_BLEND);

        if (isNightmareZone(cam)) {
            float jitter = (float) Math.sin(ticks * 0.05) * 5.0f;
            GL11.glRotatef((ticks % 360) * 3.0f, 1f, 0f, 0f);
            GL11.glTranslatef(jitter, jitter * 0.5f, jitter);

            GL11.glColor4f(0.5f + 0.5f * (float) Math.sin(ticks * 0.01f), 0.2f + 0.3f * (float) Math.cos(ticks * 0.02f),
                    0.7f, 0.9f);
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glPointSize(2.5f);
            GL11.glColor4f(1f, 1f, 1f, alpha);
        }

        GL11.glCallList(starList);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthRange(0f, 1f);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.0f);

        GL11.glPopMatrix();
    }
}
