package net.classicremastered.minecraft.model;

public final class TexturedQuad {

    // NEW: configurable atlas size (defaults match Classic)
    public static int ATLAS_W = 64;
    public static int ATLAS_H = 32;

    public Vertex[] vertices;

    private TexturedQuad(Vertex[] vs) {
        this.vertices = vs;
    }

    // pixel-rect ctor -> normalized UVs using ATLAS_W/ATLAS_H
    public TexturedQuad(Vertex[] vs, int u0, int v0, int u1, int v1) {
        this(vs);
        // scale the same “shrink” fudge proportionally to atlas size
        float uEps = (1f / ATLAS_W) / 10f; // was 0.0015625 for 64w
        float vEps = (1f / ATLAS_H) / 10f; // was 0.003125  for 32h

        float fu0 = u0 / (float) ATLAS_W + uEps;
        float fv0 = v0 / (float) ATLAS_H + vEps;
        float fu1 = u1 / (float) ATLAS_W - uEps;
        float fv1 = v1 / (float) ATLAS_H - vEps;

        vs[0] = vs[0].create(fu1, fv0);
        vs[1] = vs[1].create(fu0, fv0);
        vs[2] = vs[2].create(fu0, fv1);
        vs[3] = vs[3].create(fu1, fv1);
    }

    // already-normalized ctor (leave as-is)
    public TexturedQuad(Vertex[] vs, float fu0, float fv0, float fu1, float fv1) {
        this(vs);
        vs[0] = vs[0].create(fu1, fv0);
        vs[1] = vs[1].create(fu0, fv0);
        vs[2] = vs[2].create(fu0, fv1);
        vs[3] = vs[3].create(fu1, fv1);
    }
}
