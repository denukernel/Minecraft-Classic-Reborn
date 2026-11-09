package net.classicremastered.minecraft.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/** Twelve-block cloud cells with four-block depth, matching the modern cloud mesh layout. */
public final class CloudRenderer {
    private static final int CELL_SIZE = 12;
    private static final float THICKNESS = 4;
    private static final float[] SHADE = {.7f, 1f, .8f, .8f, .9f, .9f};
    private static final int[][] CORNERS = {
        {1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 0},
        {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
        {0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0},
        {1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1},
        {0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0},
        {1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1}
    };
    private int textureId = -1, width, height;
    private boolean[] occupied;
    private int previousX = Integer.MIN_VALUE, previousZ, previousRadius;
    private final FloatBuffer[] faces = new FloatBuffer[6];

    public void render(
            TextureManager textures,
            double cameraX,
            double cameraZ,
            float bottom,
            float ticks,
            int radius,
            float red,
            float green,
            float blue) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glPushMatrix();
        try {
            loadMask(textures.load("/clouds.png"));
            double cloudX = cameraX + (ticks % (width * 400L)) * .03;
            double cloudZ = cameraZ + 3.96;
            long worldCellX = (long) Math.floor(cloudX / CELL_SIZE);
            long worldCellZ = (long) Math.floor(cloudZ / CELL_SIZE);
            int cellX = (int) Math.floorMod(worldCellX, width);
            int cellZ = (int) Math.floorMod(worldCellZ, height);
            radius = Math.max(4, Math.min(32, radius));
            if (cellX != previousX || cellZ != previousZ || radius != previousRadius) {
                rebuild(cellX, cellZ, radius);
            }
            double offsetX = cloudX - worldCellX * CELL_SIZE;
            double offsetZ = cloudZ - worldCellZ * CELL_SIZE;
            GL11.glTranslated(cameraX - offsetX, bottom, cameraZ - offsetZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            // Resolve the nearest cloud surface before blending, including from inside the layer.
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_BLEND);
            draw(red, green, blue);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            draw(red, green, blue);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
        }
    }

    private void loadMask(int texture) {
        if (texture == textureId) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        ByteBuffer pixels =
                BufferUtils.createByteBuffer(
                        Math.multiplyExact(Math.multiplyExact(width, height), 4));
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        occupied = new boolean[width * height];
        for (int i = 0; i < occupied.length; i++) occupied[i] = (pixels.get(i * 4 + 3) & 255) >= 10;
        textureId = texture;
        previousX = Integer.MIN_VALUE;
    }

    private boolean occupied(int x, int z) {
        return occupied[Math.floorMod(z, height) * width + Math.floorMod(x, width)];
    }

    private void rebuild(int centerX, int centerZ, int radius) {
        int cells = radius * 2 + 1;
        for (int face = 0; face < 6; face++) {
            int capacity = cells * cells * 12;
            FloatBuffer vertices = faces[face];
            if (vertices == null || vertices.capacity() != capacity) {
                vertices = BufferUtils.createFloatBuffer(capacity);
            }
            vertices.clear();
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    int tx = centerX + x, tz = centerZ + z;
                    if (!occupied(tx, tz)) continue;
                    if (face == 2 && occupied(tx, tz - 1)
                            || face == 3 && occupied(tx, tz + 1)
                            || face == 4 && occupied(tx - 1, tz)
                            || face == 5 && occupied(tx + 1, tz)) continue;
                    int[] corners = CORNERS[face];
                    for (int i = 0; i < 12; i += 3) {
                        vertices.put((x + corners[i]) * CELL_SIZE);
                        vertices.put(corners[i + 1] * THICKNESS);
                        vertices.put((z + corners[i + 2]) * CELL_SIZE);
                    }
                }
            }
            vertices.flip();
            faces[face] = vertices;
        }
        previousX = centerX;
        previousZ = centerZ;
        previousRadius = radius;
    }

    private void draw(float red, float green, float blue) {
        for (int face = 0; face < faces.length; face++) {
            FloatBuffer vertices = faces[face];
            GL11.glColor4f(red * SHADE[face], green * SHADE[face], blue * SHADE[face], .85f);
            GL11.glVertexPointer(3, 0, vertices);
            GL11.glDrawArrays(GL11.GL_QUADS, 0, vertices.limit() / 3);
        }
    }
}
