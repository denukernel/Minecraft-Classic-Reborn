package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.render.TextureManager;

public class MD3Renderer {

    // Toggle: render fullbright (ignores world tint/lighting). Set to false for real lighting.
    private static final boolean FULLBRIGHT = true;

    // Your fixed MD3 size for Rana: scale shorts by (0.4 / 64)
    private static final float MD3_SHORT_SCALE = 0.025f / 64.0f;

    public static void render(MD3Model m, String texPath, TextureManager tm, float mcScaleIgnored) {
        if (m == null) return;

        final int tex = tm.load(texPath);
        if (tex <= 0) {
            System.out.println("[MD3] WARN: texture not found: " + texPath);
            return;
        }

        // Save GL state so changes don’t leak to world rendering
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            // Texture + color state
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glColor3f(1f, 1f, 1f);

            if (FULLBRIGHT) {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
            } else {
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            }

            // MD3 Z-up → Classic Y-up: rotate +90° around X
            GL11.glRotatef(90f, 1f, 0f, 0f);

            // Apply MD3 short scale (your fixed size). mcScaleIgnored is not used here.
            GL11.glScalef(MD3_SHORT_SCALE, MD3_SHORT_SCALE, MD3_SHORT_SCALE);

            // Optional: draw both sides while testing
            // GL11.glDisable(GL11.GL_CULL_FACE);

            for (MD3Surface s : m.surfaces) {
                if (s == null || s.tris == null || s.uvs == null || s.verts == null) continue;

                // Heuristic: if UVs are large, they were exported as 16-bit values; normalize them
                boolean normalizeUV = false;
                if (s.uvs.length >= 2) {
                    float au = Math.abs(s.uvs[0]);
                    float av = Math.abs(s.uvs[1]);
                    if (au > 2f || av > 2f) normalizeUV = true;
                }

                // Choose whether to flip V (most MD3s need it). We’ll pick whichever has more spread.
                boolean flipV = shouldFlipV(s, normalizeUV);

                GL11.glBegin(GL11.GL_TRIANGLES);
                for (int t = 0; t < s.tris.length; t += 3) {
                    for (int j = 0; j < 3; j++) {
                        final int idx = s.tris[t + j];
                        if (idx < 0 || idx >= s.numVerts) continue;

                        // --- UVs ---
                        float u = s.uvs[idx * 2];
                        float v = s.uvs[idx * 2 + 1];
                        if (normalizeUV) { u /= 32767f; v /= 32767f; }
                        if (flipV) v = 1f - v;
                        GL11.glTexCoord2f(u, v);

                        // --- Optional lighting (decode MD3 lat/long normal) ---
                        if (!FULLBRIGHT) {
                            int n = s.verts[idx * 4 + 3] & 0xFFFF; // unsigned short
                            float[] N = decodeMD3Normal(n);
                            GL11.glNormal3f(N[0], N[1], N[2]);
                        }

                        // --- Position (frame 0 shorts) ---
                        short vx = s.verts[idx * 4    ];
                        short vy = s.verts[idx * 4 + 1];
                        short vz = s.verts[idx * 4 + 2];
                        GL11.glVertex3f(vx, vy, vz);
                    }
                }
                GL11.glEnd();
            }

        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    // Pick V vs (1-V) by measuring UV variation on a small sample
    private static boolean shouldFlipV(MD3Surface s, boolean normalizeUV) {
        int sample = Math.min(s.numVerts, 128);
        float minUA =  1e9f, maxUA = -1e9f, minVA =  1e9f, maxVA = -1e9f;
        float minUB =  1e9f, maxUB = -1e9f, minVB =  1e9f, maxVB = -1e9f;
        for (int i = 0; i < sample; i++) {
            float u = s.uvs[i * 2];
            float v = s.uvs[i * 2 + 1];
            if (normalizeUV) { u /= 32767f; v /= 32767f; }

            // A: direct v
            if (u < minUA) minUA = u; if (u > maxUA) maxUA = u;
            if (v < minVA) minVA = v; if (v > maxVA) maxVA = v;
            // B: flipped v
            float vf = 1f - v;
            if (u < minUB) minUB = u; if (u > maxUB) maxUB = u;
            if (vf < minVB) minVB = vf; if (vf > maxVB) maxVB = vf;
        }
        float spreadA = (maxUA - minUA) + (maxVA - minVA);
        float spreadB = (maxUB - minUB) + (maxVB - minVB);
        return spreadB > spreadA;
    }

    // Decode MD3 lat/long packed normal (unsigned short)
    // lat = (n >> 8) * (2π / 255), lng = (n & 255) * (2π / 255)
    // nx = cos(lat)*sin(lng), ny = sin(lat)*sin(lng), nz = cos(lng)
    private static float[] decodeMD3Normal(int packed) {
        float lat = ((packed >> 8) & 0xFF) * (float)(2.0 * Math.PI / 255.0);
        float lng = ( packed        & 0xFF) * (float)(2.0 * Math.PI / 255.0);
        float nx = (float)(Math.cos(lat) * Math.sin(lng));
        float ny = (float)(Math.sin(lat) * Math.sin(lng));
        float nz = (float)(Math.cos(lng));
        return new float[]{ nx, ny, nz };
    }
}
