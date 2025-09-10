// src/com/mojang/minecraft/level/generator/FlatLevelGenerator.java
package net.classicremastered.minecraft.level.generator;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;

public final class FlatLevelGenerator {
    private FlatLevelGenerator() {}

    /** 0=Small, 1=Normal, 2=Huge */
    public static Level makeFlatLevel(int sizeId) {
        final int h = 64;
        final int w = (sizeId == 0) ? 64 : (sizeId == 1) ? 128 : 256;
        final int d = w;
        final int groundY = 8;

        final int AIR   = 0;
        final int STONE = Block.STONE.id;
        final int DIRT  = Block.DIRT.id;
        final int GRASS = Block.GRASS.id;

        byte[] blocks = new byte[w * h * d];

        // fill deterministically
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                for (int y = 0; y < h; y++) {
                    int id = AIR;
                    if (y < groundY - 2)        id = STONE;
                    else if (y == groundY - 2)  id = DIRT;
                    else if (y == groundY - 1)  id = DIRT;
                    else if (y == groundY)      id = GRASS;
                    blocks[((y * d) + z) * w + x] = (byte) id;
                }
            }
        }

        Level lvl = new Level();            // no ctor args needed
        lvl.setData(w, h, d, blocks);       // resets blockMap/chunks/water/etc.
        // set spawn ~center
        lvl.xSpawn = w / 2;
        lvl.zSpawn = d / 2;
        lvl.ySpawn = Math.min(h - 2, groundY + 2);

        return lvl;
    }
}
