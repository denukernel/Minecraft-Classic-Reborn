package net.classicremastered.minecraft.level.structure;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;

/** A simple structure generator contract. */
public interface Structure {
    /** Unique id for debugging/metrics. */
    String id();

    /** Try to place the structure at x/z (y is ground from getHighestTile). */
    boolean generate(Level level, int x, int y, int z, Random rand);

    /** Quick check so the registry can skip obviously bad spots. */
 // Default canPlace: fix Y bound to use level.height (vertical), not depth (Z)
    default boolean canPlace(Level level, int x, int y, int z) {
        if (y <= 0 || y + 4 >= level.height) return false;
        int ground = level.getTile(x, y - 1, z);
        int above  = level.getTile(x, y, z);
        return ground != 0 && above == 0;
    }
}
