package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;

/** Pixel-sized models with face UVs adapted from the modern block and chest models. */
public final class BlockModels {
    private record Face(float u0, float v0, float u1, float v1, int rotation) {}

    private BlockModels() {}

    private static Face tile(int index, float u0, float v0, float u1, float v1, int rotation) {
        return new Face(
                index % 16 * 16 + u0,
                index / 16 * 16 + v0,
                index % 16 * 16 + u1,
                index / 16 * 16 + v1,
                rotation);
    }

    private static Face chest(float u0, float v0, float u1, float v1, boolean standalone) {
        float scale = standalone ? 4 : 1;
        float offset = standalone ? 0 : 192;
        return new Face(
                offset + u0 * scale,
                offset + v0 * scale,
                offset + u1 * scale,
                offset + v1 * scale,
                0);
    }

    public static void chest(ShapeRenderer r, float x, float y, float z, int facing, float light) {
        chest(r, x, y, z, facing, light, 0, 0, false);
    }

    public static void chest(
            ShapeRenderer r,
            float x,
            float y,
            float z,
            int facing,
            float light,
            float openness,
            int half,
            boolean standalone) {
        float x0 = half < 0 ? 0 : 1, x1 = half > 0 ? 16 : 15;
        int width = half == 0 ? 14 : 15;
        Face[] body = chestFaces(width, 19, 10, standalone);
        Face[] lid = chestFaces(width, 0, 5, standalone);
        if (half > 0) {
            body[5] = null;
            lid[5] = null;
        }
        if (half < 0) {
            body[4] = null;
            lid[4] = null;
        }
        box(r, x, y, z, x0, 0, 1, x1, 10, 15, facing, light, body);
        float angle = openness * (float) Math.PI / 2;
        box(r, x, y, z, x0, 9, 1, x1, 14, 15, facing, light, lid, angle);
        float lockX0 = half > 0 ? 15 : half < 0 ? 0 : 7;
        float lockX1 = half > 0 ? 16 : half < 0 ? 1 : 9;
        int lockWidth = half == 0 ? 2 : 1;
        Face[] lock = {
            chest(1, 0, 1 + lockWidth, 1, standalone),
            chest(1 + lockWidth, 1, 1 + lockWidth * 2, 0, standalone),
            chest(2 + lockWidth, 1, 2 + lockWidth * 2, 5, standalone),
            chest(1, 1, 1 + lockWidth, 5, standalone),
            chest(1 + lockWidth, 1, 2 + lockWidth, 5, standalone),
            chest(0, 1, 1, 5, standalone)
        };
        if (half > 0) lock[5] = null;
        if (half < 0) lock[4] = null;
        box(r, x, y, z, lockX0, 7, 0, lockX1, 11, 1, facing, light, lock, angle);
    }

    private static Face[] chestFaces(int width, int v, int height, boolean standalone) {
        return new Face[] {
            chest(14, v, 14 + width, v + 14, standalone),
            chest(14 + width, v + 14, 14 + width * 2, v, standalone),
            chest(28 + width, v + 14, 28 + width * 2, v + 14 + height, standalone),
            chest(14, v + 14, 14 + width, v + 14 + height, standalone),
            chest(14 + width, v + 14, 28 + width, v + 14 + height, standalone),
            chest(0, v + 14, 14, v + 14 + height, standalone)
        };
    }

    public static void piston(
            ShapeRenderer r,
            float x,
            float y,
            float z,
            int facing,
            boolean extended,
            boolean head,
            boolean sticky,
            float light) {
        int front = sticky ? 147 : 148;
        if (head) {
            box(r, x, y, z, 0, 0, 0, 16, 16, 4, facing, light, pistonFaces(front, 148, 0, 4));

            box(
                    r,
                    x,
                    y,
                    z,
                    6,
                    6,
                    4,
                    10,
                    10,
                    20,
                    facing,
                    light,
                    new Face[] {
                        tile(150, 0, 0, 16, 4, 0),
                        tile(150, 0, 0, 16, 4, 0),
                        null,
                        null,
                        tile(150, 16, 4, 0, 0, 0),
                        tile(150, 0, 0, 16, 4, 0)
                    });
        } else {
            int start = extended ? 4 : 0;
            box(
                    r,
                    x,
                    y,
                    z,
                    0,
                    0,
                    start,
                    16,
                    16,
                    16,
                    facing,
                    light,
                    pistonFaces(extended ? 156 : front, 149, start, 16));
        }
    }

