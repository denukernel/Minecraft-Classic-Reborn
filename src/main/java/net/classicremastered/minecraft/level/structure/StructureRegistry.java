package net.classicremastered.minecraft.level.structure;

import java.util.*;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.util.Debug;

public final class StructureRegistry {
    public static final StructureRegistry INSTANCE = new StructureRegistry();

    public static final class Entry {
        public final Structure structure;
        public final int weight;
        Entry(Structure s, int w) { this.structure = s; this.weight = Math.max(1, w); }
    }
    public List<Entry> getEntries() { return java.util.Collections.unmodifiableList(entries); }

    private final List<Entry> entries = new ArrayList<>();
    private boolean bootstrapped = false;

    private StructureRegistry() {}

    public void register(Structure structure, int weight) {
        entries.add(new Entry(structure, weight));
    }

    public void bootstrapDefaults() {
        if (bootstrapped) return;
        bootstrapped = true;

        try {
            register(new HouseStructure(), 20);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


 // StructureRegistry.java — replace placeAll(...) with this tuned version:

    public void placeAll(Level level, Random rand) {
        if (entries.isEmpty()) {
            //Debug.struct(level, "placeAll: no entries registered.");
            return;
        }

        final int cell = 32;      // denser grid
        final int border = 8;
        int attempts = 0, successes = 0;

        //Debug.struct(level, "placeAll: BEGIN grid cell=" + cell + " border=" + border +
                //" size=(" + level.width + "," + level.height + "," + level.depth + ")");

        for (int gx = border; gx < level.width - border; gx += cell) {
            for (int gz = border; gz < level.depth - border; gz += cell) {
                int maxXSpan = Math.max(1, Math.min(cell - 16, level.width - gx - 16));
                int maxZSpan = Math.max(1, Math.min(cell - 16, level.depth - gz - 16));

                // Try up to 3 candidates per cell (center-ish + 2 jitters)
                for (int j = 0; j < 3; j++) {
                    int x = gx + 8 + rand.nextInt(maxXSpan);
                    int z = gz + 8 + rand.nextInt(maxZSpan);
                    int y = level.getHighestTile(x, z);

                    Structure s = pickWeighted(rand);
                    String sid = (s == null ? "null" : s.id());
                    attempts++;
                    //Debug.struct(level, "try " + attempts + ": pick=" + sid + " at (" + x + "," + y + "," + z + ")");

                    if (s != null && s.canPlace(level, x, y, z) && s.generate(level, x, y, z, rand)) {
                        successes++;
                        //Debug.struct(level, " -> placed OK (" + sid + "), total=" + successes);
                        break; // done with this cell
                    }
                }
            }
        }

        // Fallback near spawn/center if nothing succeeded
        if (successes == 0) {
            //Debug.struct(level, "placeAll: FALLBACK near spawn");
            int tries = 48; // more stubborn
            int cx = level.xSpawn > 0 ? level.xSpawn : level.width / 2;
            int cz = level.zSpawn > 0 ? level.zSpawn : level.depth / 2;

            for (int i = 0; i < tries; i++) {
                int x = Math.max(1, Math.min(level.width - 8,  cx + rand.nextInt(129) - 64));
                int z = Math.max(1, Math.min(level.depth - 8,  cz + rand.nextInt(129) - 64));
                int y = level.getHighestTile(x, z);

                Structure s = pickWeighted(rand);
                String sid = (s == null ? "null" : s.id());
                //Debug.struct(level, "fallback " + (i + 1) + "/" + tries + ": pick=" + sid + " @(" + x + "," + y + "," + z + ")");

                if (s != null && s.canPlace(level, x, y, z) && s.generate(level, x, y, z, rand)) {
                    successes++;
                    //Debug.struct(level, " -> fallback placed OK (" + sid + ")");
                    if (successes >= 2) break;
                }
            }
        }

        //Debug.struct(level, "placeAll: END attempts=" + attempts + " placed=" + successes);
    }


    private Structure pickWeighted(Random rand) {
        int total = 0;
        for (Entry e : entries) total += e.weight;
        if (total <= 0) return null;
        int r = rand.nextInt(total);
        for (Entry e : entries) {
            if ((r -= e.weight) < 0) return e.structure;
        }
        return null;
    }
}
