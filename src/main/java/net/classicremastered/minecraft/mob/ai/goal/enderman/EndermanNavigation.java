package net.classicremastered.minecraft.mob.ai.goal.enderman;

import java.util.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import static net.classicremastered.minecraft.mob.ai.goal.enderman.EndermanTeleportation.*;

public final class EndermanNavigation {

    private static final int PATH_RADIUS = 24;
    private static final int PATH_MAX_NODES = 800;
    private static final int MAX_STEP_UP = 1;
    private Enderman mob;
    private List<int[]> currentPath;
    private int pathIndex;
    public void clear() {
        currentPath = null;
        pathIndex = 0;
    }

    void steer(EndermanAI ai, Enderman enderman, Player player, float dx, float dz) {
        mob = enderman;
        // --- Pathfinder: try to walk around water if direct forward would step into
        // water ---
        // Compute normalized XZ direction toward player:
        float dirLen = (float) Math.sqrt(dx * dx + dz * dz);
        float forwardX = dirLen > 1e-5f ? dx / dirLen : 0f;
        float forwardZ = dirLen > 1e-5f ? dz / dirLen : 0f;

        if (wouldWalkIntoWater(enderman, forwardX, forwardZ)) {
            // Attempt to find a path around water (A*) to player's column (tx,tz)
            int startTx = (int) Math.floor(enderman.x);
            int startTz = (int) Math.floor(enderman.z);
            int rawGoalTx = (int) Math.floor(player.x);
            int rawGoalTz = (int) Math.floor(player.z);

            // Choose a nearby dry tile near the player as the path goal to avoid routing
            // into lakes.
            int[] dry = findNearestDryTile(ai.level, rawGoalTx, rawGoalTz, PATH_RADIUS);
            int goalTx = (dry != null) ? dry[0] : rawGoalTx;
            int goalTz = (dry != null) ? dry[2] : rawGoalTz;

            // If we don't have a cached path, find one
            if (currentPath == null || pathIndex >= (currentPath != null ? currentPath.size() : 0)) {
                currentPath = findPathAvoidingWater(enderman, startTx, startTz, goalTx, goalTz, PATH_RADIUS,
                        PATH_MAX_NODES);
                pathIndex = 0;
            }

            if (currentPath != null && pathIndex < currentPath.size()) {
                int[] next = currentPath.get(pathIndex);
                float tx = next[0] + 0.5f;
                float tz = next[1] + 0.5f;
                // steer towards next node
                float sx = tx - enderman.x;
                float sz = tz - enderman.z;
                float slen = (float) Math.sqrt(sx * sx + sz * sz);
                if (slen < 0.4f) {
                    // reached this node -> advance
                    pathIndex++;
                } else {
                    float nx = sx / slen;
                    float nz = sz / slen;
                    // set movement toward node
                    ai.running = true;
                    ai.yya = ai.runSpeed * 1.2f;
                    // face direction immediately (helps movement)
                    float yaw = (float) (Math.atan2(nz, nx) * 180.0D / Math.PI) - 90.0F;
                    enderman.yRot += (yaw - enderman.yRot) * 0.5f;
                }
                return;
            }
        }

    }
    private static final class Node {

        int x, z;
        int gx;
        float f;
        Node parent;

        Node(int x, int z) {
            this.x = x;
            this.z = z;
            this.gx = Integer.MAX_VALUE;
            this.f = Float.MAX_VALUE;
            this.parent = null;
        }

        public String key() {
            return x + ":" + z;
        }
    }