    private static Face[] pistonFaces(int front, int back, int start, int end) {
        // Every side quad lists its front-edge corners second and third.
        // Align the wooden rim and the extended base crop with that edge.
        Face side = tile(150, 0, start, 16, end, 3);
        return new Face[] {
            side, side, tile(front, 0, 0, 16, 16, 0), tile(back, 0, 0, 16, 16, 0), side, side
        };
    }

    public static void button(
            ShapeRenderer r, int x, int y, int z, int facing, boolean pressed, float light) {
        int depth = pressed ? 1 : 2;
        box(
                r,
                x,
                y,
                z,
                5,
                6,
                16 - depth,
                11,
                10,
                16,
                facing,
                light,
                new Face[] {
                    tile(1, 5, 0, 11, depth, 0), tile(1, 5, 0, 11, depth, 0),
                    tile(1, 5, 6, 11, 10, 0), null,
                    tile(1, 0, 6, depth, 10, 0), tile(1, 0, 6, depth, 10, 1)
                });
    }

    public static void lever(
            ShapeRenderer r, int x, int y, int z, int mount, int facing, boolean on, float light) {
        Face cap = tile(16, 5, 4, 11, 12, 0);
        Face end = tile(16, 5, 0, 11, 3, 0);
        Face edge = tile(16, 4, 0, 12, 3, 0);
        box(
                r,
                x,
                y,
                z,
                5,
                0,
                4,
                11,
                3,
                12,
                facing,
                light,
                new Face[] {cap, cap, end, edge, edge, edge},
                0,
                false,
                mount);
        Face side = tile(139, 7, 6, 9, 16, 0);
        box(
                r,
                x,
                y,
                z,
                7,
                1,
                7,
                9,
                11,
                9,
                facing,
                light,
                new Face[] {
                    null,
                    tile(139, 7, 6, 9, 8, 0),
                    side,
                    tile(139, 7, 6, 9, 16, 1),
                    side,
                    tile(139, 7, 16, 9, 6, 0)
                },
                on ? .7853982f : -.7853982f,
                false,
                mount);
    }

    public static AABB leverBounds(int x, int y, int z, int mount, int facing) {
        float[] a = leverPoint(5 / 16f, 0, 0, mount, facing);
        float[] b = leverPoint(11 / 16f, 9 / 16f, 1, mount, facing);
        return new AABB(
                x + Math.min(a[0], b[0]),
                y + Math.min(a[1], b[1]),
                z + Math.min(a[2], b[2]),
                x + Math.max(a[0], b[0]),
                y + Math.max(a[1], b[1]),
                z + Math.max(a[2], b[2]));
    }

    private static float[] leverPoint(float x, float y, float z, int mount, int facing) {
        if (mount >= 2) return rotate(x, z, 1 - y, mount);
        return mount == 1 ? rotate(x, y, z, facing) : rotate(x, 1 - y, 1 - z, facing);
    }

    public static void torch(
            ShapeRenderer r, int x, int y, int z, int facing, int texture, float light) {
        Face side = tile(texture, 7, 6, 9, 16, 0);
        Face[] faces = {
            tile(texture, 7, 13, 9, 15, 0),
            tile(texture, 7, 6, 9, 8, 0),
            side,
            tile(texture, 7, 6, 9, 16, 1),
            side,
            tile(texture, 7, 16, 9, 6, 0)
        };
        if (facing == 1) {
            box(r, x, y, z, 7, 0, 7, 9, 10, 9, 2, light, faces);
        } else {
            box(r, x, y, z, 7, 3.5f, 15, 9, 13.5f, 17, facing, light, faces, 0, true);
        }
    }

