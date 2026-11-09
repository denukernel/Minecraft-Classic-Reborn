package net.classicremastered.minecraft.level;

import java.util.*;
import net.classicremastered.minecraft.level.tile.Block;

/** Bounded flood fills with a full propagation halo, shared by mesh and entity queries. */
final class SkyLightCache {
    private static final int HALO = 8, SIDE = 32, PLANE = SIDE * SIDE, COUNT = SIDE * SIDE * SIDE;
    private record Key(int x, int y, int z) {
    }
    private final Level level;
    private final Map<Key, byte[]> tiles = new LinkedHashMap<>(128, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, byte[]> entry) {
            return size() > 128;
        }
    };
    SkyLightCache(Level level) {
        this.level = level;
    }

    void invalidate(int x, int z) {
        tiles.keySet().removeIf(k -> x >= (k.x << 4) - HALO && x < (k.x << 4) + 16 + HALO && z >= (k.z << 4) - HALO
                && z < (k.z << 4) + 16 + HALO);
    }

    float exposure(int x, int y, int z) {
        Key key = new Key(x >> 4, y >> 4, z >> 4);
        byte[] tile = tiles.get(key);
        if (tile == null) {
            tile = build(key);
            tiles.put(key, tile);
        }
        return tile[((y & 15) * 16 + (z & 15)) * 16 + (x & 15)] / 9f;
    }

    private byte[] build(Key key) {
        int ox = (key.x << 4) - HALO, oy = (key.y << 4) - HALO, oz = (key.z << 4) - HALO;
        byte[] light = new byte[COUNT];
        boolean[] opaque = new boolean[COUNT];
        int[] queue = new int[COUNT];
        int tail = 0;
        for (int z = 0; z < SIDE; z++)
            for (int x = 0; x < SIDE; x++) {
                // Unlike a bounded upward ray, this detects roofs at any world height.
                boolean sky = true;
                for (int y = level.depth - 1; y >= Math.max(0, oy); y--) {
                    int id = level.getTile(ox + x, y, oz + z);
                    boolean blocked = id > 0 && Block.blocks[id] != null && Block.blocks[id].isOpaque();
                    if (blocked)
                        sky = false;
                    int dy = y - oy;
                    if (dy >= SIDE)
                        continue;
                    int i = (dy * SIDE + z) * SIDE + x;
                    opaque[i] = blocked;
                    if (sky) {
                        light[i] = 9;
                        queue[tail++] = i;
                    }
                }
                for (int dy = Math.max(0, level.depth - oy); dy < SIDE; dy++) {
                    int i = (dy * SIDE + z) * SIDE + x;
                    light[i] = 9;
                    queue[tail++] = i;
                }
                for (int dy = 0; dy < Math.min(SIDE, -oy); dy++)
                    opaque[(dy * SIDE + z) * SIDE + x] = true;
            }
        // Multi-source breadth-first propagation visits each transparent cell once.
        for (int head = 0; head < tail; head++) {
            int i = queue[head], value = light[i] - 1;
            if (value <= 0)
                continue;
            int x = i % SIDE, z = i / SIDE % SIDE, y = i / PLANE;
            if (x > 0)
                tail = spread(i - 1, value, light, opaque, queue, tail);
            if (x < SIDE - 1)
                tail = spread(i + 1, value, light, opaque, queue, tail);
            if (z > 0)
                tail = spread(i - SIDE, value, light, opaque, queue, tail);
            if (z < SIDE - 1)
                tail = spread(i + SIDE, value, light, opaque, queue, tail);
            if (y > 0)
                tail = spread(i - PLANE, value, light, opaque, queue, tail);
            if (y < SIDE - 1)
                tail = spread(i + PLANE, value, light, opaque, queue, tail);
        }
        byte[] result = new byte[4096];
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    result[(y * 16 + z) * 16 + x] = light[((y + HALO) * SIDE + z + HALO) * SIDE + x + HALO];
        return result;
    }

    private static int spread(int i, int value, byte[] light, boolean[] opaque, int[] queue, int tail) {
        if (!opaque[i] && light[i] == 0) {
            light[i] = (byte) value;
            queue[tail++] = i;
        }
        return tail;
    }
}
