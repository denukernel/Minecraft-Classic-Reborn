package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.render.ShapeRenderer;

/** Atlas-backed cuboids for partial blocks, without mutating shared block bounds. */
public final class BlockShape {
    private BlockShape() {}

    public static void box(
            ShapeRenderer s,
            float x,
            float y,
            float z,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int top,
            int bottom,
            int side,
            float red,
            float green,
            float blue) {
        float[][] points = {
            {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1},
            {x1, y1, z1, x1, y1, z0, x0, y1, z0, x0, y1, z1},
            {x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0},
            {x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1},
            {x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1},
            {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}
        };
        for (int face = 0; face < 6; face++) {
            int tex = face == 0 ? bottom : face == 1 ? top : side;
            float u = (tex % 16) / 16f, v = (tex / 16) / 16f, d = 15.99f / 256f;
            float shade = face == 1 ? 1 : face == 0 ? .5f : face < 4 ? .8f : .6f;
            s.color(red * shade, green * shade, blue * shade);
            float[] p = points[face];
            s.vertexUV(x + p[0], y + p[1], z + p[2], u, v);
            s.vertexUV(x + p[3], y + p[4], z + p[5], u + d, v);
            s.vertexUV(x + p[6], y + p[7], z + p[8], u + d, v + d);
            s.vertexUV(x + p[9], y + p[10], z + p[11], u, v + d);
        }
    }
}