    public static void logicGate(
            ShapeRenderer renderer,
            int x,
            int y,
            int z,
            int facing,
            int topTexture,
            boolean comparator,
            boolean powered,
            boolean subtract,
            int delay,
            float light) {
        Face stone = tile(6, 0, 14, 16, 16, 0);
        box(
                renderer,
                x,
                y,
                z,
                0,
                0,
                0,
                16,
                2,
                16,
                facing,
                light,
                new Face[] {
                    tile(6, 0, 0, 16, 16, 0),
                    tile(topTexture, 0, 16, 16, 0, 1),
                    stone,
                    tile(6, 0, 14, 16, 16, 1),
                    stone,
                    tile(6, 0, 16, 16, 14, 0)
                });
        int signalTorch = powered ? 140 : 141;
        gatePost(
                renderer,
                x,
                y,
                z,
                7,
                2,
                comparator ? subtract ? 7 : 5 : 7,
                facing,
                comparator ? subtract ? 140 : 141 : signalTorch,
                light);
        if (comparator) {
            gatePost(renderer, x, y, z, 4, 11, 7, facing, signalTorch, light);
            gatePost(renderer, x, y, z, 10, 11, 7, facing, signalTorch, light);
        } else {
            gatePost(renderer, x, y, z, 7, repeaterPostZ(delay), 7, facing, signalTorch, light);
        }
    }

    static int repeaterPostZ(int delay) {
        return 4 + 2 * Math.max(1, Math.min(4, delay));
    }

    private static void gatePost(
            ShapeRenderer renderer,
            int x,
            int y,
            int z,
            int px,
            int pz,
            int top,
            int facing,
            int texture,
            float light) {
        int textureBottom = 6 + top - 2;
        if (texture == 140) light = Math.max(light, .85f);
        Face side = tile(texture, 7, 6, 9, textureBottom, 0);
        box(
                renderer,
                x,
                y,
                z,
                px,
                2,
                pz,
                px + 2,
                top,
                pz + 2,
                facing,
                light,
                new Face[] {
                    null,
                    tile(texture, 7, 6, 9, 8, 0),
                    side,
                    tile(texture, 7, 6, 9, textureBottom, 1),
                    side,
                    tile(texture, 7, textureBottom, 9, 6, 0)
                });
    }

    public static AABB torchBounds(int x, int y, int z, int facing) {
        if (facing == 1) return bounds(x, y, z, 7, 0, 7, 9, 10, 9, 2);
        return bounds(x, y, z, 7, 3, 11, 9, 14, 16, facing);
    }

    public static AABB bounds(
            int x,
            int y,
            int z,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int facing) {
        float[] a = rotate(x0 / 16, y0 / 16, z0 / 16, facing);
        float[] b = rotate(x1 / 16, y1 / 16, z1 / 16, facing);
        return new AABB(
                x + Math.min(a[0], b[0]),
                y + Math.min(a[1], b[1]),
                z + Math.min(a[2], b[2]),
                x + Math.max(a[0], b[0]),
                y + Math.max(a[1], b[1]),
                z + Math.max(a[2], b[2]));
    }

    private static float[] rotate(float x, float y, float z, int facing) {
        return switch (facing) {
            case 0 -> new float[] {x, z, 1 - y};
            case 1 -> new float[] {x, 1 - z, y};
            case 3 -> new float[] {1 - x, y, 1 - z};
            case 4 -> new float[] {z, y, 1 - x};
            case 5 -> new float[] {1 - z, y, x};
            default -> new float[] {x, y, z};
        };
    }

    public static void wire(
            ShapeRenderer r, int x, int y, int z, int connections, int power, float light) {
        float signal = power / 15f;
        r.color(
                (power == 0 ? .3f : .4f + .6f * signal) * light,
                Math.max(0, .7f * signal * signal - .5f) * light,
                Math.max(0, .6f * signal * signal - .7f) * light);
        wireQuad(r, x, y, z, 2, 0, 0, 16, 16, false, tile(201, 0, 0, 16, 16, 0));
        for (int direction = 2; direction < 6; direction++) {
            if ((connections & (1 << direction)) == 0) continue;
            wireQuad(r, x, y, z, direction, 0, 0, 16, 8, false, tile(138, 0, 0, 16, 8, 0));
            if ((connections & (1 << (direction + 4))) != 0) {
                wireQuad(r, x, y, z, direction, 0, 0, 16, 16, true, tile(138, 0, 0, 16, 16, 0));
            }
        }
    }