    private List<int[]> findPathAvoidingWater(Enderman mob, int sx, int sz, int gx, int gz, int radius, int maxNodes) {
        if (mob == null || mob.level == null)
            return null;
        Level lvl = mob.level;

        // quick bounds check
        if (!lvl.isInBounds(gx, 0, gz))
            return null;

        // clamp search box
        int minX = Math.max(0, Math.min(sx, gx) - radius);
        int maxX = Math.min(lvl.width - 1, Math.max(sx, gx) + radius);
        int minZ = Math.max(0, Math.min(sz, gz) - radius);
        int maxZ = Math.min(lvl.height - 1, Math.max(sz, gz) + radius);

        Comparator<Node> cmp = Comparator.comparingDouble(n -> n.f);
        PriorityQueue<Node> open = new PriorityQueue<>(cmp);
        Map<String, Node> all = new HashMap<>();
        Set<String> closed = new HashSet<>();

        Node start = new Node(sx, sz);
        start.gx = 0;
        start.f = heuristic(sx, sz, gx, gz);
        open.add(start);
        all.put(start.key(), start);

        int nodes = 0;
        int[][] neigh = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1},
                {-1, -1}}; // 8-neigh

        while (!open.isEmpty() && nodes < maxNodes) {
            Node cur = open.poll();
            if (cur == null)
                break;
            nodes++;
            if (cur.x == gx && cur.z == gz) {
                // reconstruct path
                List<int[]> path = new ArrayList<>();
                Node p = cur;
                while (p != null && !(p.x == sx && p.z == sz)) {
                    path.add(new int[]{p.x, p.z});
                    p = p.parent;
                }
                Collections.reverse(path);
                return path;
            }

            closed.add(cur.key());

            for (int[] d : neigh) {
                int nx = cur.x + d[0];
                int nz = cur.z + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ)
                    continue;
                if (!lvl.isInBounds(nx, 0, nz))
                    continue;

                // candidate must be walkable: highest tile >0, solid below, not liquid at top
                int ny = lvl.getHighestTile(nx, nz);
                if (ny <= 0)
                    continue;
                if (!lvl.isSolidTile(nx, ny - 1, nz))
                    continue;
                // ensure step up/down not too large
                int curY = lvl.getHighestTile(cur.x, cur.z);
                if (Math.abs(ny - curY) > MAX_STEP_UP)
                    continue;

                float cost = (d[0] != 0 && d[1] != 0) ? 1.4142f : 1.0f; // diagonal cost
                String key = nx + ":" + nz;
                if (closed.contains(key))
                    continue;

                // reject if candidate AABB intersects liquid or blocks/entities
                float cx = nx + 0.5f;
                float cz = nz + 0.5f;
                AABB candidate = buildCandidateBB(mob, cx, ny, cz);
                if (candidateIntersectsLiquid(lvl, candidate))
                    continue;
                if (!lvl.isFree(candidate))
                    continue;
                if (lvl.getCubes(candidate).size() > 0)
                    continue;

                int ng = cur.gx + Math.round(cost * 10); // multiply to keep ints
                Node node = all.get(key);
                if (node == null) {
                    node = new Node(nx, nz);
                    all.put(node.key(), node);
                }
                if (ng < node.gx) {
                    node.gx = ng;
                    node.parent = cur;
                    node.f = ng + Math.round(heuristic(nx, nz, gx, gz) * 10);
                    open.remove(node); // remove if present to update priority
                    open.add(node);
                }
            }
        }

        return null;
    }

    private float heuristic(int x, int z, int gx, int gz) {
        float dx = gx - x;
        float dz = gz - z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private int[] findNearestDryTile(Level lvl, int gx, int gz, int radius) {
        if (lvl == null || !lvl.isInBounds(gx, 0, gz))
            return null;

        // spiral/expanding square search — small and deterministic
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int[] candX = new int[]{gx + dx, gx + dx};
                int[] candZ = new int[]{gz - r, gz + r};
                for (int k = 0; k < 2; k++) {
                    int tx = candX[k];
                    int tz = candZ[k];
                    if (!lvl.isInBounds(tx, 0, tz))
                        continue;
                    int ty = lvl.getHighestTile(tx, tz);
                    if (ty <= 0)
                        continue;
                    // ensure solid ground beneath and not water at top/below
                    if (!lvl.isSolidTile(tx, ty - 1, tz))
                        continue;
                    AABB candidate = buildCandidateBB((Enderman) mob, tx + 0.5f, ty, tz + 0.5f);
                    if (candidateIntersectsLiquid(lvl, candidate))
                        continue;
                    if (!lvl.isFree(candidate))
                        continue;
                    if (lvl.getCubes(candidate).size() > 0)
                        continue;
                    return new int[]{tx, ty, tz};
                }
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                int[] candZ = new int[]{gz + dz, gz + dz};
                int[] candX = new int[]{gx - r, gx + r};
                for (int k = 0; k < 2; k++) {
                    int tx = candX[k];
                    int tz = candZ[k];
                    if (!lvl.isInBounds(tx, 0, tz))
                        continue;
                    int ty = lvl.getHighestTile(tx, tz);
                    if (ty <= 0)
                        continue;
                    if (!lvl.isSolidTile(tx, ty - 1, tz))
                        continue;
                    AABB candidate = buildCandidateBB((Enderman) mob, tx + 0.5f, ty, tz + 0.5f);
                    if (candidateIntersectsLiquid(lvl, candidate))
                        continue;
                    if (!lvl.isFree(candidate))
                        continue;
                    if (lvl.getCubes(candidate).size() > 0)
                        continue;
                    return new int[]{tx, ty, tz};
                }
            }
        }
        return null;
    }

    private boolean wouldWalkIntoWater(Enderman mob, float forwardX, float forwardZ) {
        if (mob == null || mob.level == null)
            return true;
        Level lvl = mob.level;

        final float step = 0.6f; // small step to probe immediate cell
        final int lookSteps = 3; // how many steps to scan ahead (tweakable)

        for (int s = 1; s <= lookSteps; s++) {
            float nx = mob.x + forwardX * (step * s);
            float nz = mob.z + forwardZ * (step * s);
            int bx = (int) Math.floor(nx);
            int bz = (int) Math.floor(nz);
            if (!lvl.isInBounds(bx, 0, bz))
                return true;

            int top = lvl.getHighestTile(bx, bz);
            if (top <= 0)
                return true;

            // if the column immediately has water at the top or below, consider unsafe
            if (lvl.isWater(bx, top - 1, bz) || lvl.isWater(bx, top, bz))
                return true;

            // candidate AABB for the mob feet at that column
            AABB candidate = buildCandidateBB(mob, bx + 0.5f, top, bz + 0.5f);
            if (candidateIntersectsLiquid(lvl, candidate))
                return true;
            if (!lvl.isFree(candidate))
                return true;
        }

        return false;
    }
}
