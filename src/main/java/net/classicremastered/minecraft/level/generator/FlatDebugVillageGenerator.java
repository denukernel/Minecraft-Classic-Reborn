// src/com/mojang/minecraft/level/generator/FlatDebugVillageGenerator.java
package net.classicremastered.minecraft.level.generator;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.structure.HouseStructure;
import net.classicremastered.minecraft.level.tile.Block;

public final class FlatDebugVillageGenerator {

    private FlatDebugVillageGenerator() {}

    /**
     * Makes a flat test world with exactly one village (one house + villagers).
     */
    public static Level makeDebugVillageLevel() {
        final int h = 64;
        final int w = 128;
        final int d = 128;
        final int groundY = 8;

        byte[] blocks = new byte[w * h * d];
        final int AIR   = 0;
        final int STONE = Block.STONE.id;
        final int DIRT  = Block.DIRT.id;
        final int GRASS = Block.GRASS.id;

        // === flat terrain ===
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

        Level lvl = new Level();
        lvl.setData(w, h, d, blocks);
        lvl.xSpawn = w / 2;
        lvl.zSpawn = d / 2;
        lvl.ySpawn = groundY + 2;

        // === place one house at center ===
        int hx = w / 2 - 3;
        int hz = d / 2 - 3;
        int hy = groundY + 1;
        HouseStructure house = new HouseStructure();
        boolean ok = house.generate(lvl, hx, hy, hz, new Random());
        System.out.println("[FlatDebugVillageGenerator] House gen at " + hx + "," + hy + "," + hz + " -> " + ok);

        return lvl;
    }
}
