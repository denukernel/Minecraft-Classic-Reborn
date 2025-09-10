package net.classicremastered.minecraft.level.tile;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.classicremastered.minecraft.level.Level;

public final class LeavesBlock extends LeavesBaseBlock {

    private static final int LOG_SCAN_RADIUS = 4;

    // We can't store per-block metadata in 0.30, so keep a tiny countdown map per Level.
    // Key format "x,y,z" → remaining passes until decay.
    // NOTE: Level processes scheduled ticks every 5 game ticks, so:
    // 5 seconds @ 20 TPS = 100 game ticks = 20 scheduled "passes".
    private static final Map<Level, HashMap<String, Integer>> DECAY = new HashMap<>();
    private static HashMap<String, Integer> map(Level lvl) {
        return DECAY.computeIfAbsent(lvl, k -> new HashMap<>());
    }
    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    protected LeavesBlock(int var1, int var2) {
        super(18, 22, true);
        // Ensure we also get occasional random updates for stragglers
        Block.physics[this.id] = true;
    }

    // One scheduled "pass" happens every 5 game ticks in Level.tick().
    @Override
    public int getTickDelay() { return 1; } // re-check every pass (~0.25s)

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        level.addToTickNextTick(x, y, z, this.id);
    }

    @Override
    public void update(Level level, int x, int y, int z, Random rand) {
        if (level.getTile(x, y, z) != this.id) {
            // Block changed since scheduling; clear any countdown.
            map(level).remove(key(x, y, z));
            return;
        }

        // --- Creative safeguard: leaves placed on ground in creative never decay. ---
        boolean airBelow = (y > 0) && level.getTile(x, y - 1, z) == 0;
        if (level.creativeMode && !airBelow) {
            map(level).remove(key(x, y, z)); // ensure no countdown lingers
            return;
        }

        // If still connected to any LOG via air/leaves within radius, cancel decay.
        if (isConnectedToLog(level, x, y, z)) {
            map(level).remove(key(x, y, z)); // supported → no decay timer
            // Light reschedule so we re-evaluate after future edits
            if ((rand.nextInt(8) == 0)) level.addToTickNextTick(x, y, z, this.id);
            return;
        }

        // Only decay leaves that are "floating" (air below). This keeps ground-placed
        // decorative leaves stable (esp. in creative), but allows sky/air leaves to decay.
        if (!airBelow) {
            // Not floating → don't decay, but keep occasional rechecks.
            if ((rand.nextInt(10) == 0)) level.addToTickNextTick(x, y, z, this.id);
            map(level).remove(key(x, y, z));
            return;
        }

        // --- Deterministic ~5s countdown (20 passes * 5 ticks = ~100 ticks) ---
        final String k = key(x, y, z);
        HashMap<String, Integer> m = map(level);
        Integer left = m.get(k);
        if (left == null) left = 20;         // start ~5s timer
        else left = Math.max(0, left - 1);   // decrement one scheduled pass

        if (left == 0) {
            // Final check before decay: if someone placed a log meanwhile, abort.
            if (!isConnectedToLog(level, x, y, z)) {
                level.setTile(x, y, z, 0);   // decay now
            }
            m.remove(k);
        } else {
            m.put(k, left);
            level.addToTickNextTick(x, y, z, this.id); // continue the countdown
        }
    }

    @Override
    public int getDropCount() {
        return random.nextInt(10) == 0 ? 1 : 0;
    }

    @Override
    public int getDrop() {
        return Block.SAPLING.id;
    }

    // ---------- connectivity: BFS through air/leaves to any LOG within radius ----------
    private boolean isConnectedToLog(Level level, int x, int y, int z) {
        final int r = LOG_SCAN_RADIUS;
        final int size = (r * 2 + 1);
        boolean[][][] seen = new boolean[size][size][size];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int cx = r, cy = r, cz = r;
        q.add(new int[]{cx, cy, cz});
        seen[cx][cy][cz] = true;

        while (!q.isEmpty()) {
            int[] p = q.removeFirst();
            int dx = p[0] - cx, dy = p[1] - cy, dz = p[2] - cz;
            int wx = x + dx, wy = y + dy, wz = z + dz;
            if (!level.isInBounds(wx, wy, wz)) continue;

            int id = level.getTile(wx, wy, wz);
            if (id == Block.LOG.id) return true;

            // Traverse only through air or leaves; stop at anything else
            if (!(id == 0 || id == this.id)) continue;

            if (Math.abs(dx) >= r || Math.abs(dy) >= r || Math.abs(dz) >= r) continue;

            tryQueue(seen, q, p[0] + 1, p[1], p[2]);
            tryQueue(seen, q, p[0] - 1, p[1], p[2]);
            tryQueue(seen, q, p[0], p[1] + 1, p[2]);
            tryQueue(seen, q, p[0], p[1] - 1, p[2]);
            tryQueue(seen, q, p[0], p[1], p[2] + 1);
            tryQueue(seen, q, p[0], p[1], p[2] - 1);
        }
        return false;
    }

    private static void tryQueue(boolean[][][] seen, ArrayDeque<int[]> q, int i, int j, int k) {
        if (i < 0 || j < 0 || k < 0 || i >= seen.length || j >= seen[0].length || k >= seen[0][0].length) return;
        if (!seen[i][j][k]) { seen[i][j][k] = true; q.add(new int[]{i, j, k}); }
    }
}