    private static void wireQuad(
            ShapeRenderer r,
            int x,
            int y,
            int z,
            int facing,
            int u0,
            int v0,
            int u1,
            int v1,
            boolean wall,
            Face texture) {
        float[][] corners = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
        for (int i = 0; i < corners.length; i++) {
            float a = corners[i][0] / 16, b = corners[i][1] / 16;
            float[] point = rotate(a, wall ? b : .015625f, wall ? .015625f : b, facing);
            float u = i < 2 ? texture.u1 : texture.u0;
            float v = i == 0 || i == 3 ? texture.v1 : texture.v0;
            r.vertexUV(x + point[0], y + point[1], z + point[2], u / 256, v / 256);
        }
    }

    private static void box(
            ShapeRenderer r,
            float x,
            float y,
            float z,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int facing,
            float light,
            Face[] faces) {
        box(r, x, y, z, x0, y0, z0, x1, y1, z1, facing, light, faces, 0);
    }

    private static void box(
            ShapeRenderer r,
            float x,
            float y,
            float z,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int facing,
            float light,
            Face[] faces,
            float hingeAngle) {
        box(r, x, y, z, x0, y0, z0, x1, y1, z1, facing, light, faces, hingeAngle, false);
    }

    private static void box(
            ShapeRenderer r,
            float x,
            float y,
            float z,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int facing,
            float light,
            Face[] faces,
            float hingeAngle,
            boolean wallTorch) {
        box(r, x, y, z, x0, y0, z0, x1, y1, z1, facing, light, faces, hingeAngle, wallTorch, -1);
    }

    private static void box(
            ShapeRenderer r,
            float x,
            float y,
            float z,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int facing,
            float light,
            Face[] faces,
            float hingeAngle,
            boolean wallTorch,
            int leverMount) {
        float[][] vertices = {
            {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1},
            {x1, y1, z1, x1, y1, z0, x0, y1, z0, x0, y1, z1},
            {x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0},
            {x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1},
            {x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1},
            {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}
        };
        for (int face = 0; face < 6; face++) {
            Face uv = faces[face];
            if (uv == null) continue;
            float shade = face == 1 ? 1 : face == 0 ? .5f : face < 4 ? .8f : .6f;
            r.color(light * shade, light * shade, light * shade);
            for (int i = 0; i < 4; i++) {
                float px = vertices[face][i * 3] / 16f;
                float py = vertices[face][i * 3 + 1] / 16f;
                float pz = vertices[face][i * 3 + 2] / 16f;
                if (hingeAngle != 0) {
                    float pivotY = leverMount < 0 ? 9f / 16 : 1f / 16;
                    float pivotZ = leverMount < 0 ? 15f / 16 : .5f;
                    float dy = py - pivotY, dz = pz - pivotZ;
                    float cosine = (float) Math.cos(hingeAngle),
                            sine = (float) Math.sin(hingeAngle);
                    py = pivotY + dy * cosine - dz * sine;
                    pz = pivotZ + dy * sine + dz * cosine;
                }
                if (wallTorch) {
                    float dy = py - 3.5f / 16, dz = pz - 1;
                    py = 3.5f / 16 + dy * .92388f + dz * .38268f;
                    pz = 1 - dy * .38268f + dz * .92388f;
                }
                float[] point =
                        leverMount < 0
                                ? rotate(px, py, pz, facing)
                                : leverPoint(px, py, pz, leverMount, facing);
                int corner = (i + uv.rotation) % 4;
                float u = (corner == 0 || corner == 3 ? uv.u0 : uv.u1);
                float v = (corner < 2 ? uv.v0 : uv.v1);
                r.vertexUV(x + point[0], y + point[1], z + point[2], u / 256f, v / 256f);
            }
        }
    }
}
