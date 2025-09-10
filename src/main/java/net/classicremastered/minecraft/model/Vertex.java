package net.classicremastered.minecraft.model;

public final class Vertex {

    public Vec3D vector;
    public float u;
    public float v;

    public Vertex(float x, float y, float z, float u, float v) {
        this(new Vec3D(x, y, z), u, v);
    }

    public final Vertex create(float newU, float newV) {
        return new Vertex(this, newU, newV);
    }

    private Vertex(Vertex base, float newU, float newV) {
        this.vector = base.vector;
        this.u = newU;
        this.v = newV;
    }

    private Vertex(Vec3D vec, float u, float v) {
        this.vector = vec;
        this.u = u;
        this.v = v;
    }

    // NEW: convenience to duplicate with new UVs (same as create but clearer)
    public Vertex copyWithUV(float newU, float newV) {
        return new Vertex(this, newU, newV);
    }
}
