package net.classicremastered.minecraft.level.structure;

import java.util.Random;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.tile.Block;

/** Compact timber tunnels attached to an existing dry cave. */
public final class Mineshaft {
    private Mineshaft() {}

    public static void decorate(SimpleChunk chunk, long seed) {
        Random random = siteRandom(seed, chunk.cx, chunk.cz);
        if (random.nextInt(10) != 0) return;
        int top = Math.min(28, chunk.height - 8);
        for (int y = top; y >= 9; y--) {
            if (tryBuild(chunk, y, 3, 1) || tryBuild(chunk, y, 12, -1)) return;
        }
    }

    private static boolean tryBuild(SimpleChunk chunk, int y, int mouth, int direction) {
        if (get(chunk, 7, y, mouth) != 0
                || get(chunk, 7, y + 1, mouth) != 0
                || !rock(get(chunk, 7, y - 1, mouth))
                || !rock(get(chunk, 7, y + 3, mouth))
                || !rock(get(chunk, 7, y, mouth + direction * 4))) return false;
        int junction = mouth + direction * 6;
        for (int z = Math.min(mouth, junction - 1); z <= Math.max(mouth, junction + 1); z++) {
            for (int x = 2; x <= 13; x++) {
                for (int dy = -1; dy <= 3; dy++) {
                    int id = get(chunk, x, y + dy, z);
                    if (id != 0 && !rock(id)) return false;
                }
            }
        }

        for (int step = 0; step <= 6; step++) {
            int z = mouth + direction * step;
            for (int x = 6; x <= 8; x++) passage(chunk, x, y, z);
            if (step == 2 || step == 4) {
                for (int x = 6; x <= 8; x++) set(chunk, x, y + 2, z, Block.WOOD.id);
                posts(chunk, 6, y, z);
                posts(chunk, 8, y, z);
            }
        }
        for (int x = 2; x <= 13; x++) {
            for (int z = junction - 1; z <= junction + 1; z++) passage(chunk, x, y, z);
            if (x == 4 || x == 11) {
                for (int z = junction - 1; z <= junction + 1; z++)
                    set(chunk, x, y + 2, z, Block.WOOD.id);
                posts(chunk, x, y, junction - 1);
                posts(chunk, x, y, junction + 1);
            }
        }
        return true;
    }

    public static void decorateFinite(byte[] blocks, int width, int length, int height, long seed) {
        for (int cz = 0; cz < length / 16; cz++) {
            for (int cx = 0; cx < width / 16; cx++) {
                if (siteRandom(seed, cx, cz).nextInt(10) != 0) continue;
                SimpleChunk chunk = new SimpleChunk(cx, cz, height);
                copyRows(blocks, width, length, chunk, false);
                decorate(chunk, seed);
                copyRows(blocks, width, length, chunk, true);
            }
        }
    }

    private static void copyRows(
            byte[] blocks, int width, int length, SimpleChunk chunk, boolean write) {
        for (int y = 0; y < chunk.height; y++) {
            for (int z = 0; z < 16; z++) {
                int world = (y * length + chunk.cz * 16 + z) * width + chunk.cx * 16;
                int local = SimpleChunk.idx(0, y, z, chunk.height);
                if (write) System.arraycopy(chunk.blocks, local, blocks, world, 16);
                else System.arraycopy(blocks, world, chunk.blocks, local, 16);
            }
        }
    }

    private static Random siteRandom(long seed, int cx, int cz) {
        return new Random(seed ^ cx * 341873128712L ^ cz * 132897987541L ^ 0x4d494e45L);
    }

    private static boolean rock(int id) {
        return id == Block.STONE.id
                || id == Block.COAL_ORE.id
                || id == Block.IRON_ORE.id
                || id == Block.GOLD_ORE.id
                || id == Block.REDSTONE_ORE.id
                || id == Block.DIAMOND_ORE_NEW.id;
    }

    private static void passage(SimpleChunk chunk, int x, int y, int z) {
        if (get(chunk, x, y - 1, z) == 0) set(chunk, x, y - 1, z, Block.WOOD.id);
        for (int dy = 0; dy < 3; dy++) set(chunk, x, y + dy, z, 0);
    }

    private static void posts(SimpleChunk chunk, int x, int y, int z) {
        set(chunk, x, y, z, Block.LOG.id);
        set(chunk, x, y + 1, z, Block.LOG.id);
    }

    private static int get(SimpleChunk chunk, int x, int y, int z) {
        return chunk.blocks[SimpleChunk.idx(x, y, z, chunk.height)] & 255;
    }

    private static void set(SimpleChunk chunk, int x, int y, int z, int id) {
        chunk.blocks[SimpleChunk.idx(x, y, z, chunk.height)] = (byte) id;
    }
}
